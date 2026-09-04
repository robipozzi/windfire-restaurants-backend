# Fully automating build/config/deploy of windfire-restaurants-backend on Raspberry Pi

## Context

This repo already automates *build* (`build.sh`) and most of *deployment* (`deploy.sh` →
`deployment/raspberry/windfire-restaurants.yaml`), but the chain stops short of actually running the
service: today, after `deploy.sh` finishes, the operator still has to SSH into `raspberry02` and run
`run-windfire-restaurants-backend.sh` by hand, answering four interactive prompts (Keycloak host, Keycloak
port, Keycloak truststore password, MongoDB password) before the JVM even starts. On top of that, the
Keycloak client truststore the JVM needs (see `KEYCLOAK_TLS_TRUST_PLAN.md`) is only ever built *locally*
and is never shipped to the Pi, so a from-scratch deploy would fail on first authenticated request even
after a manual start. There's also a stale bug in the uninstall playbook (wrong host group) found while
reviewing.

Compared with sibling components in the same suite (`windfire-calendar`, `windfire-security`,
`windfire-restaurants-node`), which all have their Ansible playbook start the service itself (pm2 for
Node, a shell/pgrep dance for the Python services), this playbook is the odd one out in leaving "start the
app" as a manual step. The goal of this document is to close that gap so `./deploy.sh` alone takes the app
from source to a running, supervised service on the Pi, with no follow-up SSH session required.

Decisions made:
- **Process supervision**: a systemd service (auto-restarts on crash/reboot, file logging to match this
  app's conventions), templated and enabled by Ansible — not a bare backgrounded process.
- **Secrets delivery**: `deploy.sh` prompts once (reusing the existing prompt style) for the Keycloak
  truststore password and MongoDB password, exports them as environment variables, and the playbook picks
  them up via `lookup('env', ...)`. No Ansible Vault — stays consistent with the rest of the suite, which
  uses none.

## Findings

1. **Bug** — `deployment/raspberry/windfire-restaurants-uninstall.yaml:3` targets
   `hosts: raspberry_identitymanager`, which doesn't exist in the inventory (`/etc/ansible/hosts` defines
   `identitymanager`, not `raspberry_identitymanager`) and doesn't match the app's actual host group
   (`appserver` = `raspberry02`, per `deployment/raspberry/windfire-restaurants.yaml:3`). The uninstall
   playbook currently runs against zero hosts.
2. **Gap** — `windfire-restaurants.yaml` stops the old process and copies the jar/scripts/certs, but has
   no task that starts the app. `run-windfire-restaurants-backend.sh` is the only thing that starts it, and
   it always calls `read` for four values — unusable non-interactively as-is.
3. **Gap** — The Keycloak client truststore (`$HOME/opt/keycloak/ssl/truststore/keycloak.truststore.jks`,
   built by `ssl/generate-keycloak-truststore.sh`, see `KEYCLOAK_TLS_TRUST_PLAN.md`) is built and consumed
   only on the local dev machine. Nothing copies it — or the Keycloak CA it's built from — to `raspberry02`,
   so a JVM started there has no way to validate Keycloak's TLS cert.
4. **Minor** — `deploy.sh` always prompts for the platform even though there's only ever been one option;
   sibling `deploy.sh` scripts (`windfire-maps`, `windfire-restaurants-node`) accept it as `$1` and only
   prompt when omitted.

## Design

### 1. `ssl/generate-keycloak-truststore.sh` reused, `deploy.sh` gains a pre-flight check for it
Mirror the existing `checkSSLCertificates()` pattern: add `checkKeycloakTruststore()` to `deploy.sh` that
runs `ssl/generate-keycloak-truststore.sh` if
`$DEFAULT_KEYCLOAK_TRUSTSTORE_DIR/$DEFAULT_KEYCLOAK_TRUSTSTORE` doesn't exist locally yet, so a fresh
checkout can produce it unattended (the script itself is already non-interactive and idempotent — no
changes needed there). Call it from `deployRaspberry()` alongside `checkSSLCertificates`.

### 2. `deploy.sh` collects the remaining secrets once, non-interactively hands them to Ansible
Add prompts (same style as `run-windfire-restaurants-backend.sh`'s `input*` functions, defaults from
`setenv.sh`) for Keycloak host, Keycloak port, Keycloak truststore password, and MongoDB password, then
`export` them as `KEYCLOAK_SERVER`, `KEYCLOAK_SERVER_PORT`, `KEYCLOAK_TRUSTSTORE_PWD`, `MONGODB_PASSWORD`
before invoking `ansible-playbook`. Also accept `$1` for platform selection, matching the sibling scripts'
convention (finding 4).

### 3. `deployment/raspberry/conf/config.yml` gains the truststore paths
Add, following the existing `local_windfirerestaurants_certs_dir` / `remote_windfirerestaurants_certs_dir`
pattern:
```yaml
local_keycloak_truststore_dir: "/Users/robertopozzi/opt/keycloak/ssl/truststore"
remote_keycloak_truststore_dir: "{{ remote_home_dir }}/opt/keycloak/ssl/truststore"
keycloak_truststore_file: "keycloak.truststore.jks"
```

### 4. `deployment/raspberry/windfire-restaurants.yaml` — copy the truststore, template + start a systemd unit
- Add a copy task for `{{ keycloak_truststore_file }}` from `local_keycloak_truststore_dir` to
  `remote_keycloak_truststore_dir`, mirroring the existing certificate/key copy tasks (own `file` task to
  ensure the remote dir exists first).
- Replace the current `pkill -f` stop task with `ansible.builtin.systemd: name=windfire-restaurants-backend
  state=stopped`, guarded with `failed_when`/`ignore_errors` for the first-ever deploy where the unit
  doesn't exist yet (same reasoning as the graceful-stop pattern already used in
  `windfire-security`/`windfire-calendar`'s playbooks, just via the `systemd` module since this is now a
  real unit instead of a bare process).
- After the existing copy tasks, add a `template` task rendering
  `templates/windfire-restaurants-backend.env.j2` → `{{ remote_windfirerestaurants_dir }}/windfire-restaurants-backend.env`
  (mode `0600`, owner `{{ user }}`) containing `KEYCLOAK_SERVER`, `KEYCLOAK_SERVER_PORT`,
  `HOME_DIR={{ remote_home_dir }}`, `KEYCLOAK_TRUSTSTORE_PWD`, `MONGODB_PASSWORD` — the vars value coming
  from `"{{ lookup('env', 'KEYCLOAK_SERVER') }}"` etc., set once at play level.
- Add a `template` task rendering `templates/windfire-restaurants-backend.service.j2` →
  `/etc/systemd/system/windfire-restaurants-backend.service`, `notify: reload systemd`. Unit sketch:
  ```ini
  [Unit]
  Description=Windfire Restaurants Backend
  After=network.target

  [Service]
  Type=simple
  User={{ user }}
  WorkingDirectory={{ remote_windfirerestaurants_dir }}
  EnvironmentFile={{ remote_windfirerestaurants_dir }}/windfire-restaurants-backend.env
  ExecStart=/usr/bin/java -Djavax.net.ssl.trustStore={{ remote_keycloak_truststore_dir }}/{{ keycloak_truststore_file }} -Djavax.net.ssl.trustStorePassword=${KEYCLOAK_TRUSTSTORE_PWD} -jar {{ remote_windfirerestaurants_run_target_dir }}/{{ windfirerestaurants_app_jar }}
  Restart=on-failure
  RestartSec=5
  StandardOutput=append:{{ remote_windfirerestaurants_dir }}/logs/windfire-restaurants-backend.log
  StandardError=append:{{ remote_windfirerestaurants_dir }}/logs/windfire-restaurants-backend.log

  [Install]
  WantedBy=multi-user.target
  ```
  (`${KEYCLOAK_TRUSTSTORE_PWD}` is systemd's own native expansion of a variable supplied via
  `EnvironmentFile=`, not shell expansion — no `bash -c` wrapper needed. The other three env vars reach
  Spring directly through `application.properties`' `${KEYCLOAK_SERVER}` / `${MONGODB_PASSWORD}` /
  `${HOME_DIR}` placeholders, exactly as they do today when `run-windfire-restaurants-backend.sh` exports
  them.)
- Ensure `{{ remote_windfirerestaurants_dir }}/logs` exists (`file: state=directory`) before the unit
  starts.
- `systemd: daemon_reload=yes` then `systemd: name=windfire-restaurants-backend state=started
  enabled=yes` as the final task.
- Add a verification task: `wait_for: port=8081 host=127.0.0.1 timeout=30` (matching
  `server.port=8081` in `application.properties`) so the play fails loudly if the JVM didn't come up,
  instead of silently reporting success. (An authenticated HTTP health check isn't available —
  `SecurityConfig.java` requires a bearer token on every path except Swagger — so a TCP-level check is the
  right proxy here.)
- Leave the existing copy of `run-windfire-restaurants-backend.sh`/`setenv.sh` to the Pi as-is, for manual
  troubleshooting; they're no longer what starts the service but remain useful for an operator debugging
  on-box.

### 5. `deployment/raspberry/windfire-restaurants-uninstall.yaml`
- Fix `hosts:` to `appserver` (finding 1).
- Stop+disable the systemd unit (`systemd: name=windfire-restaurants-backend state=stopped enabled=no`,
  `ignore_errors: yes` for the case it was never deployed) instead of `pkill`, then remove the unit file and
  `daemon_reload`, before the existing directory removal.

## Files to change
- `deploy.sh` — `checkKeycloakTruststore()`, secret prompts, `$1` platform arg.
- `deployment/raspberry/conf/config.yml` — new truststore vars.
- `deployment/raspberry/windfire-restaurants.yaml` — truststore copy, systemd templating/start, verification.
- `deployment/raspberry/windfire-restaurants-uninstall.yaml` — host fix, systemd-aware teardown.
- New: `deployment/raspberry/templates/windfire-restaurants-backend.service.j2`,
  `deployment/raspberry/templates/windfire-restaurants-backend.env.j2`.

No changes needed to `build.sh`, `run-windfire-restaurants-backend.sh`, `stop-windfire-restaurants-backend.sh`,
or `ssl/generate-keycloak-truststore.sh` — all four are already correct/idempotent for their existing
(local/manual) purpose and are reused as-is or left untouched.

## Verification
1. `./deploy.sh 1` from a machine that has never run it before: confirm `checkSSLCertificates` and
   `checkKeycloakTruststore` both generate their artifacts locally without manual intervention beyond the
   new prompts, `ansible-playbook` runs clean, and it ends with the `wait_for` port check passing.
2. On `raspberry02`: `systemctl status windfire-restaurants-backend` shows `active (running)`, and
   `journalctl -u windfire-restaurants-backend` / the app's own log file shows a clean Spring Boot startup
   with no `JwtDecoderInitializationException` (confirms the copied truststore is wired correctly).
3. Re-run `./deploy.sh 1` a second time (redeploy over a running instance): confirm the `systemd` stop task
   doesn't fail, the new jar is picked up, and the service comes back up — this is the main regression risk
   of moving from `pkill` to `systemd`.
4. `curl` (via `test/getRestaurants.sh` + `test/requestKeycloakToken.sh`) against
   `https://raspberry02:8081/restaurants` with a fetched bearer token, from another machine, to confirm the
   full OAuth2 + TLS + MongoDB path works end-to-end post-deploy.
5. Run the fixed uninstall playbook (`ansible-playbook deployment/raspberry/windfire-restaurants-uninstall.yaml`)
   against `raspberry02` and confirm the service is stopped/disabled and the directories are gone.
