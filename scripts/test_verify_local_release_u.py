import io
import json
import os
import runpy
import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree

GATE = runpy.run_path(str(Path(__file__).with_name("verify-local-release-u.py")))
EVIDENCE = GATE["EVIDENCE"]
COUNTS = (9, 2, 5, 12, 6, 21, 10, 36, 13, 67, 11, 3, 2, 2, 10, 7, 4)
CANARY = "RAW-PRIVATE-SENTINEL"


class Fixture:
    def __init__(self, root):
        self.root = root
        self.checkout = root / "tianji-service"
        self.checkout.mkdir()
        self.heads = {key: f"{index:040x}" for index, key in enumerate(EVIDENCE["REPOSITORIES"], 1)}
        self.paths = {}
        for key, sibling in EVIDENCE["REPOSITORIES"].items():
            path = self.checkout if sibling is None else root / sibling
            path.mkdir(exist_ok=True)
            self.paths[path] = self.heads[key]
        self.lock = root / "lock.json"
        self.lock.write_text(json.dumps(self.heads), encoding="utf-8")
        local = root / "local-repository"
        local.mkdir()
        self.settings = root / "settings.xml"
        self.settings.write_text(
            '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"><localRepository>' + str(local)
            + '</localRepository><interactiveMode>false</interactiveMode></settings>', encoding="utf-8")
        self.java = root / "jdk"
        self.binary(self.java / "bin/java")
        (self.java / "release").write_text('JAVA_VERSION="25.0.3"\n', encoding="ascii")
        url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.14/apache-maven-3.9.14-bin.zip"
        wrapper = self.checkout / ".mvn/wrapper/maven-wrapper.properties"
        wrapper.parent.mkdir(parents=True)
        wrapper.write_text("distributionUrl=" + url + "\n", encoding="ascii")
        self.cache = root / "maven-cache"
        distribution = self.cache / "wrapper/dists/apache-maven-3.9.14/db91789b"
        self.cached_maven = distribution / "bin/mvn"
        self.binary(self.cached_maven)
        (distribution / "mvnw.url").write_text(url + "\n", encoding="ascii")
        self.args = ["--checkout", str(self.checkout), "--source-lock", str(self.lock),
                     "--settings", str(self.settings), "--java-home", str(self.java),
                     "--maven-user-home", str(self.cache)]
        self.calls = []
        self.dirty = False
        self.post_dirty = False

    @staticmethod
    def binary(path):
        path.parent.mkdir(parents=True)
        path.write_text("must not execute\n", encoding="ascii")
        path.chmod(0o700)

    def git(self, checkout, command):
        if command == EVIDENCE["TOP"]:
            return os.fsencode(checkout) + b"\n"
        if command == EVIDENCE["HEAD"]:
            return (self.paths[checkout] + "\n").encode()
        if command == EVIDENCE["STATUS"]:
            return b"dirty" if self.dirty or (self.post_dirty and self.calls) else b""
        raise AssertionError("unexpected Git command")

    def reactor(self, command, checkout, environment, *, edit=None):
        self.calls.append((command, checkout, environment))
        nonce = next(part.split("=", 1)[1] for part in command if part.startswith("-D" + GATE["NONCE_KEY"] + "="))
        for index, (module, relative) in enumerate(GATE["OWNERS"]):
            name = GATE["PREFIX"] + relative
            identity = f"{name}({nonce})"
            count = COUNTS[index]
            suite = ElementTree.Element("testsuite", name=identity, tests=str(count), failures="0", errors="0", skipped="0")
            props = ElementTree.SubElement(suite, "properties")
            fields = {**GATE["FIXED_PROPERTIES"], GATE["NONCE_KEY"]: nonce, "surefire.reportNameSuffix": nonce,
                      "java.specification.version": "25", "test": ",".join(GATE["SELECTORS"]),
                      "basedir": "/.." + str(checkout / module), "private-property": CANARY}
            for key, value in fields.items():
                ElementTree.SubElement(props, "property", name=key, value=value)
            for method in range(count):
                case = "governed_process_injection_corpus" if relative in GATE["DYNAMIC_TEST_ROWS"] and method < 6 else f"fixture{method}"
                ElementTree.SubElement(suite, "testcase", name=case, classname=identity)
            ElementTree.SubElement(suite, "system-out").text = CANARY
            path = GATE["report_path"](checkout, module, name, nonce)
            path.parent.mkdir(parents=True, exist_ok=True)
            if index == 0 and edit is not None:
                edit(suite, props, path)
            else:
                path.write_bytes(ElementTree.tostring(suite, encoding="utf-8"))
                path.chmod(0o600)
        return 0

    def run(self, *, reactor=None, extra=()):
        stdout, stderr = io.BytesIO(), io.BytesIO()
        code = GATE["run"]([*self.args, *extra], stdout, stderr, git_runner=self.git, reactor=reactor or self.reactor)
        return code, stdout.getvalue(), stderr.getvalue()


class LocalReleaseUGateTest(unittest.TestCase):
    def setUp(self):
        self.private = tempfile.TemporaryDirectory()
        self.addCleanup(self.private.cleanup)
        self.fixture = Fixture(Path(self.private.name).resolve())

    def test_binds_exact_fixed_selectors_nonce_and_redacted_17_report_summary(self):
        fixture = self.fixture
        code, output, errors = fixture.run()
        self.assertEqual((code, errors), (0, b""))
        summary = json.loads(output)
        self.assertEqual((summary["classes"], summary["tests"]), (17, 220))
        self.assertEqual(summary["sourceHeads"], fixture.heads)
        self.assertNotIn(CANARY.encode(), output)
        self.assertNotIn(os.fsencode(fixture.root), output)
        self.assertEqual(len(fixture.calls), 1)
        command, checkout, env = fixture.calls[0]
        self.assertEqual(checkout, fixture.checkout)
        self.assertEqual(command[:8], (str(checkout / "scripts/verify-source-reactor.sh"), "-gs", str(fixture.settings),
                                      "-s", str(fixture.settings), "-o", "-B", "-ntp"))
        self.assertIn("-Dtest=" + ",".join(GATE["SELECTORS"]), command)
        self.assertIn("-D" + GATE["NONCE_KEY"] + "=" + summary["runId"], command)
        self.assertIn("-Dsurefire.reportNameSuffix=" + summary["runId"], command)
        self.assertNotIn("MAVEN_OPTS", env)
        self.assertNotIn("JAVA_TOOL_OPTIONS", env)
        self.assertEqual(env["ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS"], "false")
        self.assertTrue(all(set(item) == {"module", "className", "tests", "sha256"} for item in summary["reports"]))

    def test_rejects_maven_passthrough_and_dirty_checkout_before_reactor(self):
        fixture = self.fixture
        self.assertEqual(fixture.run(extra=("-Dsecret", CANARY)), (64, b"", b"TIANJI_LOCAL_U_USAGE\n"))
        fixture.dirty = True
        self.assertEqual(fixture.run(), (65, b"", b"TIANJI_LOCAL_U_REJECTED\n"))
        self.assertEqual(fixture.calls, [])

    def test_rejects_non_offline_settings_or_missing_wrapper_before_reactor(self):
        fixture = self.fixture
        original = fixture.settings.read_text(encoding="utf-8")
        fixture.settings.write_text(original.replace("</settings>",
            f"<servers><server><password>{CANARY}</password></server></servers></settings>"), encoding="utf-8")
        self.assertEqual(fixture.run(), (65, b"", b"TIANJI_LOCAL_U_REJECTED\n"))
        fixture.settings.write_text(original, encoding="utf-8")
        fixture.cached_maven.unlink()
        self.assertEqual(fixture.run(), (65, b"", b"TIANJI_LOCAL_U_REJECTED\n"))
        self.assertEqual(fixture.calls, [])

    def test_rejects_stale_nonce_wrong_owner_flaky_and_duplicate_property(self):
        def save(suite, path):
            path.write_bytes(ElementTree.tostring(suite, encoding="utf-8"))
            path.chmod(0o600)

        def old_nonce(suite, props, path):
            next(item for item in props if item.get("name") == GATE["NONCE_KEY"]).set("value", "1" * 32)
            save(suite, path)

        def wrong_owner(suite, props, path):
            next(item for item in props if item.get("name") == "basedir").set("value", "/unexpected/owner")
            save(suite, path)

        def flaky(suite, _props, path):
            ElementTree.SubElement(suite.find("testcase"), "flakyFailure").text = CANARY
            save(suite, path)

        def duplicate(suite, props, path):
            ElementTree.SubElement(props, "property", name=GATE["NONCE_KEY"], value=CANARY)
            save(suite, path)

        def repeated_other_case(suite, _props, path):
            cases = suite.findall("testcase")
            cases[1].set("name", cases[0].get("name"))
            save(suite, path)

        fixture = self.fixture
        for edit in (old_nonce, wrong_owner, flaky, duplicate, repeated_other_case):
            with self.subTest(case=edit.__name__):
                self.assertEqual(fixture.run(reactor=lambda *args: fixture.reactor(*args, edit=edit)),
                                 (65, b"", b"TIANJI_LOCAL_U_REJECTED\n"))

    def test_rejects_wrong_sibling_head_or_report_directory_link_before_reactor(self):
        fixture = self.fixture
        sibling = fixture.root / "Stellar-Enclosure-Components"
        previous = fixture.paths[sibling]
        fixture.paths[sibling] = "f" * 40
        self.assertEqual(fixture.run(), (65, b"", b"TIANJI_LOCAL_U_REJECTED\n"))
        fixture.paths[sibling] = previous
        module = fixture.checkout / GATE["OWNERS"][0][0]
        module.mkdir()
        (module / "target").symlink_to(fixture.root, target_is_directory=True)
        self.assertEqual(fixture.run(), (65, b"", b"TIANJI_LOCAL_U_REJECTED\n"))
        self.assertEqual(fixture.calls, [])

    def test_rejects_nonzero_reactor_and_post_run_source_drift_without_output(self):
        fixture = self.fixture
        self.assertEqual(fixture.run(reactor=lambda *_: 1), (1, b"", b"TIANJI_LOCAL_U_REACTOR_FAILED\n"))
        fixture.post_dirty = True
        self.assertEqual(fixture.run(), (65, b"", b"TIANJI_LOCAL_U_REJECTED\n"))


if __name__ == "__main__":
    unittest.main()
