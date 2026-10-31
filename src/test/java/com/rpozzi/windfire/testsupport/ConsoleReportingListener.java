package com.rpozzi.windfire.testsupport;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

/**
 * Prints every test as "what it is" (@DisplayName), "what it does" (@Does) and its result, and appends one row per
 * test to a TSV file that run-tests.sh turns into the final summary.
 * <p>
 * System properties: {@code wf.console.prefix} (marks the lines so run-tests.sh can pick them out of the Maven log),
 * {@code wf.color} (ANSI colours), {@code wf.results.file} (TSV path, default target/test-results/results.tsv).
 */
public class ConsoleReportingListener implements TestExecutionListener {

	private static final String RED = "\u001b[1;31m";
	private static final String GREEN = "\u001b[1;32m";
	private static final String YELLOW = "\u001b[1;33m";
	private static final String CYAN = "\u001b[1;36m";
	private static final String END = "\u001b[0m";
	private static final int HEADER_WIDTH = 78;

	private final String prefix = System.getProperty("wf.console.prefix", "");
	private final boolean color = Boolean.getBoolean("wf.color");
	private final Path resultsFile = Paths.get(System.getProperty("wf.results.file", "target/test-results/results.tsv"));
	private final Map<String, Long> startTimes = new ConcurrentHashMap<>();
	private TestPlan testPlan;
	private String lastArea;

	@Override
	public void testPlanExecutionStarted(TestPlan testPlan) {
		this.testPlan = testPlan;
		try {
			Files.createDirectories(resultsFile.toAbsolutePath().getParent());
		} catch (IOException e) {
			System.err.println("ConsoleReportingListener: cannot create " + resultsFile + ": " + e.getMessage());
		}
	}

	@Override
	public void executionStarted(TestIdentifier id) {
		startTimes.put(id.getUniqueId(), System.currentTimeMillis());
		if (id.isTest()) {
			printHeaderIfNewArea(id);
			print("  " + paint(CYAN, "▶ ") + testName(id));
			does(id).ifPresent(does -> print("      does: " + does));
		}
	}

	@Override
	public void executionSkipped(TestIdentifier id, String reason) {
		if (!id.isTest() && !isClass(id)) {
			return;
		}
		printHeaderIfNewArea(id);
		String name = id.isTest() ? testName(id) : "(all tests in class)";
		print("  " + paint(CYAN, "▶ ") + name);
		print("      " + paint(YELLOW, "⊘ SKIP") + " — " + reason);
		record(id, name, "SKIP", 0, reason);
	}

	@Override
	public void executionFinished(TestIdentifier id, TestExecutionResult result) {
		long elapsed = System.currentTimeMillis() - startTimes.getOrDefault(id.getUniqueId(), System.currentTimeMillis());
		if (id.isTest()) {
			report(id, testName(id), result, elapsed);
		} else if (isClass(id) && result.getStatus() != TestExecutionResult.Status.SUCCESSFUL) {
			// A class-level failure (e.g. @BeforeAll) is otherwise invisible in the per-test lines
			printHeaderIfNewArea(id);
			report(id, "(class setup)", result, elapsed);
		}
	}

	private void report(TestIdentifier id, String name, TestExecutionResult result, long elapsed) {
		String message = result.getThrowable().map(ConsoleReportingListener::firstLine).orElse("");
		switch (result.getStatus()) {
		case SUCCESSFUL -> {
			print("      " + paint(GREEN, "✔ PASS") + " (" + elapsed + " ms)");
			record(id, name, "PASS", elapsed, "");
		}
		case ABORTED -> {
			print("      " + paint(YELLOW, "⊘ SKIP") + " — " + message);
			record(id, name, "SKIP", elapsed, message);
		}
		case FAILED -> {
			print("      " + paint(RED, "✘ FAIL") + " — " + message);
			record(id, name, "FAIL", elapsed, message);
		}
		}
	}

	private void printHeaderIfNewArea(TestIdentifier id) {
		String area = area(id);
		if (!area.equals(lastArea)) {
			lastArea = area;
			String title = "━━ " + area + " ";
			print("");
			print(paint(CYAN, title + "━".repeat(Math.max(3, HEADER_WIDTH - title.length()))));
		}
	}

	/** Outer and @Nested class display names, joined: "Security · Authorization". */
	private String area(TestIdentifier id) {
		List<String> names = new ArrayList<>();
		for (Optional<TestIdentifier> current = Optional.of(id); current.isPresent(); current = testPlan.getParent(current.get())) {
			if (isClass(current.get())) {
				names.add(0, current.get().getDisplayName());
			}
		}
		return String.join(" · ", names);
	}

	/** Parameterized invocations get the method name in front of their "[n] arguments" display name. */
	private String testName(TestIdentifier id) {
		Optional<TestIdentifier> parent = testPlan.getParent(id);
		if (parent.isPresent() && parent.get().getSource().filter(MethodSource.class::isInstance).isPresent()) {
			return parent.get().getDisplayName() + " " + id.getDisplayName();
		}
		return id.getDisplayName();
	}

	private static Optional<String> does(TestIdentifier id) {
		return id.getSource().filter(MethodSource.class::isInstance).map(MethodSource.class::cast).flatMap(source -> {
			try {
				Method method = source.getJavaMethod();
				Does does = method.getAnnotation(Does.class);
				return Optional.ofNullable(does).map(Does::value);
			} catch (RuntimeException e) {
				return Optional.empty();
			}
		});
	}

	private static boolean isClass(TestIdentifier id) {
		return id.getSource().filter(ClassSource.class::isInstance).isPresent();
	}

	/** unit = plain JUnit, web = @WebMvcTest slice, context = full @SpringBootTest. */
	private String layer(TestIdentifier id) {
		Class<?> outermost = null;
		for (Optional<TestIdentifier> current = Optional.of(id); current.isPresent(); current = testPlan.getParent(current.get())) {
			Optional<TestSource> source = current.get().getSource();
			if (source.isPresent() && source.get() instanceof ClassSource classSource) {
				outermost = classSource.getJavaClass();
			}
		}
		if (outermost == null) {
			return "unit";
		}
		for (Annotation annotation : outermost.getAnnotations()) {
			String name = annotation.annotationType().getSimpleName();
			if (name.equals("WebMvcTest")) {
				return "web";
			}
			if (name.equals("SpringBootTest")) {
				return "context";
			}
		}
		return "unit";
	}

	private synchronized void record(TestIdentifier id, String name, String status, long elapsed, String message) {
		String row = String.join("\t", layer(id), area(id), clean(name), status, String.valueOf(elapsed), clean(message)) + "\n";
		try {
			Files.writeString(resultsFile, row, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			System.err.println("ConsoleReportingListener: cannot write " + resultsFile + ": " + e.getMessage());
		}
	}

	private static String firstLine(Throwable throwable) {
		String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
		String first = message.strip().lines().findFirst().orElse("");
		return first.length() > 200 ? first.substring(0, 200) + "…" : first;
	}

	private static String clean(String text) {
		return text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
	}

	private String paint(String ansi, String text) {
		return color ? ansi + text + END : text;
	}

	private void print(String line) {
		System.out.println(prefix + line);
	}

}
