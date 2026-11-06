# Plan: Maven vulnerability audit with OSV-Scanner

**Status:** not started. **Do this after** upgrading Spring Boot to a recent version.
Written 2026-11-06. Check the versions and tool behaviour below again before starting, as they will have moved.

## Why
Nothing in CI audits the Maven dependencies:
- `./mvnw -B verify` builds and tests, but looks for no vulnerabilities.
- Dependabot alerts rely on GitHub's dependency graph, which lists only the direct `pom.xml` entries, almost none with versions, and no transitive dependencies. It shows 0 alerts on Spring Boot 3.2.1, which is not evidence that there are no CVEs.
- Dependency review reads the same graph and is unavailable on this private repo (the job in `ci.yml` is skipped).

OSV-Scanner resolves the transitive dependency tree of `pom.xml` itself, so it does not depend on GitHub's graph. It is free and works on private repos.

## Why wait for the upgrade
On Spring Boot 3.2.1 the scan would probably report many findings that the upgrade removes anyway, and triaging them would be wasted work. The latest Spring Boot on 2026-11-06 is 4.1.2. Moving to 4.x is a major upgrade, so Dependabot's minor and patch group will not cover it.

## Prerequisites
- The Spring Boot upgrade is merged to `main`, and `./mvnw -B verify` is green.
- The `.github/workflows/ci.yml` from `fix/f9-dependabot-ci` is merged.

## Steps

### 1. Try it locally first
Install the CLI (OSV-Scanner v2.7.0 was the latest on 2026-11-06) and run:
```bash
osv-scanner scan source -L pom.xml
```
**Check the scan really sees the full tree**, because this decides the approach. OSV-Scanner's docs confirm transitive resolution for `pom.xml` but do not say whether parent POMs are resolved, and this project gets almost all its versions from `spring-boot-starter-parent`.
- Compare the scanned packages with `./mvnw dependency:tree -Dscope=runtime`. Output in JSON (`--format json`) makes this easier.
- Expected packages include `spring-core`, `tomcat-embed-core` and `mongodb-driver-sync`, with the versions Maven resolves.
- If versions differ or packages are missing, go to the fallback in step 2.

Test dependencies are not covered, according to the docs. That is acceptable, because only runtime dependencies ship in the jar.

### 2. Fallback, only if step 1 misses dependencies
Generate an SBOM from Maven's own resolution and scan that:
```bash
./mvnw -B org.cyclonedx:cyclonedx-maven-plugin:<version>:makeAggregateBom
osv-scanner scan source -L target/bom.json
```
The plugin was at 2.9.3 on 2026-11-06. The OSV-Scanner page checked for this plan does not mention CycloneDX, so confirm that SBOM input is supported and which file name it requires (it may need `.cdx.json`).

### 3. Triage the findings
- Fix what a dependency bump can fix, for example through a version property in `pom.xml`.
- For anything that does not apply here, add an entry to `osv-scanner.toml` with a reason and an expiry date, so it gets looked at again. Confirm the format in the docs first.
- Do not add the CI job as blocking until the scan is clean.

### 4. Add the workflow
Create a separate `.github/workflows/osv-scan.yml`, running on `pull_request`, on `push` to `main`, and **weekly on a schedule**. The schedule matters because new CVEs appear without any change to the code.

Requirements:
- `permissions: contents: read` only.
- Use the OSV-Scanner **CLI binary**, not Google's reusable workflows. Those are documented with `security-events: write` and `actions: read` to upload SARIF, and SARIF upload to the Security tab needs Code Security on a private repo anyway, so confirm before considering them.
- Pin `actions/checkout` to a full commit SHA, like the existing workflow.
- Pin the binary to an exact version and verify its checksum against the release's checksum file.
- Dependabot cannot update a binary version inside a `run:` step, so put a reminder in a comment or check it during upgrades.

### 5. Update the README
In "Continuous integration and dependency monitoring":
- describe the new workflow and how to read its results;
- replace the statement that no vulnerability audit runs;
- keep the note that Dependabot alerts cover only direct `pom.xml` entries;
- explain how to add an ignore entry.

## Done when
- The workflow fails on a PR that introduces a known-vulnerable dependency. Test this with a throwaway branch that pins an old vulnerable version, then delete the branch.
- The workflow passes on `main`.
- The weekly run appears in the Actions tab.
- The job's token has only `contents: read`.

## Limitations
- Failing on any finding is strict, and I have not confirmed that the scanner has a severity threshold. Check this, and decide whether high and critical only is better.
- A new CVE can turn the weekly run red with no code change. That is the purpose of the schedule, but enable GitHub's failed-workflow notifications so it gets seen.
- Resolution uses the deps.dev API by default. `--data-source=native` queries Maven Central instead.

## References
- [OSV-Scanner supported languages and lockfiles](https://google.github.io/osv-scanner/supported-languages-and-lockfiles/)
- [OSV-Scanner GitHub Action](https://google.github.io/osv-scanner/github-action/)
- [CycloneDX Maven plugin](https://github.com/CycloneDX/cyclonedx-maven-plugin)
