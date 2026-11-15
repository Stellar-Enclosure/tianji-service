#!/bin/sh
set -eu

usage() {
    printf 'Usage: %s prepare <private-worker-classpath-file> | run <private-manifest>\n' "$0" >&2
    exit 64
}

[ "$#" -eq 2 ] || usage
case "$1" in prepare|run) ;; *) usage ;; esac
case "$2" in /*) ;; *) usage ;; esac

automation_root=$(CDPATH='' cd -- "$(dirname "$0")/../.." && pwd -P)
cd "$automation_root"
: "${JAVA_HOME:?Use JDK 25}"
automation_java_major=$("$JAVA_HOME/bin/java" -XshowSettings:properties -version 2>&1 |
    awk -F'= ' '/java.specification.version/{print $2; exit}')
[ "$automation_java_major" = 25 ] || exit 65

umask 077
automation_stamp=$(mktemp "${TMPDIR:-/tmp}/tianji-automation-report.XXXXXX")
trap 'rm -f "$automation_stamp"' EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

python3 - "$1" "$2" <<'PY'
from pathlib import Path
from stat import S_IMODE
from sys import argv

mode, value = argv[1:]
path = Path(value)
if path != path.absolute() or path.parent != path.parent.resolve(strict=True):
    raise SystemExit("Fixture path must be absolute and canonical")
if path.parent.stat().st_uid != Path.cwd().stat().st_uid or S_IMODE(path.parent.stat().st_mode) != 0o700:
    raise SystemExit("Fixture path requires its owner's private directory")
if path.is_symlink() or (mode == 'prepare' and path.exists()):
    raise SystemExit("Fixture path already exists or is symbolic")
if mode == 'run' and (not path.is_file() or path.resolve(strict=True) != path
                      or path.stat().st_uid != path.parent.stat().st_uid
                      or S_IMODE(path.stat().st_mode) != 0o600):
    raise SystemExit("Run requires a private owner-only manifest")
PY

case "$1" in
    prepare)
        automation_commit=$(git rev-parse --verify HEAD)
        automation_build_id="automation-fixture-$(git rev-parse --short=12 HEAD)"
        STELLAR_VERIFY_GOAL=verify sh scripts/verify-source-reactor.sh -B -ntp clean \
            -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false \
            -Ppermission-producer "-Dpermission.commit=$automation_commit" "-Dpermission.buildId=$automation_build_id" \
            -Dtest=AutomationAcceptanceHarnessTest,AutomationWorkerAcceptanceClasspathTest,WorkerDependencyBoundaryTest \
            -Dtianji.automation.acceptance.prepare=true "-Dtianji.automation.acceptance.workerClasspath=$2"
        set -- AutomationAcceptanceHarnessTest:3 AutomationWorkerAcceptanceClasspathTest:1 WorkerDependencyBoundaryTest:1
        ;;
    run)
        STELLAR_VERIFY_GOAL=test sh scripts/verify-source-reactor.sh -B -ntp \
            -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AutomationIndependentServicesIT \
            -Dtianji.automation.acceptance.required=true "-Dtianji.automation.acceptance.manifest=$2"
        set -- AutomationIndependentServicesIT:7
        ;;
esac

python3 - "$automation_stamp" "$@" <<'PY'
from pathlib import Path
from sys import argv
from xml.etree import ElementTree
from zipfile import ZipFile

stamp = Path(argv[1]).stat().st_mtime_ns
for selector in argv[2:]:
    name, expected = selector.split(":")
    reports = [path for path in Path(".").glob("stellar-enclosure-tianji-*/target/surefire-reports/TEST-*.xml")
               if path.name.removesuffix(".xml").endswith("." + name) and path.stat().st_mtime_ns >= stamp]
    if len(reports) != 1:
        raise SystemExit(f"{name}: missing or ambiguous current Surefire report")
    suite = ElementTree.parse(reports[0]).getroot()
    counts = {field: int(suite.attrib[field]) for field in ("tests", "failures", "errors", "skipped")}
    if counts["tests"] != int(expected) or any(counts[field] for field in ("failures", "errors", "skipped")):
        raise SystemExit(f"{name}: {counts}")
    print(f"{name}: {counts}")

for role in ("process-server", "agent-server", "worker-starter"):
    target = Path(f"stellar-enclosure-tianji-{role}/target")
    if role == "worker-starter":
        artifacts = [p for p in target.glob("*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar", "-tests.jar"))]
    else:
        artifacts = list(target.glob("*-exec.jar"))
    if len(artifacts) != 1:
        raise SystemExit(f"{role}: expected exactly one product archive")
    with ZipFile(artifacts[0]) as archive:
        entries = archive.namelist()
        if role != "worker-starter" and not {
                "META-INF/permissions/manifest-v1.json", "META-INF/permissions/manifest-v1.evidence.json"}.issubset(entries):
            raise SystemExit(f"{role}: required generated permission artifact evidence is missing")
        for entry in entries:
            name = entry.rsplit("/", 1)[-1]
            if name.startswith("Automation") and (
                    "AcceptanceMain" in name or "AcceptanceHarness" in name or "IndependentServicesIT" in name):
                raise SystemExit(f"{role}: test class packaged in product archive")
PY
