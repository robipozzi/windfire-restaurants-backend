package com.rpozzi.windfire.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.rpozzi.windfire.testsupport.Does;

/**
 * The Raspberry deployment runs with no Spring profile, so the defaults must keep Swagger off;
 * only the dev profile turns it on.
 */
@DisplayName("Config · Swagger switches per profile")
class SwaggerProfileTest {

	private static Properties load(String resource) throws IOException {
		Properties properties = new Properties();
		try (InputStream in = SwaggerProfileTest.class.getResourceAsStream("/" + resource)) {
			properties.load(in);
		}
		return properties;
	}

	@Test
	@DisplayName("Swagger is off without a profile")
	@Does("Reads application.properties → api-docs, swagger-ui and windfire.swagger.public are all false")
	void swaggerIsOffWithoutProfile() throws IOException {
		Properties defaults = load("application.properties");
		assertEquals("false", defaults.getProperty("springdoc.api-docs.enabled"));
		assertEquals("false", defaults.getProperty("springdoc.swagger-ui.enabled"));
		assertEquals("false", defaults.getProperty("windfire.swagger.public"));
	}

	@Test
	@DisplayName("Swagger is on with the dev profile")
	@Does("Reads application-dev.properties → api-docs, swagger-ui and windfire.swagger.public are all true")
	void swaggerIsOnWithDevProfile() throws IOException {
		Properties dev = load("application-dev.properties");
		assertEquals("true", dev.getProperty("springdoc.api-docs.enabled"));
		assertEquals("true", dev.getProperty("springdoc.swagger-ui.enabled"));
		assertEquals("true", dev.getProperty("windfire.swagger.public"));
	}

	@Test
	@DisplayName("There is no prod profile")
	@Does("application-prod.properties is not on the classpath: prod runs with no profile, so the defaults (Swagger off) apply")
	void noProdProfile() {
		assertNull(SwaggerProfileTest.class.getResource("/application-prod.properties"));
	}

}
