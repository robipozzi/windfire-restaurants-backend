#!/bin/bash
# Live end-to-end tests against a running windfire-restaurants-backend, its Keycloak and its MongoDB.
# Normally launched by ../run-tests.sh --e2e / --env; can also be run on its own from this folder.
#
# WARNING: the writer tests create (and then delete) restaurants named E2E-<timestamp>... in the REAL
# MongoDB database the backend is connected to. dev and prod use the SAME database.
#
# Configuration (environment variables, defaults from setenv.sh):
#   WF_ENV               environment under test: prod or dev (prod)
#                          prod = Raspberry deployment, no Spring profile: Swagger must be private
#                          dev  = local backend started by run-windfire-restaurants-backend.sh with the
#                                 dev profile: Swagger must be public
#   WF_BASE_URL          backend base URL                  (prod: https://raspberry02:8081, dev: https://localhost:8081)
#   WF_CACERT            Windfire Root CA for the backend  ($DEFAULT_ROOT_CA_TRUSTSTORE_DIR/$DEFAULT_ROOT_CA_CERTIFICATE)
#   WF_KEYCLOAK_URL      Keycloak base URL                 (https://raspberry01:8443)
#   WF_KEYCLOAK_CACERT   Keycloak Root CA                  ($DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_ROOT_CA_CERTIFICATE)
#   WF_REALM             Keycloak realm                    (windfire)
#   WF_CLIENT_ID         Keycloak client used for login    (windfire)
#   WF_API_CLIENT_ID     client whose roles the API checks (windfire-restaurants-api)
#   WF_READER_USER       user with restaurants-reader      (windfire-reader)
#   WF_READER_PASSWORD   prompted if unset and a terminal is attached
#   WF_WRITER_USER       user with restaurants-writer      (windfire)
#   WF_WRITER_PASSWORD   prompted if unset and a terminal is attached
#   WF_NOROLE_USER       user with NO API role             (windfire-norole)
#   WF_NOROLE_PASSWORD   prompted if unset and a terminal is attached
#   WF_ALLOWED_ORIGIN    an origin in CORS_ALLOWED_ORIGINS (prod: https://raspberry01:9443, dev: https://localhost)
#   WF_STRICT            true → tests that cannot run for missing credentials FAIL instead of SKIP (false)
#   WF_RESULTS_FILE      TSV file results are appended to  (../target/test-results/results.tsv)
cd "$(dirname "$0")" || exit 2
source ../setenv.sh

######################################
###### Variable section - START ######
######################################
SCRIPT=e2e-tests.sh
WF_ENV=${WF_ENV:-prod}
case $WF_ENV in
	# Raspberry deployment: allowed origin from deployment/raspberry/conf/config.yml
	prod)  DEFAULT_BASE_URL=https://$DEFAULT_APPLICATION_SERVER_HOST:$DEFAULT_APPLICATION_SERVER_PORT
	       DEFAULT_ALLOWED_ORIGIN=https://raspberry01:9443
	       SWAGGER_PUBLIC=false
		;;
	# Local backend: allowed origins are DEFAULT_CORS_ALLOWED_ORIGINS from setenv.sh
	dev)   DEFAULT_BASE_URL=https://localhost:$DEFAULT_APPLICATION_SERVER_PORT
	       DEFAULT_ALLOWED_ORIGIN=https://localhost
	       SWAGGER_PUBLIC=true
		;;
	*)     echo "${red}WF_ENV must be prod or dev, not '$WF_ENV'${end}"
	       exit 2
		;;
esac
WF_BASE_URL=${WF_BASE_URL:-$DEFAULT_BASE_URL}
WF_CACERT=${WF_CACERT:-$DEFAULT_ROOT_CA_TRUSTSTORE_DIR/$DEFAULT_ROOT_CA_CERTIFICATE}
WF_KEYCLOAK_URL=${WF_KEYCLOAK_URL:-https://$DEFAULT_KEYCLOAK_HOST:$DEFAULT_KEYCLOAK_PORT}
WF_KEYCLOAK_CACERT=${WF_KEYCLOAK_CACERT:-$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_ROOT_CA_CERTIFICATE}
WF_REALM=${WF_REALM:-windfire}
WF_CLIENT_ID=${WF_CLIENT_ID:-windfire}
WF_API_CLIENT_ID=${WF_API_CLIENT_ID:-windfire-restaurants-api}
WF_READER_USER=${WF_READER_USER:-windfire-reader}
WF_WRITER_USER=${WF_WRITER_USER:-windfire}
WF_NOROLE_USER=${WF_NOROLE_USER:-windfire-norole}
WF_ALLOWED_ORIGIN=${WF_ALLOWED_ORIGIN:-$DEFAULT_ALLOWED_ORIGIN}
WF_STRICT=${WF_STRICT:-false}
WF_RESULTS_FILE=${WF_RESULTS_FILE:-../target/test-results/results.tsv}
AREA=
READER_TOKEN=
WRITER_TOKEN=
CREATED_ID=
# Every restaurant this run stored and has not deleted yet: the EXIT trap removes them
CREATED_IDS=
CREATED_NAME=E2E-$(date +%Y%m%d-%H%M%S)
CREATED_JSON=
HTTP_STATUS=
HTTP_BODY=
HTTP_HEADERS=
KC_STATUS=
KC_BODY=
RESULT_MESSAGE=
BACKEND_UNREACHABLE=false
TMP_DIR=$(mktemp -d)
####################################
###### Variable section - END ######
####################################

######################################
###### Function section - START ######
######################################
main()
{
	trap cleanup EXIT
	mkdir -p "$(dirname "$WF_RESULTS_FILE")"
	if [ "$WF_STRICT" == "true" ]; then
		echo "${blu}Env      : $WF_ENV (strict: tests that cannot run for missing credentials fail)${end}"
	else
		echo "${blu}Env      : $WF_ENV${end}"
	fi
	echo "${blu}Backend  : $WF_BASE_URL${end}"
	echo "${blu}Keycloak : $WF_KEYCLOAK_URL/realms/$WF_REALM (login client $WF_CLIENT_ID, API client $WF_API_CLIENT_ID)${end}"
	inputPasswords

	area "Live API [$WF_ENV] · TLS & health"
	runTest "Server certificate is trusted" \
		"TLS handshake with $WF_BASE_URL verifying against the Windfire Root CA ($WF_CACERT) succeeds" testTls
	runTest "Health endpoint is public and MongoDB is reachable" \
		"GET /actuator/health without a token → 200 {status: UP} (the status includes the backend's MongoDB health check)" testHealth

	area "Live API [$WF_ENV] · Keycloak & authentication"
	runTest "Request without a token is unauthorized" \
		"GET /restaurants with no Authorization header → 401, message 'No Bearer token provided'" testNoToken
	runTest "Garbage token is unauthorized" \
		"GET /restaurants with 'Bearer not-a-jwt' → 401, message 'Invalid Bearer token provided'" testGarbageToken
	runTest "Keycloak rejects a wrong password" \
		"POST the token endpoint as $WF_READER_USER with a wrong password → 400/401 invalid_grant from Keycloak, no access_token" testWrongPassword
	runTest "Keycloak issues a reader token" \
		"POST $WF_KEYCLOAK_URL/realms/$WF_REALM/protocol/openid-connect/token as $WF_READER_USER → 200 with access_token" testReaderLogin
	runTest "Reader token carries what the API needs" \
		"Decode the reader token → iss = $WF_KEYCLOAK_URL/realms/$WF_REALM, aud contains $WF_API_CLIENT_ID, role restaurants-reader on $WF_API_CLIENT_ID" testReaderClaims
	runTest "Keycloak issues a writer token" \
		"POST the token endpoint as $WF_WRITER_USER → 200 with access_token" testWriterLogin
	runTest "Writer token carries what the API needs" \
		"Decode the writer token → same issuer and audience, role restaurants-writer on $WF_API_CLIENT_ID" testWriterClaims

	area "Live API [$WF_ENV] · Authorization"
	runTest "Reader can list restaurants" \
		"GET /restaurants with the reader token → 200, body is a JSON array" testReaderList
	runTest "Writer can list restaurants" \
		"GET /restaurants with the writer token → 200, body is a JSON array (writers read too)" testWriterList
	runTest "Reader cannot create" \
		"POST /restaurants with the reader token → 403" testReaderCannotPost
	runTest "User without any API role is forbidden" \
		"Log in as $WF_NOROLE_USER, GET /restaurants → 403" testNoRoleForbidden

	area "Live API [$WF_ENV] · MongoDB round trip"
	runTest "Writer creates a restaurant" \
		"POST /restaurants {name: $CREATED_NAME, every field filled} with the writer token → 200 with a generated id" testWriterCreate
	runTest "Every field is stored and read back" \
		"GET /restaurants/{created id} with the reader token → 200, all fields incl. the nested address equal what was sent" testReadBack
	runTest "New restaurant appears in the list" \
		"GET /restaurants with the reader token → the list contains the created id" testInList
	runTest "Client-supplied id cannot overwrite a document" \
		"POST {id: <created id>, name: $CREATED_NAME-copy} → 200 with a DIFFERENT id, the original keeps its name; the copy is deleted" testNoOverwrite
	runTest "Invalid restaurant is not stored" \
		"POST {name: $CREATED_NAME-invalid, website: javascript:alert(1)} → 400 validation_failed, and no document with that name in the list" testInvalidNotStored
	runTest "Reader cannot delete" \
		"DELETE /restaurants/{created id} with the reader token → 403, the restaurant is still there" testReaderCannotDelete
	runTest "Writer deletes the restaurant" \
		"DELETE /restaurants/{created id} with the writer token → 200, then GET the id → 404 and the id is gone from the list" testWriterDelete
	runTest "Unknown id is not found" \
		"GET /restaurants/e2e-no-such-id with the reader token → 404 {error: not_found}" testUnknownId
	runTest "Deleting an unknown id is not found" \
		"DELETE /restaurants/e2e-no-such-id with the writer token → 404 {error: not_found}" testDeleteUnknown

	area "Live API [$WF_ENV] · CORS & Swagger"
	runTest "Preflight from the allowed origin is accepted" \
		"OPTIONS /restaurants from $WF_ALLOWED_ORIGIN asking GET → 200, origin echoed in Access-Control-Allow-Origin" testCorsAllowed
	runTest "Preflight from an unknown origin is rejected" \
		"OPTIONS /restaurants from https://evil.example asking GET → 403" testCorsRejected
	if [ "$SWAGGER_PUBLIC" == "true" ]; then
		runTest "API docs are public with the dev profile" \
			"GET /v3/api-docs without a token → 200, info.title 'Windfire Restaurants API'" testSwaggerDocsPublic
		runTest "Swagger UI is public with the dev profile" \
			"GET /swagger-ui/index.html without a token → 200" testSwaggerUiPublic
	else
		runTest "API docs are not public on this server" \
			"GET /v3/api-docs without a token → 401 (Swagger is only public with the dev profile)" testSwaggerNotPublic
	fi
}

inputPasswords()
{
	if [ -z "$WF_READER_PASSWORD" ] && [ -t 0 ]; then
		echo ${cyn}Input password of Keycloak reader user ${end}${mag}$WF_READER_USER${end}${cyn} : ${end}
		read -s WF_READER_PASSWORD
	fi
	if [ -z "$WF_WRITER_PASSWORD" ] && [ -t 0 ]; then
		echo ${cyn}Input password of Keycloak writer user ${end}${mag}$WF_WRITER_USER${end}${cyn} : ${end}
		read -s WF_WRITER_PASSWORD
	fi
	if [ -z "$WF_NOROLE_PASSWORD" ] && [ -t 0 ]; then
		echo ${cyn}Input password of Keycloak user without roles ${end}${mag}$WF_NOROLE_USER${end}${cyn} : ${end}
		read -s WF_NOROLE_PASSWORD
	fi
}

cleanup()
{
	# Removes what the run stored but did not delete (interrupted run, or a failed delete test)
	if [ -n "$CREATED_IDS" ] && [ -n "$WRITER_TOKEN" ]; then
		for id in $CREATED_IDS; do
			echo "${yel}Cleaning up restaurant $id ...${end}"
			http DELETE "/restaurants/$id" -H "Authorization: Bearer $WRITER_TOKEN"
		done
	fi
	rm -rf "$TMP_DIR"
}

trackCreated()
{
	CREATED_IDS="$CREATED_IDS $1"
}

untrackCreated()
{
	local id remaining=""
	for id in $CREATED_IDS; do
		if [ "$id" != "$1" ]; then remaining="$remaining $id"; fi
	done
	CREATED_IDS=$remaining
}

nowMs()
{
	perl -MTime::HiRes=time -e 'printf "%d", time * 1000'
}

area()
{
	AREA=$1
	# perl counts characters, not bytes, so the bar lines up with the JUnit headers (78 columns)
	echo
	echo "${cyn}$(perl -CSA -Mutf8 -e 'my $t = "━━ $ARGV[0] "; print $t, "━" x (78 - length $t)' "$AREA")${end}"
}

# runTest <name> <does> <function>: the function returns 0 = pass, 1 = fail, 2 = skip,
# 3 = not verified for missing credentials (skip, or fail with WF_STRICT=true); the reason is in RESULT_MESSAGE
runTest()
{
	local name=$1 does=$2 fn=$3 start status label
	echo "  ${cyn}▶ ${end}$name"
	echo "      does: $does"
	RESULT_MESSAGE=
	start=$(nowMs)
	if [ "$BACKEND_UNREACHABLE" == "true" ]; then
		# One connection failure is reported once, by the TLS test, instead of by every test
		RESULT_MESSAGE="backend $WF_BASE_URL is not reachable"
		status=2
	else
		$fn
		status=$?
	fi
	if [ "$status" == "3" ]; then
		if [ "$WF_STRICT" == "true" ]; then
			RESULT_MESSAGE="NOT verified (strict mode): $RESULT_MESSAGE"
			status=1
		else
			status=2
		fi
	fi
	local elapsed=$(( $(nowMs) - start ))
	case $status in
		0)  echo "      ${grn}✔ PASS${end} ($elapsed ms)${RESULT_MESSAGE:+ — $RESULT_MESSAGE}"; label=PASS ;;
		2)  echo "      ${yel}⊘ SKIP${end} — $RESULT_MESSAGE"; label=SKIP ;;
		*)  echo "      ${red}✘ FAIL${end} — $RESULT_MESSAGE"; label=FAIL ;;
	esac
	printf '%s\t%s\t%s\t%s\t%s\t%s\n' e2e "$AREA" "$name" "$label" "$elapsed" "$RESULT_MESSAGE" >> "$WF_RESULTS_FILE"
}

# http <method> <path> [curl args...]: sets HTTP_STATUS (000 when the connection fails), HTTP_BODY, HTTP_HEADERS
http()
{
	local method=$1 path=$2
	shift 2
	HTTP_STATUS=$(curl -s -X "$method" "$WF_BASE_URL$path" --cacert "$WF_CACERT" --max-time 15 \
		-D "$TMP_DIR/headers" -o "$TMP_DIR/body" -w '%{http_code}' "$@")
	HTTP_BODY=$(cat "$TMP_DIR/body" 2>/dev/null)
	HTTP_HEADERS=$(cat "$TMP_DIR/headers" 2>/dev/null)
}

# expectStatus <expected>: fails with the actual status and the start of the body
expectStatus()
{
	if [ "$HTTP_STATUS" != "$1" ]; then
		RESULT_MESSAGE="expected HTTP $1 but got $HTTP_STATUS ${HTTP_BODY:0:120}"
		return 1
	fi
}

# expectJson <jq filter> <expected value>
expectJson()
{
	local actual
	actual=$(echo "$HTTP_BODY" | jq -r "$1" 2>/dev/null)
	if [ "$actual" != "$2" ]; then
		RESULT_MESSAGE="expected $1 = '$2' but got '$actual'"
		return 1
	fi
}

# listContains <id>: GET /restaurants as reader, true if the list has that id
listContains()
{
	http GET /restaurants -H "Authorization: Bearer $READER_TOKEN"
	[ "$(echo "$HTTP_BODY" | jq --arg id "$1" 'any(.[]; .id == $id)' 2>/dev/null)" == "true" ]
}

readerCredentialsMissing()
{
	[ -z "$WF_READER_PASSWORD" ]
}

writerCredentialsMissing()
{
	[ -z "$WF_WRITER_PASSWORD" ]
}

# A missing token is "not verified" (3) when no credentials were given, a plain skip (2) when the login failed
requireReader()
{
	[ -n "$READER_TOKEN" ] && return 0
	if readerCredentialsMissing; then
		RESULT_MESSAGE="WF_READER_PASSWORD not set — reader access not verified"
		return 3
	fi
	RESULT_MESSAGE="no reader token (the reader login failed above)"
	return 2
}

requireWriter()
{
	[ -n "$WRITER_TOKEN" ] && return 0
	if writerCredentialsMissing; then
		RESULT_MESSAGE="WF_WRITER_PASSWORD not set — writer access and MongoDB writes not verified"
		return 3
	fi
	RESULT_MESSAGE="no writer token (the writer login failed above)"
	return 2
}

requireCreated()
{
	[ -n "$CREATED_ID" ] && return 0
	requireWriter || return
	RESULT_MESSAGE="no E2E restaurant was created (see above)"
	return 2
}

# keycloakToken <user> <password>: sets KC_STATUS and KC_BODY
keycloakToken()
{
	KC_STATUS=$(curl -s -X POST "$WF_KEYCLOAK_URL/realms/$WF_REALM/protocol/openid-connect/token" \
		--cacert "$WF_KEYCLOAK_CACERT" --max-time 15 -o "$TMP_DIR/kc" -w '%{http_code}' \
		-H "Content-Type: application/x-www-form-urlencoded" \
		--data-urlencode "client_id=$WF_CLIENT_ID" \
		--data-urlencode "username=$1" \
		--data-urlencode "password=$2" \
		--data-urlencode "grant_type=password")
	KC_BODY=$(cat "$TMP_DIR/kc" 2>/dev/null)
}

# login <user> <password> <var>: stores the access token in <var>, fails with Keycloak's answer
login()
{
	keycloakToken "$1" "$2"
	local token
	token=$(echo "$KC_BODY" | jq -r '.access_token // empty' 2>/dev/null)
	if [ -z "$token" ]; then
		RESULT_MESSAGE="Keycloak answered HTTP $KC_STATUS for $1: $(echo "$KC_BODY" | jq -r '.error_description // .error // empty' 2>/dev/null)"
		return 1
	fi
	printf -v "$3" '%s' "$token"
}

# jwtPayload <token>: prints the decoded JSON payload (base64url, unpadded)
jwtPayload()
{
	local payload
	payload=$(echo "$1" | cut -d. -f2 | tr '_-' '/+')
	while [ $(( ${#payload} % 4 )) -ne 0 ]; do payload="$payload="; done
	echo "$payload" | base64 --decode 2>/dev/null
}

# checkClaims <token> <role>: the claims SecurityConfig relies on; the message says what to fix in Keycloak
checkClaims()
{
	local claims expected_iss
	claims=$(jwtPayload "$1")
	expected_iss="$WF_KEYCLOAK_URL/realms/$WF_REALM"
	if [ "$(echo "$claims" | jq -r .iss)" != "$expected_iss" ]; then
		RESULT_MESSAGE="iss is '$(echo "$claims" | jq -r .iss)', not '$expected_iss' (the backend's issuer-uri must match it)"
		return 1
	fi
	if [ "$(echo "$claims" | jq --arg c "$WF_API_CLIENT_ID" '(.aud | if type == "array" then . else [.] end) | index($c) != null')" != "true" ]; then
		RESULT_MESSAGE="aud $(echo "$claims" | jq -c .aud) lacks $WF_API_CLIENT_ID: add an audience mapper for it to client $WF_CLIENT_ID"
		return 1
	fi
	if [ "$(echo "$claims" | jq --arg c "$WF_API_CLIENT_ID" --arg r "$2" '(.resource_access[$c].roles // []) | index($r) != null')" != "true" ]; then
		RESULT_MESSAGE="no client role $2 on $WF_API_CLIENT_ID (has $(echo "$claims" | jq -c --arg c "$WF_API_CLIENT_ID" '.resource_access[$c].roles // []')): assign it to the user"
		return 1
	fi
}

restaurantJson()
{
	# $1 = name, $2 = optional id, $3 = website
	jq -n -c --arg name "$1" --arg id "$2" --arg website "${3:-https://example.com/e2e}" '{
		name: $name,
		address: {zipCode: "28040", city: "Lesa", street: "Viale Vittorio Veneto, 2", province: "NO", region: "Piemonte", country: "Italia"},
		cuisine: "E2E test", phone: "0321000000", mobile: "3330000000", email: "e2e@example.com", website: $website
	} + (if $id == "" then {} else {id: $id} end)'
}

testTls()
{
	if [ ! -f "$WF_CACERT" ]; then
		RESULT_MESSAGE="Root CA certificate not found: $WF_CACERT"
		return 1
	fi
	local error
	if ! error=$(curl -s -S -o /dev/null --cacert "$WF_CACERT" --max-time 15 "$WF_BASE_URL/actuator/health" 2>&1); then
		RESULT_MESSAGE=$error
		# curl 6/7/28: cannot resolve, cannot connect, timeout (a TLS failure still means the server is up)
		case $error in
			*"curl: (6)"*|*"curl: (7)"*|*"curl: (28)"*) BACKEND_UNREACHABLE=true ;;
		esac
		if [ "$WF_ENV" == "dev" ]; then
			RESULT_MESSAGE="$RESULT_MESSAGE (is the local backend running? start it with ./run-windfire-restaurants-backend.sh)"
		fi
		return 1
	fi
}

testHealth()
{
	http GET /actuator/health
	expectStatus 200 && expectJson .status UP
}

testNoToken()
{
	http GET /restaurants
	expectStatus 401 && expectJson .message "No Bearer token provided"
}

testGarbageToken()
{
	http GET /restaurants -H "Authorization: Bearer not-a-jwt"
	expectStatus 401 && expectJson .message "Invalid Bearer token provided"
}

testWrongPassword()
{
	keycloakToken "$WF_READER_USER" "e2e-wrong-password-$(date +%s)"
	# OAuth2 answers a bad password grant with 400 invalid_grant (older Keycloak versions use 401)
	if [ "$KC_STATUS" != "400" ] && [ "$KC_STATUS" != "401" ]; then
		RESULT_MESSAGE="expected HTTP 400/401 from Keycloak but got $KC_STATUS ${KC_BODY:0:120}"
		return 1
	fi
	if [ "$(echo "$KC_BODY" | jq -r '.error // empty' 2>/dev/null)" != "invalid_grant" ]; then
		RESULT_MESSAGE="expected error invalid_grant but got ${KC_BODY:0:120}"
		return 1
	fi
	if [ -n "$(echo "$KC_BODY" | jq -r '.access_token // empty' 2>/dev/null)" ]; then
		RESULT_MESSAGE="Keycloak returned an access_token for a wrong password"
		return 1
	fi
}

testReaderLogin()
{
	if readerCredentialsMissing; then
		RESULT_MESSAGE="WF_READER_PASSWORD not set — Keycloak login not verified"
		return 3
	fi
	login "$WF_READER_USER" "$WF_READER_PASSWORD" READER_TOKEN
}

testReaderClaims()
{
	requireReader || return
	checkClaims "$READER_TOKEN" restaurants-reader
}

testWriterLogin()
{
	if writerCredentialsMissing; then
		RESULT_MESSAGE="WF_WRITER_PASSWORD not set — writer login not verified"
		return 3
	fi
	login "$WF_WRITER_USER" "$WF_WRITER_PASSWORD" WRITER_TOKEN
}

testWriterClaims()
{
	requireWriter || return
	checkClaims "$WRITER_TOKEN" restaurants-writer
}

testReaderList()
{
	requireReader || return
	http GET /restaurants -H "Authorization: Bearer $READER_TOKEN"
	expectStatus 200 && expectJson type array
}

testWriterList()
{
	requireWriter || return
	http GET /restaurants -H "Authorization: Bearer $WRITER_TOKEN"
	expectStatus 200 && expectJson type array
}

testReaderCannotPost()
{
	requireReader || return
	http POST /restaurants -H "Authorization: Bearer $READER_TOKEN" -H "Content-Type: application/json" \
		-d "$(restaurantJson "$CREATED_NAME-reader")"
	if [ "$HTTP_STATUS" == "200" ]; then
		# Authorization is broken: do not leave the document behind
		trackCreated "$(echo "$HTTP_BODY" | jq -r '.id // empty')"
	fi
	expectStatus 403
}

testNoRoleForbidden()
{
	if [ -z "$WF_NOROLE_PASSWORD" ]; then
		RESULT_MESSAGE="WF_NOROLE_PASSWORD not set — 403 for a user without API roles not verified"
		return 3
	fi
	# Not named "token": login's own local would shadow it and the printf -v would never reach this one
	local norole_token
	login "$WF_NOROLE_USER" "$WF_NOROLE_PASSWORD" norole_token || return 1
	http GET /restaurants -H "Authorization: Bearer $norole_token"
	expectStatus 403
}

testWriterCreate()
{
	requireWriter || return
	CREATED_JSON=$(restaurantJson "$CREATED_NAME")
	http POST /restaurants -H "Authorization: Bearer $WRITER_TOKEN" -H "Content-Type: application/json" -d "$CREATED_JSON"
	expectStatus 200 || return
	CREATED_ID=$(echo "$HTTP_BODY" | jq -r '.id // empty')
	if [ -z "$CREATED_ID" ]; then
		RESULT_MESSAGE="response has no id: ${HTTP_BODY:0:120}"
		return 1
	fi
	trackCreated "$CREATED_ID"
	RESULT_MESSAGE="created id $CREATED_ID"
}

testReadBack()
{
	requireCreated || return
	requireReader || return
	http GET "/restaurants/$CREATED_ID" -H "Authorization: Bearer $READER_TOKEN"
	expectStatus 200 || return
	# Every leaf of what was sent must come back unchanged
	local differences
	differences=$(jq -n -r --argjson sent "$CREATED_JSON" --argjson got "$HTTP_BODY" \
		'[$sent | paths(scalars) as $p | select(($sent | getpath($p)) != ($got | getpath($p))) | $p | map(tostring) | join(".")] | join(", ")')
	if [ -n "$differences" ]; then
		RESULT_MESSAGE="fields not stored as sent: $differences"
		return 1
	fi
}

testInList()
{
	requireCreated || return
	requireReader || return
	if ! listContains "$CREATED_ID"; then
		RESULT_MESSAGE="GET /restaurants (HTTP $HTTP_STATUS) does not contain id $CREATED_ID"
		return 1
	fi
}

testNoOverwrite()
{
	requireCreated || return
	http POST /restaurants -H "Authorization: Bearer $WRITER_TOKEN" -H "Content-Type: application/json" \
		-d "$(restaurantJson "$CREATED_NAME-copy" "$CREATED_ID")"
	expectStatus 200 || return
	local copy_id
	copy_id=$(echo "$HTTP_BODY" | jq -r '.id // empty')
	if [ "$copy_id" == "$CREATED_ID" ]; then
		RESULT_MESSAGE="the POST reused id $CREATED_ID: the original document was overwritten"
		return 1
	fi
	trackCreated "$copy_id"
	http GET "/restaurants/$CREATED_ID" -H "Authorization: Bearer $WRITER_TOKEN"
	expectStatus 200 && expectJson .name "$CREATED_NAME" || return
	http DELETE "/restaurants/$copy_id" -H "Authorization: Bearer $WRITER_TOKEN"
	if [ "$HTTP_STATUS" == "200" ]; then
		untrackCreated "$copy_id"
	fi
	expectStatus 200
}

testInvalidNotStored()
{
	requireWriter || return
	requireReader || return
	local name="$CREATED_NAME-invalid"
	http POST /restaurants -H "Authorization: Bearer $WRITER_TOKEN" -H "Content-Type: application/json" \
		-d "$(restaurantJson "$name" "" "javascript:alert(1)")"
	if [ "$HTTP_STATUS" == "200" ]; then
		trackCreated "$(echo "$HTTP_BODY" | jq -r '.id // empty')"
	fi
	expectStatus 400 && expectJson .error validation_failed || return
	http GET /restaurants -H "Authorization: Bearer $READER_TOKEN"
	if [ "$(echo "$HTTP_BODY" | jq --arg n "$name" '[.[] | select(.name == $n)] | length' 2>/dev/null)" != "0" ]; then
		RESULT_MESSAGE="a document named $name was stored despite the 400"
		return 1
	fi
}

testReaderCannotDelete()
{
	requireCreated || return
	requireReader || return
	http DELETE "/restaurants/$CREATED_ID" -H "Authorization: Bearer $READER_TOKEN"
	expectStatus 403 || return
	if ! listContains "$CREATED_ID"; then
		RESULT_MESSAGE="403 returned but the restaurant is gone"
		return 1
	fi
}

testWriterDelete()
{
	requireCreated || return
	http DELETE "/restaurants/$CREATED_ID" -H "Authorization: Bearer $WRITER_TOKEN"
	expectStatus 200 || return
	local deleted=$CREATED_ID
	untrackCreated "$deleted"
	CREATED_ID=
	http GET "/restaurants/$deleted" -H "Authorization: Bearer $WRITER_TOKEN"
	expectStatus 404 || return
	if [ -n "$READER_TOKEN" ] && listContains "$deleted"; then
		RESULT_MESSAGE="id $deleted is still in GET /restaurants"
		return 1
	fi
}

testUnknownId()
{
	requireReader || return
	http GET /restaurants/e2e-no-such-id -H "Authorization: Bearer $READER_TOKEN"
	expectStatus 404 && expectJson .error not_found
}

testDeleteUnknown()
{
	requireWriter || return
	http DELETE /restaurants/e2e-no-such-id -H "Authorization: Bearer $WRITER_TOKEN"
	expectStatus 404 && expectJson .error not_found
}

testCorsAllowed()
{
	http OPTIONS /restaurants -H "Origin: $WF_ALLOWED_ORIGIN" -H "Access-Control-Request-Method: GET"
	expectStatus 200 || return
	if ! echo "$HTTP_HEADERS" | tr -d '\r' | grep -qi "^access-control-allow-origin: $WF_ALLOWED_ORIGIN$"; then
		RESULT_MESSAGE="Access-Control-Allow-Origin is not $WF_ALLOWED_ORIGIN"
		return 1
	fi
}

testCorsRejected()
{
	http OPTIONS /restaurants -H "Origin: https://evil.example" -H "Access-Control-Request-Method: GET"
	expectStatus 403
}

testSwaggerNotPublic()
{
	http GET /v3/api-docs
	expectStatus 401
}

testSwaggerDocsPublic()
{
	http GET /v3/api-docs
	expectStatus 200 && expectJson .info.title "Windfire Restaurants API"
}

testSwaggerUiPublic()
{
	http GET /swagger-ui/index.html
	expectStatus 200
}
####################################
###### Function section - END ######
####################################

# ##############################################
# #################### MAIN ####################
# ##############################################
main
