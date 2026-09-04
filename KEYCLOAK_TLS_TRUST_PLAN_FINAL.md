# Extend Keycloak TLS trust plan: automate step 1 with a script, update README

## Context

`KEYCLOAK_TLS_TRUST_PLAN.md` already lays out the fix for the `PKIX path building
failed` error the backend hits now that Keycloak is HTTPS-only: regenerate the
Keycloak client truststore by seeding it from the JDK's own `cacerts` (so the
existing MongoDB Atlas TLS trust survives) and importing Keycloak's self-signed CA,
then wire that truststore into the JVM via `-Djavax.net.ssl.trustStore` /
`-Djavax.net.ssl.trustStorePassword` in `run-windfire-restaurants-backend.sh`.

Section "1. Regenerate the Keycloak client truststore" currently documents that as a
block of commands to run by hand — a one-off, not repeatable, and not discoverable by
anyone reading the repo. Every other crypto-setup step in this project (the Windfire
Root CA, the backend's own TLS cert) already has a dedicated script under `ssl/`
(`ssl/generate-ssl-cert.sh`) and a README section explaining it. This change brings
step 1 in line with that pattern: turn it into `ssl/generate-keycloak-truststore.sh`,
matching that script's exact conventions, and document every change (new script,
`setenv.sh` addition, `run-windfire-restaurants-backend.sh` prompt/flags) in the
README the same way the existing TLS setup is documented — so a future reader can
regenerate the truststore the same way twice, and doesn't have to reverse-engineer it
from a Java stack trace again.

## Changes

### A. `KEYCLOAK_TLS_TRUST_PLAN.md` — point section 1 at the new script

Replace the body of "### 1. Regenerate the Keycloak client truststore" (currently
`KEYCLOAK_TLS_TRUST_PLAN.md:40-94`) — keeping the heading and the rationale paragraphs
about JVM-wide trust and seeding from `cacerts` instead of an empty keystore
(`KEYCLOAK_TLS_TRUST_PLAN.md:42-54`), but replacing the raw command block
(`:56-72`), the `changeit`/PKCS12 explanatory paragraphs (`:74-81`), and the manual
sanity-check block (`:83-89`) — with this text, verbatim:

```markdown
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
```

The rest of the section (Requirement 3 / inbound-TLS callout, currently
`KEYCLOAK_TLS_TRUST_PLAN.md:91-94`) is unchanged.

Also update the "What's still missing" bullet list earlier in the doc
(`KEYCLOAK_TLS_TRUST_PLAN.md:24-30`) and the "## Verification" section
(`:117-125`) only as needed so they stay consistent with "now automated by a script"
rather than "run these commands by hand" — e.g. verification step 1 there already just
`curl`s with `--cacert` and needs no change, but should gain a preceding step that
runs the new script (mirroring change-set verification step 1 below).

### B. New script: `ssl/generate-keycloak-truststore.sh`

New file, `755`, following `ssl/generate-ssl-cert.sh`'s exact conventions verified by
reading that file (`ssl/generate-ssl-cert.sh:1-246`) and `setenv.sh`
(`setenv.sh:1-67`):
- No shebang; line 1 is `source ../setenv.sh`.
- No `set -e`; every command that can fail is checked explicitly with
  `cmd || { echo ${red}Error: ...${end} >&2; exit 1; }`, matching
  `ssl/generate-ssl-cert.sh:76,151,167`.
- `SCRIPT=` var, banner-commented "Variable section" / "Function section" blocks,
  trailing `# #### MAIN ####` banner calling functions in sequence (this script has no
  `main()` wrapper, matching `ssl/generate-ssl-cert.sh`'s flat style, not
  `run-windfire-restaurants-backend.sh`'s).
- Colour vars (`${blu}`/`${grn}`/`${cyn}`/`${red}`/`${yel}`/`${end}`) from `setenv.sh`
  for section banners, command echoes, and errors-to-stderr.

Functions, in call order:
1. `checkPreRequisites()` — verify `java` and `keytool` are on `PATH`; verify
   `$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_CACERT_PEM` exists (error out
   with guidance if not — this script only rebuilds the JKS, it doesn't create the CA
   PEM); `mkdir -p "$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR"`.
2. `detectJavaCacerts()` — resolve `JAVA_HOME_DETECTED` via
   `java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java\.home/ {print $2}' | xargs`
   (portable across macOS dev machine and Linux/Raspberry Pi target, no
   `java_home`/`readlink -f` dependency, exactly as justified in the existing plan
   doc), derive `JAVA_CACERTS=$JAVA_HOME_DETECTED/lib/security/cacerts`, error out if
   missing.
3. `rebuildTruststore()` — warn (yellow) if a truststore already exists and will be
   replaced; `rm -f` + `cp "$JAVA_CACERTS" "$KEYCLOAK_TRUSTSTORE_PATH"` +
   `chmod u+w`; then
   `keytool -importcert -trustcacerts -noprompt -alias ca_keycloak -file "$KEYCLOAK_CACERT_PEM_PATH" -keystore "$KEYCLOAK_TRUSTSTORE_PATH" -storepass $DEFAULT_KEYCLOAK_TRUSTSTORE_PWD`
   (using the new `setenv.sh` variable from change C instead of a hardcoded
   `changeit`, for consistency with the run script).
4. `verifyTruststore()` — `keytool -list -keystore ... | grep -Ei "ca_keycloak|digicert|amazon|isrg"`,
   exit non-zero with an error if nothing matched.
5. `printSummary()` — print the resulting truststore path/password and a one-line
   reminder that it's JVM-wide (trusts Keycloak's CA *in addition to*, not instead of,
   the public CAs it was seeded from).

Each command is built as a `CMD_RUN="..."` string, echoed, then run with
`eval $CMD_RUN`, matching `ssl/generate-ssl-cert.sh:148-151`'s pattern.

### C. `setenv.sh` — add the password default

Right after `setenv.sh:64` (`DEFAULT_KEYCLOAK_TRUSTSTORE=keycloak.truststore.jks`),
before the closing "Variable section - END" banner:
```bash
DEFAULT_KEYCLOAK_TRUSTSTORE_PWD=changeit
```

### D. `run-windfire-restaurants-backend.sh` — wire the truststore into the JVM invocation

Exact current content read at `run-windfire-restaurants-backend.sh:1-47`. Changes:
- Add `inputKeycloakTruststorePassword()`, matching the `inputKeycloakServerPort()`
  pattern exactly (echo prompt with default shown in `${mag}`, plain `read`, blank →
  default):
  ```bash
  inputKeycloakTruststorePassword()
  {
  	echo ${cyn}Input Keycloak client truststore password - leaving blank will set truststore password to ${end}${mag}$DEFAULT_KEYCLOAK_TRUSTSTORE_PWD : ${end}
  	read KEYCLOAK_TRUSTSTORE_PWD
  	if [ "$KEYCLOAK_TRUSTSTORE_PWD" == "" ]; then
  		KEYCLOAK_TRUSTSTORE_PWD=$DEFAULT_KEYCLOAK_TRUSTSTORE_PWD
      fi
  }
  ```
- In `main()`, call it after `inputKeycloakServerPort` and export the result:
  ```bash
  inputKeycloakTruststorePassword
  export KEYCLOAK_TRUSTSTORE_PWD=$KEYCLOAK_TRUSTSTORE_PWD
  ```
- Update the `java -jar` line (currently `run-windfire-restaurants-backend.sh:28`) to:
  ```bash
  java -Djavax.net.ssl.trustStore=$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_TRUSTSTORE \
       -Djavax.net.ssl.trustStorePassword=$KEYCLOAK_TRUSTSTORE_PWD \
       -jar target/$JAR_NAME-$VERSION.jar &
  ```

### E. `README.md` — document all of the above

Exact current structure read at `README.md:1-226`.
- **Table of Contents** (`README.md:3-10`): add a new bullet under "How the
  application works", after "TLS configuration" and before "Test the application":
  `- [Keycloak client trust (outbound TLS)](#keycloak-client-trust-outbound-tls)`.
- **New subsection**, inserted after the existing "TLS configuration" section ends
  (after `README.md:220`, before `### Test the application` at `README.md:222`):
  `### Keycloak client trust (outbound TLS)`, explaining:
  - why this is needed (Keycloak now HTTPS-only with a self-signed cert; outbound
    JWKS/OIDC metadata fetch needs to trust it too, separate from the inbound TLS
    described just above) and that it's JVM-wide via
    `-Djavax.net.ssl.trustStore`/`trustStorePassword`, not a Spring property.
  - the new **[generate-keycloak-truststore.sh](ssl/generate-keycloak-truststore.sh)**
    script: what it expects as input (the CA PEM already at
    `$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_CACERT_PEM`), what it does
    (seed from the running JDK's `cacerts` so existing public-CA trust — e.g. for
    MongoDB Atlas — isn't lost, then import Keycloak's CA), and how to run it
    (`cd ssl && ./generate-keycloak-truststore.sh`), noting it's a one-time/local
    setup step whose *output* isn't tracked in git (only the script is).
  - that **[run-windfire-restaurants-backend.sh](run-windfire-restaurants-backend.sh)**
    now also prompts for the truststore password (default from
    `DEFAULT_KEYCLOAK_TRUSTSTORE_PWD` in `setenv.sh`) and passes it to the JVM.
- **Run** section (`README.md:31-38`): add one sentence after the existing
  `run-windfire-restaurants-backend.sh` mention, pointing to the new subsection for
  the one-time truststore setup it now depends on.

No other README sections need changes — "Implementing OAuth2 Authentication with
Keycloak" and "Test the application" are unaffected.

## Verification

1. `cd ssl && ./generate-keycloak-truststore.sh` — confirm it completes with the
   `ca_keycloak` alias and at least one public CA alias both listed by the built-in
   verification step, and re-run it a second time to confirm it's idempotent/repeatable
   (no leftover-file errors, truststore rebuilt cleanly).
2. `curl --cacert "$HOME/opt/keycloak/ssl/truststore/keycloak.truststore.pem" https://raspberry01:8443/realms/windfire/.well-known/openid-configuration` — confirms the CA cert itself is valid, independent of the script/Java changes.
3. Run `./run-windfire-restaurants-backend.sh`, accept the new truststore password prompt (default), confirm the app starts with no `SSLHandshakeException`/`JwtDecoderInitializationException` in the log.
4. Call `https://localhost:8081/restaurants` with a valid Bearer token and confirm a real data response — exercises both the Keycloak trust path and the still-working MongoDB Atlas TLS trust (proving the `cacerts`-seeding approach didn't regress it).
5. Read through the edited `README.md` TOC and new subsection rendering to confirm links/anchors resolve and content matches the actual script/vars.
