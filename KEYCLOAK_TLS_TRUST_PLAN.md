# Fix Keycloak TLS trust for windfire-restaurants-backend

## Context

Keycloak on `raspberry01` was switched to HTTPS-only (`--http-enabled=false`), and now serves
`https://raspberry01:8443` with a self-signed certificate. The Spring Boot resource server
(`windfire-restaurants-backend`) validates incoming JWTs by fetching Keycloak's OIDC metadata
from `spring.security.oauth2.resourceserver.jwt.issuer-uri`, which already points at
`https://${KEYCLOAK_SERVER}:${KEYCLOAK_SERVER_PORT}/realms/windfire` — but the JVM that runs the
backend has no trust store containing Keycloak's self-signed CA, so that HTTPS call fails with
`PKIX path building failed: unable to find valid certification path to requested target`, wrapped
by Spring into `JwtDecoderInitializationException` on every authenticated request.

A prior planning doc (`/Users/robertopozzi/dev/windfire-ui-react/KEYCLOAK_HTTPS_PLAN.md`) already
called this out and most of the groundwork is already in place:
- `application.properties` issuer-uri is already `https://...` ✅
- `setenv.sh` already has `DEFAULT_KEYCLOAK_PORT=8443` and the (currently unused)
  `DEFAULT_KEYCLOAK_TRUSTSTORE_DIR` / `DEFAULT_KEYCLOAK_CACERT_PEM` / `DEFAULT_KEYCLOAK_TRUSTSTORE`
  variables ✅
- Keycloak's CA cert already exists locally at
  `$HOME/opt/keycloak/ssl/truststore/keycloak.truststore.pem` (verified: self-signed, `CN=raspberry01`,
  valid through 2036; identical to the canonical copy in `windfire-security`) ✅

What's still missing — and what actually causes the error:
1. A **JKS truststore** built from that CA cert. One exists at
   `$HOME/opt/keycloak/ssl/truststore/keycloak.truststore.jks` but its password is unknown/lost
   (neither blank nor `changeit` opens it), so it needs to be regenerated.
2. `run-windfire-restaurants-backend.sh` never passes `-Djavax.net.ssl.trustStore` /
   `-Djavax.net.ssl.trustStorePassword` to the `java -jar` invocation, so the JVM falls back to its
   default cacerts, which doesn't know about Keycloak's CA.

No application Java code needs to change — there's no custom `RestTemplate`/`JwtDecoder`/`SSLContext`
bean in `SecurityConfig.java`; Spring's default `NimbusJwtDecoder` uses the plain JDK HTTPS stack, so
trust must be supplied at the JVM level via system properties, matching the approach the prior plan
doc already recommended (and the same pattern already used for the backend's own inbound TLS via
`server.ssl.*` properties).

## Changes

### 1. Regenerate the Keycloak client truststore (one-time local setup, not tracked in git)

Delete the existing unreadable JKS and rebuild it from the already-present CA PEM, with a known
password:

```bash
rm -f "$HOME/opt/keycloak/ssl/truststore/keycloak.truststore.jks"
keytool -importcert -trustcacerts -noprompt \
  -alias ca_keycloak \
  -file "$HOME/opt/keycloak/ssl/truststore/keycloak.truststore.pem" \
  -keystore "$HOME/opt/keycloak/ssl/truststore/keycloak.truststore.jks" \
  -storepass changeit
```

(`changeit` is the conventional Java truststore default; this file holds only a public CA
certificate, so it doesn't need a strong secret — it's used purely so the JVM will trust
Keycloak's HTTPS endpoint.)

### 2. `setenv.sh` — add a default password variable

Next to the existing `DEFAULT_KEYCLOAK_TRUSTSTORE` line, add:

```bash
DEFAULT_KEYCLOAK_TRUSTSTORE_PWD=changeit
```

### 3. `run-windfire-restaurants-backend.sh` — wire the truststore into the JVM invocation

- Add a new `inputKeycloakTruststorePassword()` function, following the same `read -p "... [$DEFAULT]: "`
  pattern already used by `inputKeycloakServer()` / `inputKeycloakServerPort()` (default-on-blank,
  not a hidden/required secret like the MongoDB password, since this isn't protecting a private key).
- Call it from `main()`, and export `KEYCLOAK_TRUSTSTORE_PWD`.
- Update the `java -jar` invocation to:
  ```bash
  java -Djavax.net.ssl.trustStore=$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_TRUSTSTORE \
       -Djavax.net.ssl.trustStorePassword=$KEYCLOAK_TRUSTSTORE_PWD \
       -jar target/$JAR_NAME-$VERSION.jar &
  ```

## Verification

1. `curl --cacert "$HOME/opt/keycloak/ssl/truststore/keycloak.truststore.pem" https://raspberry01:8443/realms/windfire/.well-known/openid-configuration` — confirms the CA cert itself is valid and the chain resolves, independent of the Java changes.
2. Run `./run-windfire-restaurants-backend.sh`, accept the new truststore password prompt (default), and confirm the app starts with no `SSLHandshakeException`/`JwtDecoderInitializationException` in the log.
3. Call `https://localhost:8081/restaurants` with a valid Bearer token and confirm it now returns data (not the 500 with the PKIX stack trace).
