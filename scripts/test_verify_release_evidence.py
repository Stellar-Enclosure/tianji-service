import hashlib
import importlib.util
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree


CHECKER = Path(__file__).with_name("verify-release-evidence.py")
POLICY_PATH = CHECKER.with_name("release-evidence-policy-v1.json")
REVISIONS = {
    "tianjiService": "1" * 40,
    "components": "2" * 40,
    "shoubing": "3" * 40,
    "jianshen": "4" * 40,
    "tianshu": "5" * 40,
}
REPOS = dict(zip(("tianji-service", "Stellar-Enclosure-Components", "tianquan-shoubing-service",
                  "tianquan-jianshen-service", "tianshu-service"), REVISIONS.values()))
AUDIT_FROM = "2026-11-01T00:00:00Z"
START = "2026-11-01T00:00:00.1Z"
OBSERVED = "2026-11-01T00:00:00.2Z"
FINISH = "2026-11-01T00:00:00.3Z"
AUDIT_UNTIL = "2026-11-01T00:00:00.4Z"
NOW_NS = 1793491200 * 1_000_000_000 + 400_000_000
CANDIDATE_FIXTURE = r'''{
  "agent": {
    "authorityState": "DECLARED",
    "definitionArtifactSha256": "180ef94e820f94ab3f25f4755a62d6853c6f72110e21729d681d0522ba66c69d",
    "definitionContentSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "definitionKey": "expenseAnalysis",
    "definitionVersion": 1,
    "inputContract": {
      "artifactSha256": "96dc248fa51f930414675967a0ae710251b251cce4e2c8fdb393b155b45f95ad",
      "symbol": "top.egon.cola.platform.tianji.contract.agent.AgentInputDTO",
      "version": "1"
    },
    "modelRouteRef": "test-model-🛠",
    "optionalDependencyState": "NOT_REQUIRED",
    "outputContract": {
      "artifactSha256": "43a33a0d21ebd96016bbcf35d4ea8f5b828fbed70629be32a4d086b2e5201897",
      "symbol": "top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult",
      "version": "1"
    },
    "ownerDigestState": "DECLARED",
    "policyVersionRef": "v1"
  },
  "agentLink": {
    "agentDefinitionContentSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "agentDefinitionKey": "expenseAnalysis",
    "agentDefinitionVersion": 1,
    "processDefinitionKey": "expenseReview",
    "schemaVersion": "1",
    "state": "ARTIFACT_CHECKED",
    "topic": "agentStart",
    "workerElementId": "agentStart"
  },
  "channel": "expense",
  "packages": {
    "agent": {
      "archiveSha256": "d3e3ef40f8d7459b783919cb2d708cf474375ea74513d62920951f17246d2ad2",
      "manifestEntrySha256": "570e50b4f113cecd89f5ba0272da9fc1905f5e21c483521f21f9bc7cfde98867",
      "producerId": "tianji-agent-java",
      "state": "ARTIFACT_CHECKED"
    },
    "process": {
      "archiveSha256": "91f93c0252a13454ddb0de7ed175358158c9dc99f15fd6796523c369f25e4f43",
      "manifestEntrySha256": "808e156db1757ba73f0362d5bd564b05535ff4245dd42615010df58b8e1c56c5",
      "producerId": "tianji-process-java",
      "state": "ARTIFACT_CHECKED"
    }
  },
  "pendingAuthority": [
    "RELEASE_SIGNING",
    "OWNER_PUBLICATION",
    "LIVE_REVOCATION",
    "REVIEW_AND_ACTIVATION"
  ],
  "process": {
    "manifestSha256": "f65fc8ac24ff2683ab3c3645e4fb60f7003666380767014df51bea10d96b24f5",
    "models": [
      {
        "definitionKey": "expenseReview",
        "inputs": {
          "amount": "INTEGER",
          "operationId": "STRING"
        },
        "modelSha256": "5957151c2760507749f0b68763863dde4e42466261e63de10627e1d48ed1c1b6",
        "type": "BPMN"
      },
      {
        "definitionKey": "expenseException",
        "inputs": {},
        "modelSha256": "c2bc0015fcc709b1bd568b635737b4fc7feff80fe343622a9efdc1c9a02f49c1",
        "type": "CMMN"
      },
      {
        "definitionKey": "expenseRouting",
        "inputs": {
          "amount": "INTEGER"
        },
        "modelSha256": "94cbd131a086c5e2a87240e99e0c7c88b267d946c184e5c7c1f3bfd694feba74",
        "type": "DMN"
      }
    ],
    "releaseVersion": "expense-v1",
    "state": "ARTIFACT_CHECKED"
  },
  "schemaVersion": 1,
  "sourceRevision": "987c7a8769b1f2e92623835ada174cc57c514c63",
  "status": "ARTIFACT_CHECKED_ONLY",
  "tenantId": "20"
}'''


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def load_checker():
    spec = importlib.util.spec_from_file_location("t405_evidence_checker", CHECKER)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class Fixture:
    def __init__(self, base):
        self.base = base.resolve()
        self.root = self.base / "stage"
        self.root.mkdir(mode=0o700)
        for name in REPOS:
            (self.base / name).mkdir()
        self.checkout = self.base / "tianji-service"
        self.policy_bytes = POLICY_PATH.read_bytes()
        self.policy = json.loads(self.policy_bytes)
        self.candidate = json.loads(CANDIDATE_FIXTURE)
        self.candidate["sourceRevision"] = REVISIONS["tianjiService"]
        archives = {role: self.put(role + ".jar", (role + " synthetic archive").encode()) for role in ("agent", "process")}
        self.archive_shas = {role: ref["sha256"] for role, ref in archives.items()}
        for role, digest in self.archive_shas.items():
            self.candidate["packages"][role]["archiveSha256"] = digest
        self.candidate_sha = sha(b"TIANJI_PLATFORM_RELEASE_ARTIFACT_V1\0" + canonical(self.candidate))
        candidate_ref = self.put_json("candidate.json", {"candidateSha256": self.candidate_sha, "candidate": self.candidate})
        self.index = {
            "schemaVersion": 1, "profileId": self.policy["profileId"], "policySha256": sha(self.policy_bytes),
            "candidate": candidate_ref, "revisions": dict(REVISIONS), "archives": archives,
            "testClasses": {"agent": "a" * 64, "process": "b" * 64}, "compileProofs": [], "runs": [],
        }
        self.git_calls = []
        self.dirty_repo = None
        self.signature_pin = None
        for group in self.policy["groups"]:
            self.index["runs"].append(self.make_run(group))

    def put(self, path, raw):
        target = self.root / path
        target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        target.write_bytes(raw)
        target.chmod(0o600)
        return {"path": path, "sha256": sha(raw)}

    def put_json(self, path, payload):
        return self.put(path, canonical(payload) + b"\n")

    def make_run(self, group):
        identity = group["id"]
        selectors = [report["selector"] for report in group["reports"]]
        reports = [self.report(report, selectors) for report in group["reports"]]
        run = {
            "groupId": identity, "runId": "run-" + identity.lower(), "level": group["level"],
            "startedAt": START, "finishedAt": FINISH, "exitCode": 0, "candidateSha256": self.candidate_sha,
            "revisions": dict(REVISIONS), "archiveShas": self.archive_shas.copy(), "selectors": selectors, "reports": reports,
        }
        if identity == "U_SOURCE":
            run["environment"] = {"kind": "OFFLINE_FAKE"}
        elif identity == "I_CASE":
            run["environment"] = {"kind": "ISOLATED_NATIVE_PG", "postgresVersion": "18.1",
                                  "flowableVersion": "8.0.0", "environmentSha256": "d" * 64}
        else:
            suite = identity.removeprefix("E_")
            run["environment"] = {"kind": "EXPENSE_ISOLATED_E",
                                  "manifest": self.put_json(suite + "-manifest.json", self.manifest(run["runId"], suite)),
                                  "witness": self.put_json(suite + "-witness.json", self.witness(run["runId"], suite))}
        return run

    def report(self, owner, selectors, *, event=None, zero=False, module=None, testcase_class=None):
        names = owner["methods"] or [f"fixtureCase{index}" for index in range(owner["minimumTests"])]
        if zero:
            names = []
        testsuite = ElementTree.Element("testsuite", {
            "name": owner["className"], "tests": str(len(names)), "failures": str(int(event == "failure")),
            "errors": str(int(event == "error")), "skipped": str(int(event == "skipped")),
        })
        props = ElementTree.SubElement(testsuite, "properties")
        ElementTree.SubElement(props, "property", name="test", value=",".join(selectors))
        ElementTree.SubElement(props, "property", name="basedir", value="/private-reactor/../" + (module or owner["module"]))
        ElementTree.SubElement(props, "property", name="test.secret", value="never-output-this-secret")
        for index, name in enumerate(names):
            case = ElementTree.SubElement(testsuite, "testcase", classname=testcase_class or owner["className"], name=name)
            if event is not None and index == 0:
                ElementTree.SubElement(case, event).text = "never-output-this-diagnostic"
        file_ref = self.put("reports/TEST-" + owner["className"] + ".xml", ElementTree.tostring(testsuite, encoding="utf-8"))
        return {"module": owner["module"], "className": owner["className"], "file": file_ref}

    def manifest(self, run_id, suite):
        owners = {"AGENT": "tianji_agent", "PROCESS": "tianji_process", "BUSINESS": "tianji_worker"}
        stores = []
        for number, (owner, schema) in enumerate(owners.items(), 1):
            stem = f"tianji_it_{suite.lower()}_{owner.lower()}"
            stores.append({
                "owner": owner, "jdbcUrl": f"jdbc:postgresql://127.0.0.1:{5400 + number}/{stem}",
                "serverIdentity": "isolated-fixture", "schema": schema, "postgresVersion": "18.1",
                "pgvectorVersion": "0.8.1" if owner == "AGENT" else "NONE", "historySha256": "e" * 64,
                "setupRole": stem + "_setup", "runtimeRole": stem + "_runtime", "observerRole": stem + "_observer",
                "credentialFile": "/intentionally-not-created/" + stem,
            })
        tenants = [{
            "tenantId": tenant, "issuer": f"https://127.0.0.1:9/{tenant}", "ownerSubject": "same-owner",
            "reviewerSubjects": ["reviewer-a", "reviewer-b"], "roleIds": ["1", "2", "3"], "applicationId": "1",
            "processResource": "https://127.0.0.1:9/process", "agentResource": "https://127.0.0.1:9/agent",
            "credentialFile": "/intentionally-not-created/tenant-" + str(tenant),
        } for tenant in (20, 21)]
        return {
            "schemaVersion": 1, "runId": run_id, "runAuthorized": True, "suite": suite,
            "checkoutRoot": "/ignored/original/checkout", "privateRoot": "/ignored/original/private",
            "outputDirectory": "/ignored/original/private/output", "fixtureRoot": "/ignored/original/fixture",
            "revisions": dict(REVISIONS), "tenants": tenants, "stores": stores,
            "tls": dict.fromkeys(("trustStore", "agentKeyStore", "processKeyStore", "relayKeyStore", "credentialFile"),
                                 "/intentionally-not-created/tls"),
            "endpoints": {key: f"https://127.0.0.1:{8000 + index}" for index, key in enumerate(
                ("agent", "process", "linkedRelay", "resultRelay", "workerRelay", "collector"))},
            "limits": {"readinessSeconds": 30, "pathSeconds": 30, "manualSeconds": 60, "recoverySeconds": 90, "linkedHoldSeconds": 5},
            **{role: {"execJar": "/ignored/" + role + ".jar", "execJarSha256": self.archive_shas[role],
                      "testClasses": "/ignored/" + role + "-tests", "testClassesSha256": self.index["testClasses"][role]}
               for role in ("agent", "process")},
        }

    def witness(self, run_id, suite):
        paths = []
        all_witnesses = set(self.policy["baseWitnesses"])
        for rule in self.policy["expensePaths"].values():
            all_witnesses.update(rule["extraWitnesses"])
        for scenario, rule in self.policy["expensePaths"].items():
            if rule["suite"] != suite:
                continue
            required = set(self.policy["baseWitnesses"]) | set(rule["extraWitnesses"])
            for tenant in (20, 21):
                witness = {
                    kind: {"state": "PROVED", "reference": f"{scenario}-{kind}-{tenant}"}
                    if kind in required else {"state": "NOT_REACHED", "reference": None}
                    for kind in all_witnesses
                }
                for kind, field in (("DECISION_RULE", "ruleId"), ("AGENT_STATUS", "agentStatus"), ("PROCESS_END", "processEnd")):
                    witness[kind]["reference"] = rule[field]
                paths.append({"tenantId": tenant, "scenario": scenario, "operationId": "operation-" + scenario,
                              "businessKey": "business-" + scenario, "correlationId": "correlation-" + scenario,
                              "passed": True, "witnesses": witness})
        children = []
        for number, role in enumerate(("AGENT", "PROCESS"), 1):
            for generation in range(1, 3 if suite == "RECOVERY" else 2):
                children.append({"role": role, "generation": generation,
                                 "pid": (100 if suite == "BUSINESS" else 200) + number * 10 + generation,
                                 "startedAt": START if generation == 1 else "2026-11-01T00:00:00.15Z"})
        return {"schemaVersion": 1, "runId": run_id, "suite": suite, "observedAt": OBSERVED, "passed": True,
                "paths": paths, "children": children, "exits": [{"child": child, "exitCode": 143} for child in children]}

    def git(self, root, command):
        self.git_calls.append((root, command))
        if command == ("rev-parse", "--show-toplevel"):
            return (str(root) + "\n").encode()
        if command == ("rev-parse", "--verify", "HEAD^{commit}"):
            return (REPOS[root.name] + "\n").encode()
        if command == ("status", "--porcelain=v1", "-z", "--untracked-files=all", "--ignore-submodules=none"):
            return b"?? private-dirty-path\0" if root.name == self.dirty_repo else b""
        raise AssertionError(command)

    def verify(self, *, mutate_args=None, now=lambda: NOW_NS, stdout=None):
        self.put_json("index.json", self.index)
        argv = ["verify", "--root", str(self.root), "--index", "index.json", "--checkout", str(self.checkout),
                "--expect-revision", REVISIONS["tianjiService"], "--expect-candidate-sha256", self.candidate_sha,
                "--audit-from", AUDIT_FROM, "--audit-until", AUDIT_UNTIL]
        if self.signature_pin is not None:
            argv.extend(("--expect-signature-proof-sha256", self.signature_pin))
        if mutate_args is not None:
            mutate_args(argv)
        output, error = io.BytesIO() if stdout is None else stdout, io.BytesIO()
        status = load_checker().run(argv, output, error, now_ns=now, git_runner=self.git)
        return status, output.getvalue(), error.getvalue()


class EvidenceGateTest(unittest.TestCase):
    def fixture(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        return Fixture(Path(temporary.name))

    def test_cli_entrypoint_exists(self):
        self.assertTrue(CHECKER.is_file())

    def test_valid_bundle_is_scoped_ready(self):
        fixture = self.fixture()
        status, output, error = fixture.verify()
        self.assertEqual(0, status)
        self.assertEqual(b"", error)
        gates = [
            {"id": identity, "level": identity[0], "status": "PASS", "reason": "VERIFIED",
             "tests": count, "executed": count, "failures": 0, "errors": 0, "skipped": 0}
            for identity, count in (("U_SOURCE", 183), ("I_CASE", 1), ("E_BUSINESS", 3), ("E_RECOVERY", 1))
        ]
        expected = {
            "schemaVersion": 1, "profileId": "expense-release-evidence-v1", "policySha256": sha(fixture.policy_bytes),
            "candidateSha256": fixture.candidate_sha, "sourceRevision": REVISIONS["tianjiService"],
            "archiveShas": fixture.archive_shas, "auditWindow": {"fromInclusive": AUDIT_FROM, "untilExclusive": AUDIT_UNTIL},
            "signatureProof": "NOT_SUPPLIED", "verdict": "EXPENSE_EVIDENCE_READY_FOR_REVIEW", "gates": gates,
        }
        self.assertEqual(canonical(expected) + b"\n", output)

    def assert_refused(self, result, status=65, code="INVALID"):
        self.assertEqual((status, b"", f"TIANJI_RELEASE_EVIDENCE_{code}\n".encode()), result)

    def summary(self, fixture, expected=1):
        status, stdout, stderr = fixture.verify()
        self.assertEqual(expected, status)
        self.assertEqual(b"", stderr)
        self.assertNotIn(b"never-output", stdout)
        self.assertNotIn(b"intentionally-not-created", stdout)
        return json.loads(stdout)

    def assert_gate(self, fixture, identity, status, reason):
        result = self.summary(fixture)
        self.assertEqual("BLOCKED", result["verdict"])
        gate = next(row for row in result["gates"] if row["id"] == identity)
        self.assertEqual((status, reason), (gate["status"], gate["reason"]))
        return gate

    def test_rejects_unsafe_or_malformed_evidence(self):
        fixture = self.fixture()
        self.assert_refused(fixture.verify(mutate_args=lambda a: a.extend(("--root", str(fixture.root)))), 64, "USAGE")
        self.assert_refused(fixture.verify(mutate_args=lambda a: a.__setitem__(4, "../index.json")))
        self.assert_refused(fixture.verify(mutate_args=lambda a: a.__setitem__(2, str(fixture.root) + "/.")))
        self.assert_refused(fixture.verify(mutate_args=lambda a: a.__setitem__(2, str(fixture.root) + "\0")))
        alias = fixture.base / "alias"
        alias.symlink_to(fixture.root, target_is_directory=True)
        self.assert_refused(fixture.verify(mutate_args=lambda a: a.__setitem__(2, str(alias))))
        actual_archive = fixture.root / "agent.jar"
        hardlink = fixture.root / "shared.jar"
        hardlink.hardlink_to(actual_archive)
        process = fixture.index["archives"]["process"]
        fixture.index["archives"]["process"] = {"path": hardlink.name, "sha256": sha(actual_archive.read_bytes())}
        self.assert_refused(fixture.verify())
        fixture.index["archives"]["process"] = process
        actual_archive.chmod(0o620)
        self.assert_refused(fixture.verify())
        actual_archive.chmod(0o600)
        staged_candidate = fixture.index["candidate"]
        fixture.index["candidate"] = fixture.put("bad-candidate.json", b"\xef\xbb\xbf{}")
        self.assert_refused(fixture.verify())
        fixture.index["candidate"] = fixture.put("bad-candidate.json", b'{"candidate":{},"candidate":{}}')
        self.assert_refused(fixture.verify())
        fixture.index["candidate"] = fixture.put("bad-candidate.json", b" " * (512 * 1024 + 1))
        self.assert_refused(fixture.verify())
        fixture.index["candidate"] = staged_candidate

        run = fixture.index["runs"][0]
        report = run["reports"][0]
        original_ref = report["file"]
        old = (fixture.root / original_ref["path"]).read_bytes()
        report["file"] = fixture.put(original_ref["path"], b'<!DOCTYPE testsuite [<!ENTITY payload "x">]>' + old)
        self.assert_gate(fixture, "U_SOURCE", "INVALID_EVIDENCE", "INVALID_REPORT")
        report["file"] = fixture.put(original_ref["path"], b"\xff")
        self.assert_gate(fixture, "U_SOURCE", "INVALID_EVIDENCE", "INVALID_REPORT")
        report["file"] = fixture.put(original_ref["path"], old)
        run["reports"][0] = {**report, "module": "stellar-enclosure-tianji-process-server"}
        self.assert_gate(fixture, "U_SOURCE", "INVALID_EVIDENCE", "INVALID_REPORT")
        run["reports"][0] = report
        run["reports"][1] = report
        self.assert_gate(fixture, "U_SOURCE", "INVALID_EVIDENCE", "INVALID_REPORT")

        class BrokenOutput(io.BytesIO):
            def write(self, raw):
                raise OSError("never-output-private-error")

        self.assert_refused(fixture.verify(stdout=BrokenOutput()), 66, "IO_UNAVAILABLE")

    def test_blocks_unexecuted_and_compiled_only(self):
        fixture = self.fixture()
        fixture.index["runs"] = fixture.index["runs"][:1]
        for group in fixture.policy["groups"][2:]:
            owner = group["reports"][0]
            artifact = fixture.put(owner["selector"] + ".class", b"\xca\xfe\xba\xbe\x00\x00\x00\x45\x00\x01")
            fixture.index["compileProofs"].append({
                "module": owner["module"], "className": owner["className"], "classFile": artifact,
                "candidateSha256": fixture.candidate_sha, "revisions": dict(REVISIONS),
                "archiveShas": fixture.archive_shas.copy(), "capturedAt": FINISH,
            })
        summary = self.summary(fixture)
        self.assertEqual(["PASS", "NOT_EXECUTED", "COMPILED_ONLY", "COMPILED_ONLY"],
                         [row["status"] for row in summary["gates"]])
        self.assertIsNone(summary["gates"][2]["executed"])
        fixture.index["compileProofs"][0]["capturedAt"] = AUDIT_UNTIL
        self.assert_gate(fixture, "E_BUSINESS", "STALE", "AUDIT_WINDOW_MISS")
        proof = fixture.index["compileProofs"][1]
        proof["classFile"] = fixture.put(proof["classFile"]["path"], b"compiled-looking-without-magic")
        self.assert_gate(fixture, "E_RECOVERY", "INVALID_EVIDENCE", "INVALID_COMPILE_PROOF")

    def test_classifies_actual_reports_and_audit_window(self):
        fixture = self.fixture()
        run = fixture.index["runs"][1]
        owner = fixture.policy["groups"][1]["reports"][0]
        for node, status, reason in (
            ("failure", "FAILED", "TEST_FAILURE"), ("error", "FAILED", "TEST_FAILURE"),
            ("flakyFailure", "FAILED", "TEST_FAILURE"), ("rerunError", "FAILED", "TEST_FAILURE"),
            ("skipped", "SKIPPED", "TEST_SKIPPED"),
        ):
            with self.subTest(node=node):
                run["environment"] = None
                run["reports"] = [fixture.report(owner, run["selectors"], event=node)]
                self.assert_gate(fixture, "I_CASE", status, reason)
        run["reports"] = [fixture.report(owner, run["selectors"], zero=True)]
        gate = self.assert_gate(fixture, "I_CASE", "NOT_EXECUTED", "NO_EXECUTED_TESTS")
        self.assertEqual(0, gate["executed"])
        run["exitCode"] = 1
        self.assert_gate(fixture, "I_CASE", "FAILED", "RUN_EXIT_NONZERO")
        run["exitCode"] = 0
        run["reports"] = [fixture.report(owner, run["selectors"])]
        self.assert_gate(fixture, "I_CASE", "INVALID_EVIDENCE", "INVALID_ENVIRONMENT")
        run["environment"] = {"kind": "ISOLATED_NATIVE_PG", "postgresVersion": "18.2",
                              "flowableVersion": "8.0.0", "environmentSha256": "d" * 64}
        run["startedAt"] = run["finishedAt"] = AUDIT_FROM
        self.summary(fixture, expected=0)
        run["finishedAt"] = "2026-11-01T00:00:00.399999999Z"
        self.summary(fixture, expected=0)
        run["finishedAt"] = AUDIT_UNTIL
        self.assert_gate(fixture, "I_CASE", "STALE", "AUDIT_WINDOW_MISS")
        run["startedAt"] = run["finishedAt"] = "2026-10-31T23:59:59.999999999Z"
        self.assert_gate(fixture, "I_CASE", "STALE", "AUDIT_WINDOW_MISS")
        run["startedAt"], run["finishedAt"] = START, FINISH
        run["candidateSha256"] = "0" * 64
        self.assert_gate(fixture, "I_CASE", "STALE", "SOURCE_LOCK_MISMATCH")
        self.assert_refused(fixture.verify(now=lambda: NOW_NS - 1), 64, "USAGE")

        u = fixture.index["runs"][0]
        u["reports"].pop()
        self.assert_gate(fixture, "U_SOURCE", "NOT_EXECUTED", "REQUIRED_REPORT_MISSING")
        first = fixture.policy["groups"][0]["reports"][0]
        u["reports"][0] = fixture.report(first, u["selectors"], event="failure")
        self.assert_gate(fixture, "U_SOURCE", "FAILED", "TEST_FAILURE")

    def test_checks_exact_expense_witnesses(self):
        fixture = self.fixture()
        business, recovery = fixture.index["runs"][2:]
        recover_witness = recovery["environment"]["witness"]
        recovery["environment"]["witness"] = None
        self.assert_gate(fixture, "E_RECOVERY", "INVALID_EVIDENCE", "WITNESS_MISSING")
        recovery["environment"]["witness"] = recover_witness

        witness = fixture.witness(recovery["runId"], "RECOVERY")
        witness["paths"][0]["witnesses"]["EARLY_INBOX_PENDING"] = {"state": "PENDING", "reference": None}
        recovery["environment"]["witness"] = fixture.put_json(recover_witness["path"], witness)
        self.assert_gate(fixture, "E_RECOVERY", "INVALID_EVIDENCE", "INVALID_WITNESS")
        witness = fixture.witness(recovery["runId"], "RECOVERY")
        witness["paths"][0]["witnesses"]["ORIGINAL_RECEIPT"] = witness["paths"][1]["witnesses"]["ORIGINAL_RECEIPT"]
        recovery["environment"]["witness"] = fixture.put_json(recover_witness["path"], witness)
        self.assert_gate(fixture, "E_RECOVERY", "INVALID_EVIDENCE", "INVALID_WITNESS")
        witness = fixture.witness(recovery["runId"], "RECOVERY")
        witness["exits"].pop()
        recovery["environment"]["witness"] = fixture.put_json(recover_witness["path"], witness)
        self.assert_gate(fixture, "E_RECOVERY", "INVALID_EVIDENCE", "INVALID_WITNESS")
        witness = fixture.witness(recovery["runId"], "RECOVERY")
        witness["passed"] = False
        recovery["environment"]["witness"] = fixture.put_json(recover_witness["path"], witness)
        self.assert_gate(fixture, "E_RECOVERY", "FAILED", "WITNESS_FAILED")
        recovery["environment"]["witness"] = fixture.put_json(recover_witness["path"], fixture.witness(recovery["runId"], "RECOVERY"))

        manifest = fixture.manifest(recovery["runId"], "RECOVERY")
        manifest["stores"] = fixture.manifest(business["runId"], "BUSINESS")["stores"]
        recovery["environment"]["manifest"] = fixture.put_json("RECOVERY-manifest.json", manifest)
        self.assert_gate(fixture, "E_RECOVERY", "INVALID_EVIDENCE", "INVALID_ENVIRONMENT")
        self.assert_gate(fixture, "E_BUSINESS", "INVALID_EVIDENCE", "INVALID_ENVIRONMENT")
        manifest = fixture.manifest(recovery["runId"], "RECOVERY")
        manifest["agent"]["testClassesSha256"] = "c" * 64
        recovery["environment"]["manifest"] = fixture.put_json("RECOVERY-manifest.json", manifest)
        self.assert_gate(fixture, "E_RECOVERY", "STALE", "SOURCE_LOCK_MISMATCH")

    def test_binds_five_repositories_candidate_and_optional_a2(self):
        fixture = self.fixture()
        self.assertEqual("NOT_SUPPLIED", self.summary(fixture, expected=0)["signatureProof"])
        self.assertEqual(set(REPOS), {root.name for root, _ in fixture.git_calls})
        self.assertEqual(15, len(fixture.git_calls))
        fixture.dirty_repo = "tianshu-service"
        self.assert_refused(fixture.verify())
        fixture.dirty_repo = None
        fixture.index["revisions"]["jianshen"] = "0" * 40
        self.assert_refused(fixture.verify())
        fixture.index["revisions"] = dict(REVISIONS)
        fixture.index["archives"]["agent"]["sha256"] = "f" * 64
        self.assert_refused(fixture.verify())
        fixture.index["archives"]["agent"]["sha256"] = fixture.archive_shas["agent"]
        self.assert_refused(fixture.verify(mutate_args=lambda a: a.__setitem__(8, "f" * 40)))

        proof = {
            "schemaVersion": 1, "status": "ARTIFACT_SIGNATURE_VERIFIED_OFFLINE", "candidateSha256": fixture.candidate_sha,
            "candidateEnvelopeSha256": fixture.index["candidate"]["sha256"], "sourceRevision": REVISIONS["tianjiService"],
            "tenantId": fixture.candidate["tenantId"], "channel": fixture.candidate["channel"],
            "keyId": "a" * 64, "role": "RELEASE_ARTIFACT_SIGNER", "purpose": "RELEASE_CANDIDATE_ARTIFACT",
            "trustSha256": "b" * 64, "issuedAt": "2020-01-01T00:00:00Z", "expiresAt": "2021-01-01T00:00:00Z",
            "pendingAuthority": ["CURRENT_SIGNER_AUTHORITY", "OWNER_PUBLICATION", "LIVE_REVOCATION", "REVIEW_AND_ACTIVATION"],
        }
        fixture.index["signatureProof"] = fixture.put_json("a2-proof.json", proof)
        self.assert_refused(fixture.verify())
        fixture.signature_pin = "0" * 64
        self.assert_refused(fixture.verify())
        fixture.signature_pin = fixture.index["signatureProof"]["sha256"]
        self.assertEqual("REFERENCE_MATCHED", self.summary(fixture, expected=0)["signatureProof"])
        proof["candidateEnvelopeSha256"] = "0" * 64
        fixture.index["signatureProof"] = fixture.put_json("a2-proof.json", proof)
        fixture.signature_pin = fixture.index["signatureProof"]["sha256"]
        self.assert_refused(fixture.verify())
