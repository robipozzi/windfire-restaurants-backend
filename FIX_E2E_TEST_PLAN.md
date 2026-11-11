# Fix live e2e login failures: "Client not allowed for direct access grants"

## Context
All 119 JUnit tests pass. The only failures come from the live e2e tests, and they share one cause:

- [test/e2e-tests.sh](test/e2e-tests.sh) `keycloakToken()` (~line 352) gets user tokens with the **Resource Owner Password grant** (`grant_type=password`) on client `WF_CLIENT_ID`, which defaults to `windfire`.
- `windfire` is the React SPA's client. Commit `68be12a` in windfire-ui-react ("switch UI to Authorization code + PKCE") moved it to Authorization Code + PKCE, and its README now specifies **Direct access grants = Off**. That setting is applied in Keycloak.
- So Keycloak refuses every password login on that client with `unauthorized_client`. This makes 4 tests fail (wrong-password, reader, writer and no-role logins), and the 14 tests that need a token are skipped.

The Keycloak setting is correct for the SPA. The test suite is now out of date. Turning direct access grants back on for `windfire` would allow password login on the public browser client again, which the PKCE change was meant to remove. **Remediation: give the tests their own Keycloak client and leave `windfire` as it is.**

## Step 1: Keycloak (manual, admin console, realm `windfire`)
Create client **`windfire-e2e`**:
| Setting | Value |
|---|---|
| Client type | OpenID Connect |
| Client authentication | **On** (confidential: a secret is needed, so knowing the client id is not enough to use the password grant) |
| Standard flow / Implicit / Service accounts / Device | Off |
| Direct access grants | **On** (only this client) |
| Redirect URIs / Web origins | empty |

- **Audience**: add the same mapper `windfire` uses to put `windfire-restaurants-api` in `aud`. That is a *Client scopes → windfire-e2e-dedicated → Add mapper → Audience* mapper with Included Client Audience = `windfire-restaurants-api` and Add to access token = On. If `windfire` gets the audience from a shared client scope, assign that scope instead. Without it, `checkClaims` fails with "aud lacks windfire-restaurants-api".
- Client roles need nothing new: `resource_access.windfire-restaurants-api.roles` comes from the user's role mappings. Check that the access token includes the client roles. The default `roles` scope does this, and it is on by default for new clients.
- Copy the secret from the *Credentials* tab.

## Step 2: Code changes in windfire-restaurants-backend
1. **test/e2e-tests.sh**
   - Change the default to `WF_CLIENT_ID=${WF_CLIENT_ID:-windfire-e2e}` and add `WF_CLIENT_SECRET` to the header doc block. Note there that `windfire` (the SPA, PKCE) has no direct access grants.
   - `inputPasswords()`: ask for the client secret first (`read -s`), the same way the passwords are asked for, if it is unset and a terminal is attached.
   - `keycloakToken()`: add `--data-urlencode "client_secret=$WF_CLIENT_SECRET"` when it is set.
   - Clearer diagnostics: in `login()` and `testWrongPassword()`, when `.error == "unauthorized_client"`, say so directly: *"client $WF_CLIENT_ID does not allow direct access grants: use the dedicated test client (see README)"*. For `invalid_client`, the message points to a wrong or missing `WF_CLIENT_SECRET`.
   - Change the banner line (~112) only if needed. It already prints the login client.
2. **run-tests.sh**: in the help text (~line 87–91), list `WF_CLIENT_SECRET` (secret of the `windfire-e2e` Keycloak client, prompted if unset).
3. **README.md**, e2e section (~lines 483–500): document the `windfire-e2e` client (settings table above + audience mapper), why it is separate from `windfire`, and `WF_CLIENT_SECRET`.

Never store the secret in the repo. It is only prompted for, or read from the environment, like the user passwords.

## Verification
1. Check the client quickly before running the suite:
   `curl -s --cacert ~/opt/keycloak/ssl/truststore/WindfireKeycloakRootCA.crt -d client_id=windfire-e2e -d client_secret=… -d grant_type=password -d username=windfire-reader -d password=… https://raspberry01:8443/realms/windfire/protocol/openid-connect/token | jq -r .access_token | cut -d. -f2 | base64 -d` → `aud` contains `windfire-restaurants-api` and `resource_access` contains `restaurants-reader`.
2. `./run-tests.sh` → option 1 (dev), enter the client secret and the passwords. Expected result: 145 total, 0 fail, 0 skip.
3. Regression check: `WF_CLIENT_ID=windfire ./test/e2e-tests.sh` should now fail with the new explicit "does not allow direct access grants" message.
4. Optional: run option 3 (prod) as well. It uses the same Keycloak and the same new client.
