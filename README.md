# 2026-AA Concert Pilot — Concert + Bob CVE Auto-Remediation

This repository is the **2026 AA Concert Pilot** demo, showcasing automated CVE remediation using IBM Concert Protect + Bob Shell across two intentionally vulnerable Java applications.

## 📦 Demo Applications

### `app/credit-app/` — Struts 2.3.31 Credit Reporting App
Replicates the 2017 Equifax data breach scenario.

- **CVE-2017-5638** — Apache Struts 2.3.31 RCE via Content-Type header (CVSS 10.0)
- **CVE-2015-6420** — commons-collections 3.2.1 deserialization (CVSS 7.5)

### `app/dvja/` — Damn Vulnerable Java Application (Full)
A complete Struts 2 / Spring / Hibernate web application covering the full OWASP Top 10. 21 Java source files, 63 JSPs, Docker + MySQL.

Key CVEs surfaced by Concert Protect:

| Dependency | Version | CVE | CVSS |
|---|---|---|---|
| log4j-core | 2.3 | CVE-2021-44228 Log4Shell | 10.0 |
| struts2-core | 2.3.30 | CVE-2017-5638 Equifax RCE | 10.0 |
| spring-core | 3.0.5.RELEASE | CVE-2022-22965 Spring4Shell | 9.8 |
| commons-fileupload | 1.3.2 | CVE-2016-1000031 | 9.8 |
| mysql-connector-java | 5.1.42 | CVE-2019-2692 | 8.8 |
| commons-collections | 3.1 | CVE-2015-6420 | 7.5 |

---

## Workflow Overview

![Workflow Diagram](./workflow_diagram.png)

This automated [workflow](./.github/workflows/bob-shell-v2.yml) orchestrates CVE remediation through four key stages:

1. **DevOps Phase** — Bob Shell researches the CVE using Concert API and external sources (NVD, GitHub Advisory), performs false positive detection, then creates a PR with package upgrades
2. **DevOps Approval** — DevOps engineer reviews and approves the package changes in the GitHub UI
3. **Unit Testing** — Automated tests run against the upgraded dependencies to verify compatibility
4. **Code Remediation** — If tests fail, Bob Shell automatically fixes application code to work with the new package versions
5. **Merge & Deploy** — Software Lead assigned for final code review before merge

Bob Shell runs in two modes: `devops-concert-shell-v2` for CVE research + package upgrades, and `advanced` mode for code fixes.

---

## CVE Score Threshold Auto-Trigger

[`concert-cve-threshold-trigger.yml`](./.github/workflows/concert-cve-threshold-trigger.yml) runs every 6 hours and automatically opens a GitHub issue + applies the `cve-remediation` label for any Concert CVE above the configured threshold — fully hands-free pipeline start.

Configure the threshold via repository variable `CVSS_THRESHOLD` (default: `7.0`). Supports manual dispatch with dry-run mode.

---

## Repository Setup

### GitHub Environments

Two environments are required:

| Environment | Purpose | Secrets stored here |
|---|---|---|
| **`apikey-env`** | Provides all API credentials to workflow jobs | `BOBSHELL_API_KEY`, `BOBSHELL_MCP_CONFIG`, `CONCERT_API_KEY`, `CONCERT_INSTANCE_ID` |
| **`devops-approval`** | Human gate — pauses pipeline for DevOps review | Required reviewer: `e-wong` (or your DevOps user) |

Configure at: `https://github.ibm.com/e-wong/2026-aa-concert-pilot/settings/environments`

#### `apikey-env` — Secrets
| Secret | Value |
|---|---|
| `BOBSHELL_API_KEY` | Your Bob Shell API key |
| `BOBSHELL_MCP_CONFIG` | Full JSON from `.bob/mcp.json` with real `github.ibm.com` PAT (needs `repo` scope) |
| `CONCERT_API_KEY` | `C_API_KEY <your key>` |
| `CONCERT_INSTANCE_ID` | Your Concert instance UUID |

#### `devops-approval` — Protection Rules
1. Go to Settings → Environments → `devops-approval`
2. Add **Deployment protection rules** → **Required reviewers** → select your DevOps user

### Repository Variables (Settings → Variables → Actions)

```
CONCERT_HOSTNAME=https://<your-concert-ip>:12443
INGESTION_JOB_ID=<uuid of source_control_discovery_job>
CVSS_THRESHOLD=7.0
BOBSHELL_SETTINGS={"security":{"ibmTelemetry":{"enabled":false}}}
DEVOPS_REVIEWERS=["e-wong"]
LEAD_REVIEWERS=["e-wong"]
SWE_REVIEWERS=["e-wong"]
```

---

## Triggering Remediation

### Automatic (Concert CVE threshold monitor)
Once secrets are configured, `concert-cve-threshold-trigger.yml` polls Concert every 6 hours and opens issues automatically for CVEs above `CVSS_THRESHOLD`.

### Manual
1. Create a GitHub Issue with CVE details in the body
2. Apply the label **`cve-remediation`** — this fires `bob-shell-v2.yml` immediately

Issue body format:
```
CVE ID: CVE-2017-5638
Package: org.apache.struts:struts2-core
Version: 2.3.31
CVSS: 10.0
Severity: CRITICAL
Application: credit-app
```

---

## False Positive Detection

Before creating any PR, Bob analyzes the codebase to determine if the CVE actually affects the application:

1. **Extract CVE context** — parse vulnerable functions, attack vectors, severity
2. **Search codebase** — find all imports and usages of the vulnerable package
3. **Analyze call paths** — determine if vulnerable functions are actually invoked
4. **Evaluate context** — trace data flow from user input to vulnerable code
5. **Assess deployment** — consider exposure, WAF, and security controls
6. **Make determination** — FALSE POSITIVE or TRUE POSITIVE per CVE
7. **Act accordingly:**
   - **False positive** → posts analysis comment on issue, tags DevOps for review, Concert API patched to mark as false positive after approval. No PR created.
   - **True positive** → creates `fix/cve-<ID>-<package>` branch, upgrades `pom.xml`, opens PR, requests DevOps approval

When uncertain, Bob defaults to **TRUE POSITIVE** (safer).

---

## Reset / Reproduce Scenarios

```bash
# Re-introduce vulnerabilities (credit-app)
cp app/credit-app/pom-vuln.xml app/credit-app/pom.xml

# Clear vulnerabilities (credit-app)
cp app/credit-app/pom-reset.xml app/credit-app/pom.xml

# Generate fresh SBOMs
mvn package -DskipTests -f app/credit-app/pom.xml
mvn package -DskipTests -f app/dvja/pom.xml
```

---

## Watch it in action

[Click-through Demo](https://demo-now.techzone.ibm.com/psl/fmx0mz7) — enable voiceover for narration.
