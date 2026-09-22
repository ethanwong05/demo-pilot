#!/usr/bin/env bash
# =============================================================================
# run-validation.sh
# Validates CVE targeting and auto-remediation for the credit-app.
# All output is captured into TEST/<YYYY-MM-DD_HH-MM-SS>/
#
# Usage (run from your own terminal inside credit-app/):
#   bash run-validation.sh
# =============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TIMESTAMP="$(date '+%Y-%m-%d_%H-%M-%S')"
OUT_DIR="${SCRIPT_DIR}/TEST/${TIMESTAMP}"

mkdir -p "${OUT_DIR}"

LOG="${OUT_DIR}/run.log"
_log() { echo "$@" | tee -a "${LOG}"; }

_log "============================================================"
_log "  Credit-App CVE Validation Run"
_log "  Timestamp : ${TIMESTAMP}"
_log "  Output dir: ${OUT_DIR}"
_log "============================================================"
_log ""

# ---------------------------------------------------------------------------
# 1. CVE TARGETING CHECK
# ---------------------------------------------------------------------------
_log "--- [1/4] CVE Targeting Check ---"

POM="${SCRIPT_DIR}/pom.xml"
STRUTS_VERSION="$(sed -n 's|.*<struts2\.version>\([^<]*\)</struts2\.version>.*|\1|p' "${POM}" | head -1 || true)"
CC_VERSION="$(awk '/commons-collections</{found=1} found && /<version>/{gsub(/.*<version>|<\/version>.*/,""); print; exit}' "${POM}" || true)"
CF_VERSION="$(awk '/commons-fileupload/{found=1} found && /<version>/{gsub(/.*<version>|<\/version>.*/,""); print; exit}' "${POM}" || true)"

_log "  pom.xml struts2.version       = ${STRUTS_VERSION}"
_log "  commons-collections version   = ${CC_VERSION:-not explicitly set}"
_log "  commons-fileupload version    = ${CF_VERSION:-not explicitly set (transitive)}"

if [[ "${STRUTS_VERSION}" == "2.3.31" ]]; then
    _log "  ✓ VULNERABLE version confirmed (CVE-2017-5638 target active)"
    CVE_STATUS="VULNERABLE (2.3.31 — CVE-2017-5638 target)"
elif [[ "${STRUTS_VERSION}" == "2.5."* ]] || [[ "${STRUTS_VERSION}" == "6."* ]] || [[ "${STRUTS_VERSION}" == "7."* ]]; then
    _log "  ✓ REMEDIATED version in use (${STRUTS_VERSION})"
    CVE_STATUS="REMEDIATED (${STRUTS_VERSION})"
else
    _log "  ⚠ Unknown Struts version: ${STRUTS_VERSION} — manual review required"
    CVE_STATUS="UNKNOWN (${STRUTS_VERSION})"
fi
_log ""

# ---------------------------------------------------------------------------
# 2. DEPENDENCY TREE
# ---------------------------------------------------------------------------
_log "--- [2/4] Dependency Tree ---"
if mvn -f "${POM}" dependency:tree --no-transfer-progress \
        > "${OUT_DIR}/dependency-tree.txt" 2>&1; then
    _log "  ✓ dependency-tree.txt written"
else
    _log "  ⚠ dependency:tree had non-zero exit (see dependency-tree.txt)"
fi
_log ""

# ---------------------------------------------------------------------------
# 3. MAVEN TEST + SBOM GENERATION
#    mvn package runs tests AND triggers the CycloneDX plugin (bound to package)
# ---------------------------------------------------------------------------
_log "--- [3/4] mvn package (tests + SBOM generation) ---"
mvn -f "${POM}" package --no-transfer-progress \
    > "${OUT_DIR}/mvn-package.txt" 2>&1
MVN_EXIT=$?

if [[ ${MVN_EXIT} -eq 0 ]]; then
    _log "  ✓ BUILD SUCCESS"
else
    _log "  ✗ BUILD FAILED (exit ${MVN_EXIT})"
fi

SUMMARY="$(grep 'Tests run:' "${OUT_DIR}/mvn-package.txt" | tail -1 || true)"
_log "  Test summary: ${SUMMARY}"

# Copy generated SBOM into the timestamped output dir
SBOM_SRC="${SCRIPT_DIR}/credit-app-sbom.json"
if [[ -f "${SBOM_SRC}" ]]; then
    cp "${SBOM_SRC}" "${OUT_DIR}/credit-app-sbom.json"
    _log "  ✓ SBOM copied → ${OUT_DIR}/credit-app-sbom.json"
else
    _log "  ⚠ SBOM not found at ${SBOM_SRC}"
fi
_log ""

# ---------------------------------------------------------------------------
# 4. COPY SUREFIRE REPORTS
# ---------------------------------------------------------------------------
_log "--- [4/4] Copying surefire reports ---"
SUREFIRE_SRC="${SCRIPT_DIR}/target/surefire-reports"
if [[ -d "${SUREFIRE_SRC}" ]]; then
    cp -r "${SUREFIRE_SRC}" "${OUT_DIR}/surefire-reports"
    REPORT_COUNT="$(find "${OUT_DIR}/surefire-reports" -name '*.xml' | wc -l | tr -d ' ')"
    _log "  ✓ Copied ${REPORT_COUNT} XML report(s)"
else
    _log "  ⚠ No surefire-reports directory found"
fi
_log ""

# ---------------------------------------------------------------------------
# SUMMARY
# ---------------------------------------------------------------------------
{
    echo "Credit-App CVE Validation Summary"
    echo "=================================="
    echo "Run timestamp : ${TIMESTAMP}"
    echo "Struts version: ${STRUTS_VERSION}"
    echo "CVE status    : ${CVE_STATUS}"
    echo "Maven result  : $(if [[ ${MVN_EXIT} -eq 0 ]]; then echo 'BUILD SUCCESS'; else echo 'BUILD FAILED'; fi)"
    echo "Test summary  : ${SUMMARY}"
    echo ""
    echo "CVEs in scope:"
    echo "  CVE-2017-5638    Apache Struts2 OGNL injection via Content-Type   CVSS 10.0"
    echo "  CVE-2016-1000031 commons-fileupload deserialization                CVSS 9.8"
    echo "  CVE-2015-6420    commons-collections deserialization               CVSS 7.5"
    echo ""
    echo "Remediation reference: pom-reset.xml (struts2 7.1.1, commons-collections removed)"
} | tee "${OUT_DIR}/summary.txt" | tee -a "${LOG}"

_log ""
_log "All outputs → ${OUT_DIR}/"
find "${OUT_DIR}" -type f | sort | sed 's|^|  |' | tee -a "${LOG}"

exit ${MVN_EXIT}
