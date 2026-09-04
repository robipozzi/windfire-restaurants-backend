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
   (neither blank nor `changeit` opens it), so it needs to be regenerated — now automated by
   `ssl/generate-keycloak-truststore.sh` (see below).
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

**Important:** this truststore is wired in via `-Djavax.net.ssl.trustStore`, which is JVM-wide —
it doesn't just cover Keycloak's HTTPS calls, it replaces the *default* trust store for every TLS
client in the process, including the outbound MongoDB Atlas connection
(`spring.data.mongodb.uri=mongodb+srv://...@windfire.rxdd4.mongodb.net/restaurants`), whose
certificate is signed by a public CA that lives in the JDK's default `cacerts`, not by Keycloak's
CA. There's no custom `SSLContext`/`MongoClientSettings` bean anywhere in this codebase, so Mongo's
TLS trust today comes entirely from that default `cacerts`. Building the new truststore from an
empty keystore containing *only* the Keycloak CA (as a naive rebuild would) would fix the Keycloak
PKIX error while breaking Mongo's with the exact same error.

So instead of starting from an empty keystore, seed the new truststore from the running JDK's own
default `cacerts` (which already trusts the public CAs, including whichever signed the Atlas
cert), then add Keycloak's CA into that copy.

This is now automated by **[ssl/generate-keycloak-truststore.sh](ssl/generate-keycloak-truststore.sh)**,
which:
1. Locates the default `cacerts` of whichever JDK is on the `PATH` (works identically on a Mac
   dev machine and a Linux/Raspberry Pi deployment target — no dependency on macOS-only
   `/usr/libexec/java_home` or GNU-only `readlink -f`).
2. Copies it to `$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_TRUSTSTORE`, replacing any
   existing truststore there.
3. Imports Keycloak's CA cert (`$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_CACERT_PEM`)
   into it via `keytool -importcert`, under alias `ca_keycloak`, using the password from the new
   `DEFAULT_KEYCLOAK_TRUSTSTORE_PWD` variable in `setenv.sh` (`changeit` by default — the
   conventional Java truststore default, and already the password of the JDK's own `cacerts` this
   script copies from, so this doesn't introduce a new secret).
4. Verifies the merge worked: both a known public CA and the new `ca_keycloak` alias must be
   listed by `keytool -list` on the resulting file.

Since JDK 9+, `cacerts` ships in PKCS12 format (not JKS); copying it forward preserves that format,
which matches the JVM's own default `javax.net.ssl.trustStoreType` on JDK 17 (this project's
target per `pom.xml`), so no extra `-Djavax.net.ssl.trustStoreType` flag is needed at runtime
despite the file keeping its existing `keycloak.truststore.jks` name.

Run it once (and again whenever Keycloak's CA certificate changes) with:
```bash
cd ssl
./generate-keycloak-truststore.sh
```

This is a one-time/local setup step whose *output* (the `.jks` file) is not tracked in git — only
the script that produces it lives in the repository. See the
[README](README.md#keycloak-client-trust-outbound-tls) for a user-facing description.

Requirement 3 (the app's own inbound TLS via `windfire-restaurants-backend.crt`, signed by the
separate Windfire Root CA) needs no changes here — it's wired in via
`server.ssl.certificate`/`server.ssl.certificate-private-key` (PEM keystore config), a completely
different mechanism from this JVM-wide client-trust property.

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

1. `cd ssl && ./generate-keycloak-truststore.sh` — confirm it completes with the `ca_keycloak`
   alias and at least one public CA alias both listed by the built-in verification step.
2. `curl --cacert "$HOME/opt/keycloak/ssl/truststore/keycloak.truststore.pem" https://raspberry01:8443/realms/windfire/.well-known/openid-configuration` — confirms the CA cert itself is valid and the chain resolves, independent of the Java changes.
3. Run `./run-windfire-restaurants-backend.sh`, accept the new truststore password prompt (default), and confirm the app starts with no `SSLHandshakeException`/`JwtDecoderInitializationException` in the log.
4. Call `https://localhost:8081/restaurants` with a valid Bearer token and confirm it now returns data (not the 500 with the PKIX stack trace). This step exercises **both** trust paths, not just Keycloak: the token validation needs Keycloak's CA, and `/restaurants` itself reads from MongoDB Atlas, so a real data response also proves the merged truststore still trusts Mongo's TLS handshake.

If step 4 fails, check the exception's target host in the stack trace (`raspberry01:8443` vs
`windfire.rxdd4.mongodb.net`) to tell a remaining Keycloak-trust gap apart from a Mongo-trust
regression from the merge.
