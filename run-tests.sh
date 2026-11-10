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
TEST_ENV=${WF_ENV:-}
STRICT=${WF_STRICT:-false}
RESULTS_DIR=target/test-results
RESULTS_FILE=$RESULTS_DIR/results.tsv
MAVEN_LOG=$RESULTS_DIR/maven.log
START_TIME=$(date +%s)
## -- Local backend (dev live tests) - START
BACKEND_JAR=target/windfire-restaurants-backend-0.0.1-SNAPSHOT.jar
BACKEND_URL=https://localhost:$DEFAULT_APPLICATION_SERVER_PORT
BACKEND_CACERT=${WF_CACERT:-$DEFAULT_ROOT_CA_TRUSTSTORE_DIR/$DEFAULT_ROOT_CA_CERTIFICATE}
BACKEND_LOG=$RESULTS_DIR/backend.log
PACKAGE_LOG=$RESULTS_DIR/package.log
BACKEND_START_TIMEOUT=${WF_BACKEND_START_TIMEOUT:-120}
BACKEND_STOP_TIMEOUT=10
# PID of the backend THIS script started: the only process it ever stops
STARTED_PID=
NO_START=false
KEEP_BACKEND=false
## -- Local backend (dev live tests) - END
####################################
###### Variable section - END ######
####################################

######################################
###### Function section - START ######
######################################
main()
{
	parseArgs "$@"
	resolveEnvironment $#
	preflight
	printPlan
	rm -rf "$RESULTS_DIR"
	mkdir -p "$RESULTS_DIR"
	if [ "$RUN_JUNIT" == "true" ]; then
		runJUnitTests
	fi
	if [ "$RUN_E2E" == "true" ]; then
		if [ "$TEST_ENV" == "dev" ]; then
			ensureBackendJar
			startBackend
		fi
		runE2ETests
		stopBackend
	fi
	printSummary
}

printHelp()
{
	echo "Usage: ./$SCRIPT [option]"
	echo
	echo "  (no option)             asks for the environment, then runs every JUnit test (unit, web slice, full context)"
	echo "                          and the live end-to-end tests on it; without a terminal (CI, pipes) JUnit tests only"
	echo "  --e2e                   JUnit tests, then the live end-to-end tests (test/e2e-tests.sh)"
	echo "  --e2e-only              only the live end-to-end tests"
	echo "  --env <dev|test|prod>   environment the live tests run against (implies --e2e; asked for if not given,"
	echo "                          prod without a terminal):"
	echo "                            dev  = local backend (localhost:$DEFAULT_APPLICATION_SERVER_PORT, dev profile, Swagger public), started in"
	echo "                                   background and stopped at the end, unless one is already running"
	echo "                            test = not configured yet: live tests skipped (set WF_BASE_URL to test a server)"
	echo "                            prod = Raspberry deployment ($DEFAULT_APPLICATION_SERVER_HOST:$DEFAULT_APPLICATION_SERVER_PORT, no profile, Swagger private)"
	echo "  --no-start              dev: never start the backend, fail if none is running"
	echo "  --keep-backend          dev: leave the backend started by this script running after the tests"
	echo "  --strict                live tests that cannot run for missing credentials FAIL instead of SKIP"
	echo "                          (use it before deploying: a PASSED run then means Keycloak and MongoDB were really checked)"
	echo "  --only <pattern>        only the JUnit test classes whose name contains <pattern> (e.g. Security, Validation)"
	echo "  --help                  this help"
	echo
	echo "Examples: ./$SCRIPT                        asks for the environment, runs everything"
	echo "          ./$SCRIPT --env dev              JUnit tests + live tests on a local backend, before deploying"
	echo "          ./$SCRIPT --e2e-only --env prod  only the live tests on the Raspberry deployment"
	echo "          ./$SCRIPT --only Security        only some JUnit tests, no live tests"
	echo
	echo "The live tests need Keycloak and create/delete one restaurant in the backend's MongoDB; they are"
	echo "configured by WF_* environment variables, see the header of test/e2e-tests.sh."
	echo "The dev backend is started from $BACKEND_JAR (rebuilt if older than the sources) with:"
	echo "  WF_MONGODB_PASSWORD      MongoDB dev password (prompted if unset and a terminal is attached)"
	echo "  WF_KEYCLOAK_HOST/PORT    Keycloak the backend trusts ($DEFAULT_KEYCLOAK_HOST:$DEFAULT_KEYCLOAK_PORT)"
	echo "  WF_BACKEND_START_TIMEOUT seconds to wait for it to answer (120); its output goes to $BACKEND_LOG"
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
			--no-start)  NO_START=true
				;;
			--keep-backend) KEEP_BACKEND=true
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
	if [ -n "$TEST_ENV" ] && [ "$TEST_ENV" != "prod" ] && [ "$TEST_ENV" != "dev" ] && [ "$TEST_ENV" != "test" ]; then
		echo "${red}--env must be dev, test or prod, not '$TEST_ENV'${end}"
		exit 2
	fi
}

# resolveEnvironment <number of command line arguments>
resolveEnvironment()
{
	local arg_count=$1
	# No option at all: ask for the environment and run everything; without a terminal stay JUnit-only (CI)
	if [ "$arg_count" == "0" ] && [ -t 0 ]; then
		RUN_E2E=true
	fi
	if [ "$RUN_E2E" != "true" ] || [ -n "$TEST_ENV" ]; then
		return
	fi
	if [ -t 0 ]; then
		selectEnvironment
	else
		TEST_ENV=prod
	fi
}

selectEnvironment()
{
	local selection
	echo "${cyn}Select the environment the live tests run against :${end}"
	echo "${cyn}1. Development${end} (local backend on localhost:$DEFAULT_APPLICATION_SERVER_PORT, started automatically)"
	echo "${cyn}2. Test${end} (not configured yet: live tests skipped)"
	echo "${cyn}3. Production${end} (Raspberry deployment $DEFAULT_APPLICATION_SERVER_HOST:$DEFAULT_APPLICATION_SERVER_PORT)"
	if ! read -r selection; then
		echo "${red}No environment selected${end}"
		exit 2
	fi
	case $selection in
		1)  TEST_ENV=dev ;;
		2)  TEST_ENV=test ;;
		3)  TEST_ENV=prod ;;
		*)  echo "${red}No valid option selected${end}"
		    selectEnvironment ;;
	esac
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
		# dev: the backend may have to be packaged and started
		if [ "$TEST_ENV" == "dev" ] && [ "$NO_START" != "true" ]; then
			tools="$tools mvn java"
		fi
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
		case $TEST_ENV in
			dev)
				if [ "$NO_START" == "true" ]; then
					echo "${blu}    Backend: must already run on $BACKEND_URL (--no-start)${end}"
				else
					echo "${blu}    Backend: $BACKEND_URL, started in background unless already running$( [ "$KEEP_BACKEND" == "true" ] && echo ", kept running afterwards" || echo ", stopped afterwards")${end}"
				fi
				;;
			test)  echo "${blu}    Backend: ${WF_BASE_URL:-not configured, live tests skipped}${end}" ;;
			prod)  echo "${blu}    Backend: ${WF_BASE_URL:-Raspberry deployment https://$DEFAULT_APPLICATION_SERVER_HOST:$DEFAULT_APPLICATION_SERVER_PORT}${end}" ;;
		esac
	fi
}

# The backend answers (any HTTP status: a 503 DOWN is still a started backend, testHealth reports why)
backendResponding()
{
	local code
	code=$(curl -s -o /dev/null -m 3 --cacert "$BACKEND_CACERT" -w '%{http_code}' "$BACKEND_URL/actuator/health" 2>/dev/null)
	[ -n "$code" ] && [ "$code" != "000" ]
}

# Rebuilds the jar when it is missing or older than the sources (mvn test does not package it)
ensureBackendJar()
{
	if backendResponding; then
		return
	fi
	if [ -f "$BACKEND_JAR" ] && [ -z "$(find pom.xml src/main -newer "$BACKEND_JAR" -type f 2>/dev/null | head -1)" ]; then
		return
	fi
	echo
	echo "${grn}Packaging the backend (log: $PACKAGE_LOG) ...${end}"
	if ! mvn -B --no-transfer-progress -DskipTests package > "$PACKAGE_LOG" 2>&1; then
		echo "${red}Packaging failed - last lines of $PACKAGE_LOG:${end}"
		tail -30 "$PACKAGE_LOG"
		exit 2
	fi
}

startBackend()
{
	echo
	if backendResponding; then
		echo "${grn}Backend already running on $BACKEND_URL: using it (it will not be stopped)${end}"
		return
	fi
	if [ "$NO_START" == "true" ]; then
		echo "${red}No backend on $BACKEND_URL and --no-start given: start it with ./start-windfire-restaurants-backend.sh${end}"
		exit 2
	fi
	if [ ! -f "$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_ROOT_CA_CERTIFICATE" ]; then
		echo "${red}Windfire Keycloak Root CA not found: $DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_ROOT_CA_CERTIFICATE${end}"
		echo "${yel}Create it with windfire-security/keycloak/security/setupTLS.sh${end}"
		exit 2
	fi
	local mongodb_password=$WF_MONGODB_PASSWORD
	if [ -z "$mongodb_password" ]; then
		if [ ! -t 0 ]; then
			echo "${red}WF_MONGODB_PASSWORD is not set and no terminal is attached: cannot start the backend${end}"
			exit 2
		fi
		echo "${cyn}Input MongoDB dev password (user windfire-dev-app on windfire-dev.dx6zesz.mongodb.net) : ${end}"
		read -rs mongodb_password
	fi
	# Same environment start-windfire-restaurants-backend.sh sets, with its defaults instead of prompts
	echo "${grn}Starting the backend in background (dev profile, log: $BACKEND_LOG) ...${end}"
	KEYCLOAK_SERVER=${WF_KEYCLOAK_HOST:-$DEFAULT_KEYCLOAK_HOST} \
	KEYCLOAK_SERVER_PORT=${WF_KEYCLOAK_PORT:-$DEFAULT_KEYCLOAK_PORT} \
	CORS_ALLOWED_ORIGINS=${CORS_ALLOWED_ORIGINS:-$DEFAULT_CORS_ALLOWED_ORIGINS} \
	HOME_DIR=$HOME \
	MONGODB_PASSWORD=$mongodb_password \
		java -jar "$BACKEND_JAR" --spring.profiles.active=dev > "$BACKEND_LOG" 2>&1 &
	STARTED_PID=$!
	local i
	for ((i = 0; i < BACKEND_START_TIMEOUT; i++)); do
		if ! kill -0 "$STARTED_PID" 2>/dev/null; then
			STARTED_PID=
			echo "${red}The backend exited during startup - last lines of $BACKEND_LOG:${end}"
			tail -30 "$BACKEND_LOG"
			exit 2
		fi
		if backendResponding; then
			echo "${grn}Backend ready on $BACKEND_URL (PID $STARTED_PID, ${i} s)${end}"
			return
		fi
		sleep 1
	done
	echo "${red}The backend did not answer within ${BACKEND_START_TIMEOUT} s - last lines of $BACKEND_LOG:${end}"
	tail -30 "$BACKEND_LOG"
	exit 2
}

# Stops only the backend this script started, never one started by hand
# (stop-windfire-restaurants-backend.sh is not used: its pgrep would kill any running backend)
stopBackend()
{
	if [ -z "$STARTED_PID" ] || ! kill -0 "$STARTED_PID" 2>/dev/null; then
		STARTED_PID=
		return
	fi
	if [ "$KEEP_BACKEND" == "true" ]; then
		echo
		echo "${yel}Backend left running (--keep-backend): PID $STARTED_PID, log $BACKEND_LOG${end}"
		STARTED_PID=
		return
	fi
	echo
	echo "${grn}Stopping the backend started for the tests (PID $STARTED_PID) ...${end}"
	kill -TERM "$STARTED_PID" 2>/dev/null
	local i
	for ((i = 0; i < BACKEND_STOP_TIMEOUT; i++)); do
		if ! kill -0 "$STARTED_PID" 2>/dev/null; then
			STARTED_PID=
			return
		fi
		sleep 1
	done
	echo "${yel}Backend still running after ${BACKEND_STOP_TIMEOUT} s: killing it${end}"
	kill -KILL "$STARTED_PID" 2>/dev/null
	STARTED_PID=
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
# The backend started for the dev live tests is stopped however the script ends (exit, error, Ctrl-C)
trap stopBackend EXIT
trap 'exit 130' INT TERM
main "$@"
