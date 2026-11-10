#!/bin/bash
cd "$(dirname "$0")" || exit 2
source ./setenv.sh

######################################
###### Variable section - START ######
######################################
SCRIPT=run-tests.sh
RUN_JUNIT=true
RUN_E2E=false
ONLY_PATTERN=
TEST_ENV=${WF_ENV:-prod}
STRICT=${WF_STRICT:-false}
RESULTS_DIR=target/test-results
RESULTS_FILE=$RESULTS_DIR/results.tsv
MAVEN_LOG=$RESULTS_DIR/maven.log
START_TIME=$(date +%s)
####################################
###### Variable section - END ######
####################################

######################################
###### Function section - START ######
######################################
main()
{
	parseArgs "$@"
	preflight
	printPlan
	rm -rf "$RESULTS_DIR"
	mkdir -p "$RESULTS_DIR"
	if [ "$RUN_JUNIT" == "true" ]; then
		runJUnitTests
	fi
	if [ "$RUN_E2E" == "true" ]; then
		runE2ETests
	fi
	printSummary
}

printHelp()
{
	echo "Usage: ./$SCRIPT [option]"
	echo
	echo "  (no option)        run every JUnit test (unit, web slice, full context)"
	echo "  --e2e              JUnit tests, then the live end-to-end tests (test/e2e-tests.sh)"
	echo "  --e2e-only         only the live end-to-end tests"
	echo "  --env <prod|dev>   environment the live tests run against (implies --e2e, default prod):"
	echo "                       prod = Raspberry deployment ($DEFAULT_APPLICATION_SERVER_HOST:$DEFAULT_APPLICATION_SERVER_PORT, no profile, Swagger private)"
	echo "                       dev  = local start-windfire-restaurants-backend.sh (localhost:$DEFAULT_APPLICATION_SERVER_PORT, dev profile, Swagger public)"
	echo "  --strict           live tests that cannot run for missing credentials FAIL instead of SKIP"
	echo "                     (use it before deploying: a PASSED run then means Keycloak and MongoDB were really checked)"
	echo "  --only <pattern>   only the JUnit test classes whose name contains <pattern> (e.g. Security, Validation)"
	echo "  --help             this help"
	echo
	echo "Examples: ./$SCRIPT --env dev           JUnit tests + live tests on the local backend, before deploying"
	echo "          ./$SCRIPT --e2e-only --env prod  only the live tests on the Raspberry deployment"
	echo
	echo "The live tests need a running backend and Keycloak, and create/delete one restaurant in its MongoDB;"
	echo "they are configured by WF_* environment variables, see the header of test/e2e-tests.sh."
	echo
	echo "Exit code: 0 all passed, 1 some test failed, 2 the tests could not run (missing tool, build error)."
}

parseArgs()
{
	while [ $# -gt 0 ]; do
		case $1 in
			--e2e)       RUN_E2E=true
				;;
			--e2e-only)  RUN_E2E=true
			             RUN_JUNIT=false
				;;
			--strict)    STRICT=true
				;;
			--env)       TEST_ENV=$2
			             RUN_E2E=true
			             shift
				;;
			--only)      ONLY_PATTERN=$2
			             if [ -z "$ONLY_PATTERN" ]; then
			             	echo "${red}--only needs a pattern${end}"
			             	exit 2
			             fi
			             shift
				;;
			--help|-h)   printHelp
			             exit 0
				;;
			*)           echo "${red}Unknown option: $1${end}"
			             printHelp
			             exit 2
				;;
		esac
		shift
	done
	if [ "$TEST_ENV" != "prod" ] && [ "$TEST_ENV" != "dev" ]; then
		echo "${red}--env must be prod or dev, not '$TEST_ENV'${end}"
		exit 2
	fi
}

preflight()
{
	# Homebrew mvn defaults to the latest openjdk when JAVA_HOME is unset: pin JDK 17, the version the project targets
	if [ -z "$JAVA_HOME" ] && [ -d /opt/homebrew/opt/openjdk@17 ]; then
		export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
	fi
	local tools=""
	if [ "$RUN_JUNIT" == "true" ]; then
		tools="mvn"
	fi
	if [ "$RUN_E2E" == "true" ]; then
		tools="$tools curl jq perl"
	fi
	for tool in $tools; do
		if ! command -v $tool > /dev/null; then
			echo "${red}Error: $tool is required but not installed${end}"
			exit 2
		fi
	done
}

printPlan()
{
	echo "${grn}Windfire Restaurants Backend - test run${end}"
	if [ "$RUN_JUNIT" == "true" ]; then
		if [ -n "$ONLY_PATTERN" ]; then
			echo "${grn}  • JUnit tests in classes matching *$ONLY_PATTERN*${end}"
		else
			echo "${grn}  • JUnit tests: unit, web slice (MockMvc + real SecurityConfig), full application context${end}"
		fi
		echo "${blu}    Java: ${JAVA_HOME:-system default}${end}"
	fi
	if [ "$RUN_E2E" == "true" ]; then
		echo "${grn}  • Live end-to-end tests on the ${end}${mag}$TEST_ENV${end}${grn} environment${end}$( [ "$STRICT" == "true" ] && echo "${grn} (strict)${end}")"
	fi
}

runJUnitTests()
{
	echo
	echo "${grn}Running JUnit tests with Maven (full log: $MAVEN_LOG) ...${end}"
	local maven_args=(-B --no-transfer-progress test
		"-Dwf.console.prefix=[WFTEST]" -Dwf.color=true "-Dwf.results.file=$PWD/$RESULTS_FILE")
	if [ -n "$ONLY_PATTERN" ]; then
		maven_args+=("-Dtest=*${ONLY_PATTERN}*" -Dsurefire.failIfNoSpecifiedTests=false)
	fi
	# Only the reporter's lines reach the console; Maven and Spring output stay in the log
	mvn "${maven_args[@]}" 2>&1 | tee "$MAVEN_LOG" \
		| awk '/^\[WFTEST\]/ { sub(/^\[WFTEST\]/, ""); print; fflush() }'
	local maven_status=${PIPESTATUS[0]}
	if [ "$maven_status" != "0" ] && ! grep -qv "^e2e" "$RESULTS_FILE" 2>/dev/null; then
		echo
		echo "${red}Maven failed before any test could run - last lines of $MAVEN_LOG:${end}"
		tail -30 "$MAVEN_LOG"
		exit 2
	fi
}

runE2ETests()
{
	echo
	echo "${grn}Running live end-to-end tests on the $TEST_ENV environment ...${end}"
	WF_ENV=$TEST_ENV WF_STRICT=$STRICT WF_RESULTS_FILE="$PWD/$RESULTS_FILE" ./test/e2e-tests.sh
}

printSummary()
{
	local elapsed=$(( $(date +%s) - START_TIME ))
	if [ ! -s "$RESULTS_FILE" ]; then
		echo
		echo "${red}No test was run${ONLY_PATTERN:+ (no test class matches *$ONLY_PATTERN*)}${end}"
		exit 2
	fi
	echo
	echo "${cyn}═══════════════════════════════ TEST SUMMARY ═══════════════════════════════${end}"
	# C locale: length() counts bytes, so UTF-8 continuation bytes are discounted to pad by visible width
	LC_ALL=C awk -F'\t' -v red="$red" -v grn="$grn" -v yel="$yel" -v end="$end" '
		function pad(text, width,    visible) {
			visible = text
			gsub(/[\200-\277]/, "", visible)
			return text sprintf("%" (width - length(visible) > 0 ? width - length(visible) : 0) "s", "")
		}
		function counts(t, p, f, s) {
			return sprintf("%6d %s%6d%s %s%6d%s %s%6d%s", t, grn, p, end, (f ? red : ""), f, (f ? end : ""), (s ? yel : ""), s, (s ? end : ""))
		}
		{
			key = $1 "\t" $2
			if (!(key in total)) { order[++n] = key }
			total[key]++; all++
			if ($4 == "PASS") { pass[key]++; allPass++ }
			if ($4 == "FAIL") { fail[key]++; allFail++; failures[++nf] = "  " red "✘" end " [" $1 "] " $2 " › " $3 " — " $6 }
			if ($4 == "SKIP") { skip[key]++; allSkip++ }
		}
		END {
			printf "%s%s%6s %6s %6s %6s\n", pad("Layer", 9), pad("Area", 50), "Total", "Pass", "Fail", "Skip"
			for (i = 1; i <= n; i++) {
				split(order[i], parts, "\t")
				printf "%s%s%s\n", pad(parts[1], 9), pad(parts[2], 50), counts(total[order[i]], pass[order[i]], fail[order[i]], skip[order[i]])
			}
			print "────────────────────────────────────────────────────────────────────────────"
			printf "%s%s%s\n", pad("TOTAL", 9), pad("", 50), counts(all, allPass, allFail, allSkip)
			if (nf > 0) {
				print ""
				print red "FAILED:" end
				for (i = 1; i <= nf; i++) { print failures[i] }
			}
		}' "$RESULTS_FILE"
	local failed
	failed=$(awk -F'\t' '$4 == "FAIL"' "$RESULTS_FILE" | wc -l | tr -d ' ')
	local files="results: $RESULTS_FILE"
	if [ "$RUN_JUNIT" == "true" ]; then
		files="$files, Maven log: $MAVEN_LOG"
	fi
	# A skipped live test means part of the environment was not checked: a PASSED run must not hide that
	local e2e_skipped
	e2e_skipped=$(awk -F'\t' '$1 == "e2e" && $4 == "SKIP"' "$RESULTS_FILE" | wc -l | tr -d ' ')
	if [ "$e2e_skipped" != "0" ]; then
		echo
		echo "${yel}⚠ $e2e_skipped live test(s) skipped: those checks were NOT verified on $TEST_ENV (reasons above; --strict makes missing credentials fail)${end}"
	fi
	echo
	if [ "$failed" == "0" ]; then
		echo "${grn}RESULT: PASSED${end} in ${elapsed} s — $files"
		exit 0
	fi
	echo "${red}RESULT: FAILED ($failed failure(s))${end} in ${elapsed} s — $files"
	exit 1
}
####################################
###### Function section - END ######
####################################

# ##############################################
# #################### MAIN ####################
# ##############################################
main "$@"
