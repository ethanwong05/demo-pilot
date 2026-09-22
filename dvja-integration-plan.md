# DVJA Integration Plan
## Concert Protect Demo — Full Vulnerable Java Application

### Top-Level Overview

Bring the full **Damn Vulnerable Java Application (DVJA)** (`appsecco/dvja`) into
`concert-bob-remediate/app/dvja/` so it becomes a second, richer target application
alongside `credit-app` within the same Concert + Bob remediation repo.

DVJA is a real Struts 2.x / Java EE web application with intentional vulnerabilities
across OWASP Top 10 categories. Its `pom.xml` declares a wide set of known-CVE
dependencies in the same CVE family as `credit-app` (Struts, commons-collections,
MySQL connector, etc.), making it ideal for Concert Protect ingestion and Bob
auto-remediation demos.

**Scope:** Clone DVJA source → add to repo → add CycloneDX SBOM plugin →
wire into the existing Concert ingestion + Bob workflow → commit + push to
`github.ibm.com/e-wong/concert-bob-remediate`.

**Out of scope:** Running DVJA locally (requires MySQL + Tomcat); modifying
DVJA's application logic; integrating DVJA tests into the Bob remediation loop
(that is a future task).

---

## Known DVJA dependency CVEs (from published pom.xml)

These are the CVEs Concert Protect will surface once the SBOM is ingested:

| Dependency | Version | CVE | CVSS |
|---|---|---|---|
| struts2-core | 2.3.12 | CVE-2017-5638 (RCE via Content-Type) | 10.0 |
| struts2-core | 2.3.12 | CVE-2013-2251 (RCE via action/redirect) | 10.0 |
| commons-collections | 3.1 | CVE-2015-6420 (deserialization RCE) | 7.5 |
| mysql-connector-java | 5.1.26 | CVE-2015-2575, CVE-2019-2692 | 7.1–8.8 |
| commons-fileupload | 1.2.2 (transitive) | CVE-2016-1000031 (deserialization) | 9.8 |
| xwork-core | 2.3.12 (transitive) | CVE-2017-9804, CVE-2018-1327 | 9.8 |

---

## Sub-Tasks

---

### Sub-Task 1 — Clone DVJA into app/dvja/

**Status:** `[ ] pending`

**Intent:**
Copy the full DVJA source tree into `concert-bob-remediate/app/dvja/` using
`git clone` with `--depth 1` (we don't need its full git history). Strip the
nested `.git/` directory so it becomes part of the parent repo rather than a
submodule.

**Expected Outcomes:**
- `app/dvja/` exists with the full DVJA directory structure
- `app/dvja/pom.xml` is present with the original vulnerable dependency versions
- `app/dvja/src/`, `app/dvja/WebContent/`, `app/dvja/docker/`, `app/dvja/README.md` all present
- No nested `.git` directory inside `app/dvja/`

**Todo List:**
1. From the repo root, run:
   ```
   git clone --depth 1 https://github.com/appsecco/dvja.git app/dvja
   rm -rf app/dvja/.git
   ```
2. Verify the directory structure looks correct (`pom.xml`, `src/`, `WebContent/`)
3. Check that `app/dvja/.gitignore` does not conflict with the parent repo's `.gitignore`

**Relevant Context:**
- DVJA upstream: https://github.com/appsecco/dvja
- Parent `.gitignore`: `concert-bob-remediate/.gitignore`
- Should NOT be added as a git submodule — just a plain directory copy

---

### Sub-Task 2 — Add CycloneDX Maven plugin to app/dvja/pom.xml

**Status:** `[ ] pending`

**Intent:**
Add the same `cyclonedx-maven-plugin 2.8.2` configuration used in `credit-app`
to DVJA's `pom.xml`, bound to the `package` phase, outputting
`app/dvja/dvja-sbom.json`. This is what Concert Protect ingests to discover CVEs.

**Expected Outcomes:**
- `app/dvja/pom.xml` contains the CycloneDX plugin block
- Running `mvn package -DskipTests -f app/dvja/pom.xml` produces `app/dvja/dvja-sbom.json`
- The SBOM JSON contains all the CVE-bearing dependencies listed in the table above

**Todo List:**
1. Open `app/dvja/pom.xml`
2. Insert the CycloneDX plugin block inside `<build><plugins>` — use the same
   configuration as `app/credit-app/pom.xml` but set `<outputName>dvja-sbom</outputName>`
3. Set `<outputDirectory>${project.basedir}</outputDirectory>`
4. Run `mvn package -DskipTests -f app/dvja/pom.xml` to verify SBOM is generated
5. Confirm `app/dvja/dvja-sbom.json` is present and non-empty

**Relevant Context:**
- Reference plugin config: `app/credit-app/pom.xml` lines 156–190
- Plugin version: `2.8.2` (CycloneDX Maven plugin, latest stable)
- Output must be CycloneDX 1.6 JSON format (matches Concert's expected format)

---

### Sub-Task 3 — Add SBOM output files to .gitignore

**Status:** `[ ] pending`

**Intent:**
The generated `credit-app-sbom.json` and `dvja-sbom.json` are build artifacts —
they should NOT be committed alongside source, they should be regenerated on
each build. Add them to `.gitignore` so they don't clutter commits.

Exception: a *committed* snapshot SBOM (e.g. `app/dvja/dvja-sbom-snapshot.json`)
may be useful for Concert ingestion without requiring a build step. Decide
at implementation time whether to commit the snapshot.

**Expected Outcomes:**
- `app/credit-app/credit-app-sbom.json` is listed in `.gitignore` (or the app-level `.gitignore`)
- `app/dvja/dvja-sbom.json` is listed in `.gitignore`
- Committed SBOMs (if any) use a `-snapshot` suffix and are explicitly force-added

**Todo List:**
1. Open `concert-bob-remediate/.gitignore`
2. Add entries for `app/credit-app/credit-app-sbom.json` and `app/dvja/dvja-sbom.json`
3. If a committed snapshot is wanted, generate the SBOM, rename to `*-snapshot.json`,
   and `git add -f` it

**Relevant Context:**
- Concert ingestion currently reads from the committed `app/codescan-cyclonedx-sbom.json`
- The `trigger-concert-ingestion.yml` workflow uses `source_control_discovery_job` —
  Concert pulls from the repo, so a committed SBOM is required for ingestion to work
  without a separate build step

---

### Sub-Task 4 — Update Concert ingestion workflow to include DVJA SBOM

**Status:** `[ ] pending`

**Intent:**
The existing `trigger-concert-ingestion.yml` workflow triggers Concert to re-scan
the repo on every push to `main`. Concert's `source_control_discovery_job` will
automatically pick up any CycloneDX SBOM files it finds in the repo.

Verify that the SBOM filename and location match what Concert expects, or update
the workflow/Concert job configuration accordingly.

**Expected Outcomes:**
- Concert ingestion job discovers both `app/credit-app/credit-app-sbom.json`
  AND `app/dvja/dvja-sbom.json` (or their `-snapshot` equivalents)
- No changes to the workflow YAML are required unless the Concert job needs
  explicit file paths configured

**Todo List:**
1. Read `concert-bob-remediate/.github/workflows/trigger-concert-ingestion.yml`
   to confirm it sends a generic `source_control_discovery_job` (no hardcoded paths)
2. Read `concert-bob-remediate/README.md` for any Concert SBOM path requirements
3. If Concert requires explicit SBOM paths, update the ingestion job payload in
   `trigger-concert-ingestion.yml` to list both SBOMs
4. If no changes needed, document that Concert auto-discovers CycloneDX files

**Relevant Context:**
- Workflow: `.github/workflows/trigger-concert-ingestion.yml`
- Concert API endpoint: `POST /ingestion/api/v1/job/v2/submit`
- Payload type: `source_control_discovery_job`

---

### Sub-Task 5 — Commit and push to github.ibm.com/e-wong/concert-bob-remediate

**Status:** `[ ] pending`

**Intent:**
Stage all new files (DVJA source tree, updated pom files, updated .gitignore,
any committed SBOM snapshots), commit with a descriptive message, and push
to the `ibm` remote (`git@github.ibm.com:e-wong/concert-bob-remediate.git`).
This triggers Concert ingestion automatically via `trigger-concert-ingestion.yml`.

**Expected Outcomes:**
- `git push ibm main` succeeds
- GitHub Actions on `github.ibm.com/e-wong` shows the `trigger-concert-ingestion` workflow run
- Concert Protect reflects both `credit-app` and `dvja` CVEs after ingestion completes

**Todo List:**
1. `git add app/dvja/ app/credit-app/pom.xml concert-bob-remediate/.gitignore`
2. Add any committed SBOM snapshots with `git add -f`
3. `git commit -m "feat: add DVJA full application for Concert Protect CVE demo"`
4. `git push ibm main`
5. Monitor the `trigger-concert-ingestion` workflow run on github.ibm.com

**Relevant Context:**
- IBM remote: `git@github.ibm.com:e-wong/concert-bob-remediate.git` (SSH, already configured)
- The push will trigger `.github/workflows/trigger-concert-ingestion.yml`
- `CONCERT_API_KEY`, `CONCERT_HOSTNAME`, `INGESTION_JOB_ID` must be set as
  GitHub secrets/variables on `github.ibm.com/e-wong/concert-bob-remediate`
  (same values as the `highorbit25` origin repo)
