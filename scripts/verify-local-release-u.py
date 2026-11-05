"""Run the exact Tianji Service finite U allowlist on locked local source."""

import os
import re
import runpy
import secrets
import signal
import stat
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

EVIDENCE = runpy.run_path(str(Path(__file__).with_name("verify-release-evidence.py")))
InvalidEvidence = EVIDENCE["InvalidEvidence"]
Unavailable = EVIDENCE["Unavailable"]
require = EVIDENCE["require"]
FLAGS = {"--checkout", "--source-lock", "--settings", "--java-home", "--maven-user-home"}
NONCE_KEY = "tianji.acceptance.u.runId"
SETTINGS_NS = "{http://maven.apache.org/SETTINGS/1.2.0}"
DIST = re.compile(r"https://repo\.maven\.apache\.org/maven2/org/apache/maven/apache-maven/"
                  r"([0-9]+\.[0-9]+\.[0-9]+)/apache-maven-\1-bin\.zip")
TIMEOUT = 1800

AR = "stellar-enclosure-tianji-agent-runtime"
AS = "stellar-enclosure-tianji-agent-server"
PS = "stellar-enclosure-tianji-process-server"
CL = "stellar-enclosure-tianji-client"
W = "stellar-enclosure-tianji-worker-starter"
PREFIX = "top.egon.cola.platform.tianji."
OWNERS = (
    (AS, "agent.server.api.AgentRunHttpTest"),
    (AS, "agent.server.api.AgentRunEventStreamCapacityTest"),
    (AR, "agent.application.run.AgentRunReconcilerTest"),
    (AR, "agent.infrastructure.adk.AdkNativeAgentCompletionTest"),
    (AR, "agent.infrastructure.adk.session.AdkSessionEventCodecTest"),
    (PS, "process.server.release.ProcessModelValidatorTest"),
    (PS, "process.server.bridge.AgentStartWorkerTest"),
    (AR, "agent.infrastructure.adk.AdkGovernedProcessLoopTest"),
    (PS, "process.server.task.FlowableHumanTaskServiceTest"),
    (PS, "process.server.release.CmmnModelValidatorTest"),
    (W, "worker.RemoteWorkerPollerTest"),
    (AR, "agent.infrastructure.adk.AdkNativeResumeSemanticsTest"),
    (CL, "client.HttpAgentResultTraceLinkTest"),
    (AS, "agent.server.integration.process.AgentResultSpanObservationTest"),
    (AS, "agent.server.integration.process.AgentResultDeliveryHandlerTest"),
    (PS, "process.server.api.AgentResultInboxControllerTest"),
    (PS, "process.server.bridge.AgentResultInboxLagObservationTest"),
)
SELECTORS = tuple(name.rsplit(".", 1)[1] for _, name in OWNERS)
DYNAMIC_TEST_ROWS = {
    "agent.infrastructure.adk.AdkGovernedProcessLoopTest": {"governed_process_injection_corpus": 6},
}
FIXED_PROPERTIES = {
    "skipITs": "true",
    "skipTests": "false",
    "surefire.failIfNoSpecifiedTests": "false",
    "invoker.skip": "true",
    "archetype.test.skip": "true",
    "otel.traces.exporter": "none",
    "otel.metrics.exporter": "none",
    "otel.logs.exporter": "none",
}


def arguments(argv):
    if len(argv) != 2 * len(FLAGS):
        raise ValueError
    values = {}
    for key, value in zip(argv[::2], argv[1::2]):
        if key not in FLAGS or key in values or not value.startswith("/") or any(ord(c) < 32 for c in value):
            raise ValueError
        values[key] = value
    return values


def directory(raw):
    path = Path(raw).resolve(strict=True)
    require(path.is_dir())
    return path


def stable_bytes(path, limit, *, private=False):
    require(path.is_absolute() and path.resolve(strict=True) == path)
    before = path.lstat()
    require(stat.S_ISREG(before.st_mode) and before.st_uid == os.geteuid()
            and before.st_mode & (0o077 if private else 0o022) == 0 and 0 < before.st_size <= limit)
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    identity = EVIDENCE["file_identity"]
    with os.fdopen(fd, "rb") as stream:
        require(identity(os.fstat(stream.fileno())) == identity(before))
        raw = stream.read(limit + 1)
        require(len(raw) == before.st_size and identity(os.fstat(stream.fileno())) == identity(before))
    require(identity(path.lstat()) == identity(before))
    return raw


def offline_settings(raw):
    root = EVIDENCE["parse_xml"](raw)
    namespace = SETTINGS_NS if root.tag == SETTINGS_NS + "settings" else ""
    require(root.tag == namespace + "settings" and not root.attrib and not (root.text or "").strip())
    values = {}
    for child in root:
        require(child.tag in (namespace + "localRepository", namespace + "interactiveMode")
                and child.tag not in values and not child.attrib and not list(child) and not (child.tail or "").strip())
        values[child.tag] = (child.text or "").strip()
    repository = values.get(namespace + "localRepository", "")
    require(repository.startswith("/") and values.get(namespace + "interactiveMode", "false") == "false"
            and not any(ord(char) < 32 for char in repository))
    directory(repository)


def wrapper_ready(checkout, cache):
    config = stable_bytes(checkout / ".mvn/wrapper/maven-wrapper.properties", 16_384).decode("utf-8")
    urls = [line.split("=", 1)[1].strip() for line in config.splitlines() if line.startswith("distributionUrl=")]
    require(len(urls) == 1 and (match := DIST.fullmatch(urls[0])) is not None)
    value = 0
    for character in urls[0]:
        value = (value * 31 + ord(character)) & 0xFFFFFFFF
    home = cache / "wrapper/dists" / ("apache-maven-" + match[1]) / f"{value:x}"
    require(stable_bytes(home / "mvnw.url", 4096).decode("utf-8").strip() == urls[0]
            and (home / "bin/mvn").is_file() and os.access(home / "bin/mvn", os.X_OK))


def java_ready(java_home):
    with (java_home / "release").open("rb") as stream:
        release = stream.read(16_385)
    require(len(release) <= 16_384 and re.search(rb'(?m)^JAVA_VERSION="25(?:[.+"-])', release) is not None
            and (java_home / "bin/java").is_file() and os.access(java_home / "bin/java", os.X_OK))


def reactor_command(checkout, settings, nonce):
    return (str(checkout / "scripts/verify-source-reactor.sh"), "-gs", str(settings), "-s", str(settings),
            "-o", "-B", "-ntp", *(f"-D{key}={value}" for key, value in FIXED_PROPERTIES.items()),
            "-Dtest=" + ",".join(SELECTORS), f"-D{NONCE_KEY}={nonce}", f"-Dsurefire.reportNameSuffix={nonce}")


def reactor_environment(checkout, java_home, cache):
    environment = {
        "HOME": str(Path.home()), "PATH": os.defpath, "LC_ALL": "C", "JAVA_HOME": str(java_home),
        "MAVEN_USER_HOME": str(cache), "MAVEN_SKIP_RC": "true",
        "ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS": "false", "STELLAR_VERIFY_GOAL": "test",
    }
    for variable, sibling in (
        ("STELLAR_COMPONENTS_DIR", "Stellar-Enclosure-Components"),
        ("STELLAR_SHOUBING_DIR", "tianquan-shoubing-service"),
        ("STELLAR_JIANSHEN_DIR", "tianquan-jianshen-service"),
        ("STELLAR_TIANSHU_DIR", "tianshu-service"),
    ):
        environment[variable] = str(checkout.parent / sibling)
    return environment


def invoke_reactor(command, checkout, environment):
    with subprocess.Popen(command, cwd=checkout, env=environment, stdin=subprocess.DEVNULL,
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                          start_new_session=True, umask=0o077) as process:
        try:
            return process.wait(timeout=TIMEOUT)
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGKILL)
            process.wait()
            raise Unavailable from None


def report_path(checkout, module, name, nonce):
    return checkout / module / "target/surefire-reports" / f"TEST-{name}-{nonce}.xml"


def verify_report_targets(checkout, nonce):
    for module in {module for module, _ in OWNERS}:
        for directory_path in (checkout / module, checkout / module / "target", checkout / module / "target/surefire-reports"):
            if directory_path.exists() or directory_path.is_symlink():
                require(directory_path.is_dir() and directory_path.resolve(strict=True) == directory_path)
    for module, name in OWNERS:
        path = report_path(checkout, module, PREFIX + name, nonce)
        require(not path.exists() and not path.is_symlink())


def collect_reports(checkout, nonce):
    require(re.fullmatch(r"[0-9a-f]{32}", nonce) is not None)
    expected = {**FIXED_PROPERTIES, NONCE_KEY: nonce, "surefire.reportNameSuffix": nonce,
                "java.specification.version": "25", "test": ",".join(SELECTORS)}
    reports = []
    total = 0
    for module, relative in OWNERS:
        name = PREFIX + relative
        raw = stable_bytes(report_path(checkout, module, name, nonce), EVIDENCE["XML_LIMIT"], private=True)
        total += len(raw)
        require(total <= EVIDENCE["ALL_XML_LIMIT"])
        suite = EVIDENCE["parse_xml"](raw)
        owner = {"module": module, "className": name, "methods": [], "minimumTests": 1}
        counts, flaky, complete = EVIDENCE["summarize_xml_suite"](
            suite, owner, set(SELECTORS), suffix=nonce, expected_basedir=str(checkout / module),
            expected_properties=expected, expected_repetitions=DYNAMIC_TEST_ROWS.get(relative),
        )
        require(complete and not flaky and counts["executed"] > 0
                and counts["failures"] == counts["errors"] == counts["skipped"] == 0)
        reports.append({"module": module, "className": name, "tests": counts["executed"], "sha256": EVIDENCE["sha"](raw)})
    return reports


def now():
    return datetime.now(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")


def run(argv, stdout, stderr, *, git_runner=EVIDENCE["run_git"], reactor=invoke_reactor):
    try:
        values = arguments(argv)
    except ValueError:
        return EVIDENCE["diagnostic"](stderr, 64, b"TIANJI_LOCAL_U_USAGE\n")
    try:
        checkout = EVIDENCE["canonical_directory"](values["--checkout"])
        settings = Path(values["--settings"])
        lock = Path(values["--source-lock"])
        lock_bytes = stable_bytes(lock, 4096)
        settings_bytes = stable_bytes(settings, 16_384)
        heads = EVIDENCE["revisions"](EVIDENCE["strict_json"](lock_bytes))
        EVIDENCE["verify_checkout"](str(checkout), heads["tianjiService"], heads, git_runner)
        java_home = directory(values["--java-home"])
        cache = directory(values["--maven-user-home"])
        java_ready(java_home)
        wrapper_ready(checkout, cache)
        offline_settings(settings_bytes)
        nonce = secrets.token_hex(16)
        verify_report_targets(checkout, nonce)
        started = now()
        code = reactor(reactor_command(checkout, settings, nonce), checkout,
                       reactor_environment(checkout, java_home, cache))
        if code != 0:
            return EVIDENCE["diagnostic"](stderr, 1, b"TIANJI_LOCAL_U_REACTOR_FAILED\n")
        reports = collect_reports(checkout, nonce)
        EVIDENCE["verify_checkout"](str(checkout), heads["tianjiService"], heads, git_runner)
        require(stable_bytes(lock, 4096) == lock_bytes and stable_bytes(settings, 16_384) == settings_bytes)
        summary = {
            "profile": "tianji-local-finite-u-v1", "status": "FINITE_U_PASSED", "runId": nonce,
            "startedAt": started, "finishedAt": now(), "sourceHeads": heads,
            "sourceLockSha256": EVIDENCE["sha"](lock_bytes), "settingsSha256": EVIDENCE["sha"](settings_bytes),
            "classes": len(reports), "tests": sum(report["tests"] for report in reports), "reports": reports,
        }
        stdout.write(EVIDENCE["canonical"](summary) + b"\n")
        stdout.flush()
        return 0
    except (InvalidEvidence, UnicodeError, ValueError):
        return EVIDENCE["diagnostic"](stderr, 65, b"TIANJI_LOCAL_U_REJECTED\n")
    except (OSError, Unavailable, subprocess.SubprocessError):
        return EVIDENCE["diagnostic"](stderr, 66, b"TIANJI_LOCAL_U_UNAVAILABLE\n")


if __name__ == "__main__":
    sys.exit(run(sys.argv[1:], sys.stdout.buffer, sys.stderr.buffer))
