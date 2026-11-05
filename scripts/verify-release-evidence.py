"""Verify a private expense evidence bundle using Python 3.11+ on POSIX."""

import calendar
import errno
import hashlib
import json
import os
import re
import selectors
import shutil
import stat
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path
from xml.etree import ElementTree


REQUIRED_FLAGS = {
    "--root", "--index", "--checkout", "--expect-revision", "--expect-candidate-sha256",
    "--audit-from", "--audit-until",
}
OPTIONAL_FLAG = "--expect-signature-proof-sha256"
PROFILE_ID = "expense-release-evidence-v1"
GROUP_LEVELS = {"U_SOURCE": "U", "I_CASE": "I", "E_BUSINESS": "E", "E_RECOVERY": "E"}
REPOSITORIES = {
    "tianjiService": None, "components": "Stellar-Enclosure-Components", "shoubing": "tianquan-shoubing-service",
    "jianshen": "tianquan-jianshen-service", "tianshu": "tianshu-service",
}
ARCHIVE_ROLES = ("agent", "process")
POLICY_PATH = Path(__file__).with_name("release-evidence-policy-v1.json")
JSON_LIMIT = 512 * 1024
XML_LIMIT = 2 * 1024 * 1024
ALL_XML_LIMIT = 32 * 1024 * 1024
ARCHIVE_LIMIT = 512 * 1024 * 1024
CLASS_LIMIT = 2 * 1024 * 1024
OUTPUT_LIMIT = 32 * 1024
UTC = re.compile(r"([0-9]{4})-([0-9]{2})-([0-9]{2})T([0-9]{2}):([0-9]{2}):([0-9]{2})(?:\.([0-9]{1,9}))?Z")
SAFE_REFERENCE = r"[A-Za-z0-9][A-Za-z0-9_.:@-]{0,254}"
SAFE_PART = r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}"
ARTIFACT_PENDING = ["RELEASE_SIGNING", "OWNER_PUBLICATION", "LIVE_REVOCATION", "REVIEW_AND_ACTIVATION"]
SIGNATURE_PENDING = ["CURRENT_SIGNER_AUTHORITY", "OWNER_PUBLICATION", "LIVE_REVOCATION", "REVIEW_AND_ACTIVATION"]
COUNT_FIELDS = ("tests", "executed", "failures", "errors", "skipped")
HEAD = ("rev-parse", "--verify", "HEAD^{commit}")
TOP = ("rev-parse", "--show-toplevel")
STATUS = ("status", "--porcelain=v1", "-z", "--untracked-files=all", "--ignore-submodules=none")


class UsageError(Exception):
    pass


class InvalidEvidence(Exception):
    pass


class Unavailable(Exception):
    pass


def require(condition):
    if not condition:
        raise InvalidEvidence


def shape(value, fields, optional=()):
    expected = set(fields)
    require(type(value) is dict and expected <= value.keys() <= expected | set(optional))
    return value


def integer(value, low=0, high=(1 << 63) - 1):
    require(type(value) is int and low <= value <= high)
    return value


def text(value, pattern=None, limit=4096):
    require(type(value) is str and 0 < len(value) <= limit and value.strip() == value)
    require(pattern is None or re.fullmatch(pattern, value) is not None)
    return value


def sha_text(value):
    return text(value, r"[0-9a-f]{64}")


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def array(value, maximum):
    require(type(value) is list and len(value) <= maximum)
    return value


def bool_value(value):
    require(type(value) is bool)
    return value


def unique(values):
    require(len(values) == len(set(values)))
    return set(values)


def exact_strings(values, maximum, pattern=SAFE_REFERENCE):
    return [text(value, pattern) for value in array(values, maximum)]


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")


def strict_json(raw):
    def members(pairs):
        values = dict(pairs)
        require(len(values) == len(pairs))
        return values

    def invalid_number(_raw):
        raise InvalidEvidence

    def tree(value, depth):
        require(depth <= 32)
        if type(value) is dict:
            for key, item in value.items():
                tree(key, depth + 1)
                tree(item, depth + 1)
        elif type(value) is list:
            for item in value:
                tree(item, depth + 1)
        elif type(value) is str:
            require(all(not (ord(char) < 32 or ord(char) == 127 or 0xD800 <= ord(char) <= 0xDFFF) for char in value))
        elif type(value) is int:
            integer(value, -(1 << 63))

    try:
        require(not raw.startswith(b"\xef\xbb\xbf"))
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=members,
                           parse_float=invalid_number, parse_constant=invalid_number)
        tree(value, 0)
        return value
    except (UnicodeError, ValueError, RecursionError):
        raise InvalidEvidence from None


def instant(value, *, seconds_only=False):
    match = UTC.fullmatch(text(value, limit=30))
    require(match is not None and (not seconds_only or match[7] is None))
    try:
        date = datetime(*(int(part) for part in match.groups()[:6]))
    except ValueError:
        raise InvalidEvidence from None
    return calendar.timegm(date.timetuple()) * 1_000_000_000 + int((match[7] or "").ljust(9, "0"))


def parse_argv(argv):
    if not argv or argv[0] != "verify" or len(argv) % 2 == 0:
        raise UsageError
    values = {}
    for flag, value in zip(argv[1::2], argv[2::2]):
        if flag not in REQUIRED_FLAGS | {OPTIONAL_FLAG} or flag in values or not value or value.startswith("--"):
            raise UsageError
        values[flag] = value
    if REQUIRED_FLAGS - values.keys():
        raise UsageError
    try:
        text(values["--expect-revision"], r"[0-9a-f]{40}")
        sha_text(values["--expect-candidate-sha256"])
        if OPTIONAL_FLAG in values:
            sha_text(values[OPTIONAL_FLAG])
        for flag in ("--audit-from", "--audit-until"):
            instant(values[flag])
    except InvalidEvidence:
        raise UsageError from None
    return values


def canonical_directory(raw):
    require(not any(ord(char) < 32 or ord(char) == 127 or 0xD800 <= ord(char) <= 0xDFFF for char in raw))
    path = Path(raw)
    require(path.is_absolute() and str(path) == raw and ".." not in path.parts and path.root == "/")
    require(path.resolve(strict=True) == path and path.is_dir())
    return path


def path_parts(relative):
    require(type(relative) is str and 0 < len(relative) <= 512)
    parts = relative.split("/")
    require(len(parts) <= 16 and all(re.fullmatch(SAFE_PART, part) for part in parts))
    return parts


def file_ref(value):
    shape(value, ("path", "sha256"))
    path_parts(value["path"])
    sha_text(value["sha256"])
    return value


def file_identity(info):
    return info.st_dev, info.st_ino, info.st_mode, info.st_uid, info.st_size, info.st_mtime_ns, info.st_ctime_ns


class PrivateRoot:
    def __init__(self, path):
        self.root = canonical_directory(path)
        flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
        self.fd = os.open("/", flags)
        try:
            for part in self.root.parts[1:]:
                child = os.open(part, flags, dir_fd=self.fd)
                os.close(self.fd)
                self.fd = child
            metadata = os.fstat(self.fd)
            require(metadata.st_uid == os.geteuid() and metadata.st_mode & 0o077 == 0)
        except (InvalidEvidence, OSError):
            os.close(self.fd)
            raise
        self.paths = set()
        self.inodes = set()
        self.xml_bytes = 0

    def __enter__(self):
        return self

    def __exit__(self, *_exception):
        os.close(self.fd)

    def read(self, relative, limit, *, expected=None, retain=True):
        parts = path_parts(relative)
        require(relative not in self.paths)
        directory = os.dup(self.fd)
        try:
            for part in parts[:-1]:
                child = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=directory)
                os.close(directory)
                directory = child
                metadata = os.fstat(directory)
                require(metadata.st_uid == os.geteuid() and metadata.st_mode & 0o022 == 0)
            return self._read_at(directory, parts[-1], relative, limit, expected, retain)
        except OSError as error:
            if error.errno in (errno.ELOOP, errno.ENOTDIR):
                raise InvalidEvidence from None
            raise
        finally:
            os.close(directory)

    def _read_at(self, directory, name, relative, limit, expected, retain):
        before = os.stat(name, dir_fd=directory, follow_symlinks=False)
        identity = (before.st_dev, before.st_ino)
        require(stat.S_ISREG(before.st_mode) and before.st_uid == os.geteuid()
                and before.st_mode & 0o022 == 0 and 0 < before.st_size <= limit and identity not in self.inodes)
        self.paths.add(relative)
        self.inodes.add(identity)
        handle = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=directory)
        digest = hashlib.sha256()
        data = bytearray()
        size = 0
        with os.fdopen(handle, "rb") as stream:
            require(file_identity(os.fstat(stream.fileno())) == file_identity(before))
            while block := stream.read(64 * 1024):
                size += len(block)
                require(size <= before.st_size)
                digest.update(block)
                if retain:
                    data.extend(block)
            require(file_identity(os.fstat(stream.fileno())) == file_identity(before))
        after = os.stat(name, dir_fd=directory, follow_symlinks=False)
        require(size == before.st_size and file_identity(before) == file_identity(after))
        actual = digest.hexdigest()
        require(expected is None or expected == actual)
        return bytes(data), actual

    def ref(self, reference, limit, *, retain=True):
        value = file_ref(reference)
        return self.read(value["path"], limit, expected=value["sha256"], retain=retain)


def revisions(value):
    shape(value, REPOSITORIES)
    for digest in value.values():
        text(digest, r"[0-9a-f]{40}")
    return value


def archive_shas(value):
    shape(value, ARCHIVE_ROLES)
    for digest in value.values():
        sha_text(digest)
    return value


def run_git(checkout, command):
    require(command in (TOP, HEAD, STATUS))
    git = shutil.which("git", path=os.defpath)
    if git is None:
        raise Unavailable
    env = {
        "PATH": os.defpath, "LC_ALL": "C", "GIT_OPTIONAL_LOCKS": "0", "GIT_TERMINAL_PROMPT": "0",
        "GIT_CONFIG_GLOBAL": os.devnull, "GIT_CONFIG_SYSTEM": os.devnull, "GIT_CONFIG_NOSYSTEM": "1",
    }
    deadline = time.monotonic() + 3
    try:
        with subprocess.Popen(
            (git, "--no-optional-locks", "-c", "core.fsmonitor=false", "-c", "core.untrackedCache=false",
             "-C", str(checkout), *command),
            stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, env=env,
        ) as process, selectors.DefaultSelector() as events:
            data = bytearray()
            try:
                events.register(process.stdout, selectors.EVENT_READ)
                while True:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0 or not events.select(remaining):
                        raise Unavailable
                    block = os.read(process.stdout.fileno(), 4096)
                    if not block:
                        break
                    data.extend(block)
                    if len(data) > 8192:
                        if command == STATUS:
                            return b"dirty"  # Any nonempty status disqualifies this checkout.
                        raise Unavailable
                if process.wait(timeout=max(0, deadline - time.monotonic())) != 0:
                    raise Unavailable
                return bytes(data)
            finally:
                if process.poll() is None:
                    process.kill()
    except (OSError, subprocess.SubprocessError):
        raise Unavailable from None


def verify_checkout(raw, expected, lock, git_runner):
    observed = {}
    checkout = canonical_directory(raw)
    for name, sibling in REPOSITORIES.items():
        path = checkout if sibling is None else canonical_directory(str(checkout.parent / sibling))
        require(git_runner(path, TOP) == os.fsencode(path) + b"\n")
        actual = git_runner(path, HEAD)
        require(re.fullmatch(rb"[0-9a-f]{40}\n", actual) is not None)
        require(git_runner(path, STATUS) == b"")
        observed[name] = actual[:-1].decode("ascii")
    require(expected == observed["tianjiService"] and revisions(lock) == observed)
    return observed


def validate_candidate_payload(candidate):
    shape(candidate, ("schemaVersion", "status", "tenantId", "channel", "sourceRevision", "agent", "process",
                      "agentLink", "packages", "pendingAuthority"))
    require(integer(candidate["schemaVersion"]) == 1 and candidate["status"] == "ARTIFACT_CHECKED_ONLY"
            and candidate["pendingAuthority"] == ARTIFACT_PENDING)
    integer(int(text(candidate["tenantId"], r"[1-9][0-9]{0,18}")), 1)
    text(candidate["channel"], r"[a-z][a-z0-9._-]{0,63}")
    text(candidate["sourceRevision"], r"[0-9a-f]{40}")

    agent = shape(candidate["agent"], ("definitionKey", "definitionVersion", "definitionArtifactSha256",
                  "definitionContentSha256", "ownerDigestState", "inputContract", "outputContract", "modelRouteRef",
                  "policyVersionRef", "authorityState", "optionalDependencyState"))
    text(agent["definitionKey"], SAFE_PART)
    integer(agent["definitionVersion"], 1)
    sha_text(agent["definitionArtifactSha256"])
    sha_text(agent["definitionContentSha256"])
    require(agent["ownerDigestState"] == agent["authorityState"] == "DECLARED"
            and agent["optionalDependencyState"] == "NOT_REQUIRED")
    for key in ("modelRouteRef", "policyVersionRef"):
        text(agent[key], limit=256)
    for role, symbol in (("inputContract", "AgentInputDTO"), ("outputContract", "AgentExecutionResult")):
        contract = shape(agent[role], ("symbol", "version", "artifactSha256"))
        require(contract["symbol"] == "top.egon.cola.platform.tianji.contract.agent." + symbol)
        text(contract["version"], limit=64)
        sha_text(contract["artifactSha256"])

    link = shape(candidate["agentLink"], ("processDefinitionKey", "workerElementId", "topic", "schemaVersion",
                 "agentDefinitionKey", "agentDefinitionVersion", "agentDefinitionContentSha256", "state"))
    process_key = r"[A-Za-z_][A-Za-z0-9_.-]{0,127}"
    for name in ("processDefinitionKey", "workerElementId", "topic"):
        text(link[name], process_key)
    text(link["schemaVersion"], SAFE_PART)
    require(integer(link["agentDefinitionVersion"], 1) == agent["definitionVersion"]
            and link["agentDefinitionKey"] == agent["definitionKey"]
            and link["agentDefinitionContentSha256"] == agent["definitionContentSha256"]
            and link["state"] == "ARTIFACT_CHECKED" and len(link["topic"]) + len(link["schemaVersion"]) + 1 <= 255)

    process = shape(candidate["process"], ("releaseVersion", "manifestSha256", "state", "models"))
    text(process["releaseVersion"], SAFE_PART)
    sha_text(process["manifestSha256"])
    require(process["state"] == "ARTIFACT_CHECKED")
    order = []
    reserved = {"true", "false", "null", "empty", "and", "or", "not", "div", "mod", "eq", "ne", "lt",
                "le", "gt", "ge", "instanceof", "execution", "task", "authenticatedUserId"}
    for model in array(process["models"], 16):
        shape(model, ("type", "definitionKey", "modelSha256", "inputs"))
        require(model["type"] in ("BPMN", "DMN", "CMMN"))
        order.append((model["type"], text(model["definitionKey"], process_key)))
        sha_text(model["modelSha256"])
        inputs = model["inputs"]
        require(type(inputs) is dict and len(inputs) <= 16 and (model["type"] != "CMMN" or not inputs))
        for key, kind in inputs.items():
            text(key, r"[A-Za-z_][A-Za-z0-9_]{0,63}")
            require(key not in reserved and kind in ("BOOLEAN", "INTEGER", "STRING"))
    require(order == sorted(unique(order)) and ("BPMN", link["processDefinitionKey"]) in order)

    packages = shape(candidate["packages"], ARCHIVE_ROLES)
    for role in ARCHIVE_ROLES:
        packaged = shape(packages[role], ("producerId", "archiveSha256", "manifestEntrySha256", "state"))
        require(packaged["producerId"] == "tianji-" + role + "-java" and packaged["state"] == "ARTIFACT_CHECKED")
        sha_text(packaged["archiveSha256"])
        sha_text(packaged["manifestEntrySha256"])


def parse_candidate(root, ref, expected):
    raw, envelope_sha = root.ref(ref, JSON_LIMIT)
    envelope = shape(strict_json(raw), ("candidateSha256", "candidate"))
    candidate = envelope["candidate"]
    validate_candidate_payload(candidate)
    require(raw == canonical(envelope) + b"\n")
    digest = sha(b"TIANJI_PLATFORM_RELEASE_ARTIFACT_V1\0" + canonical(candidate))
    require(expected == sha_text(envelope["candidateSha256"]) == digest)
    return candidate, envelope_sha


def bind_signature_reference(root, index, pin, candidate, candidate_sha, envelope_sha):
    supplied = "signatureProof" in index
    require(supplied == (pin is not None))
    if not supplied:
        return "NOT_SUPPLIED"
    raw, actual = root.ref(index["signatureProof"], JSON_LIMIT)
    require(pin == actual)
    proof = shape(strict_json(raw), ("schemaVersion", "status", "candidateSha256", "candidateEnvelopeSha256",
                  "sourceRevision", "tenantId", "channel", "keyId", "role", "purpose", "trustSha256", "issuedAt",
                  "expiresAt", "pendingAuthority"))
    require(integer(proof["schemaVersion"]) == 1 and proof["status"] == "ARTIFACT_SIGNATURE_VERIFIED_OFFLINE"
            and proof["candidateSha256"] == candidate_sha and proof["candidateEnvelopeSha256"] == envelope_sha
            and all(proof[key] == candidate[key] for key in ("sourceRevision", "tenantId", "channel"))
            and proof["role"] == "RELEASE_ARTIFACT_SIGNER" and proof["purpose"] == "RELEASE_CANDIDATE_ARTIFACT"
            and proof["pendingAuthority"] == SIGNATURE_PENDING)
    sha_text(proof["keyId"])
    sha_text(proof["trustSha256"])
    require(instant(proof["issuedAt"], seconds_only=True) < instant(proof["expiresAt"], seconds_only=True))
    return "REFERENCE_MATCHED"


def read_policy():
    handle = os.open(POLICY_PATH, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(handle, "rb") as stream:
        require(stat.S_ISREG(os.fstat(stream.fileno()).st_mode))
        raw = stream.read(JSON_LIMIT + 1)
    require(len(raw) <= JSON_LIMIT)
    policy = shape(strict_json(raw), ("schemaVersion", "profileId", "windowMode", "groups", "baseWitnesses", "expensePaths"))
    require(integer(policy["schemaVersion"]) == 1 and policy["profileId"] == PROFILE_ID
            and policy["windowMode"] == "DECLARED_UTC_AUDIT_WINDOW")
    groups = array(policy["groups"], 4)
    require(len(groups) == 4)
    for group, (identity, level), expected_count in zip(groups, GROUP_LEVELS.items(), (14, 1, 1, 1)):
        shape(group, ("id", "level", "reports"))
        require(group["id"] == identity and group["level"] == level)
        owners = array(group["reports"], expected_count)
        require(len(owners) == expected_count)
        selectors = []
        for owner in owners:
            shape(owner, ("module", "className", "selector", "minimumTests", "methods"))
            name = text(owner["className"], r"[A-Za-z0-9.]+")
            text(owner["module"], SAFE_PART)
            selector = text(owner["selector"], SAFE_PART)
            require(name.rsplit(".", 1)[-1] == selector)
            selectors.append(selector)
            minimum = integer(owner["minimumTests"], 1, 1000)
            methods = unique(exact_strings(owner["methods"], 3))
            require((not methods and level == "U") or (level != "U" and len(methods) == minimum))
        unique(selectors)
    unique(exact_strings(policy["baseWitnesses"], 11))
    paths = shape(policy["expensePaths"], ("NORMAL", "AGENT_FAILURE", "MANUAL_CASE", "RECOVERY"))
    for scenario, rule in paths.items():
        shape(rule, ("suite", "ruleId", "agentStatus", "processEnd", "extraWitnesses"))
        require(rule["suite"] == ("RECOVERY" if scenario == "RECOVERY" else "BUSINESS"))
        for name in ("ruleId", "agentStatus", "processEnd"):
            text(rule[name], SAFE_REFERENCE)
        unique(exact_strings(rule["extraWitnesses"], 5))
    return policy, sha(raw)


def read_index(root, path, policy, policy_sha):
    raw, _ = root.read(path, JSON_LIMIT)
    index = shape(strict_json(raw), ("schemaVersion", "profileId", "policySha256", "candidate", "revisions",
                  "archives", "testClasses", "compileProofs", "runs"), ("signatureProof",))
    require(integer(index["schemaVersion"]) == 1 and index["profileId"] == PROFILE_ID and index["policySha256"] == policy_sha)
    archive_shas(index["testClasses"])
    shape(index["archives"], ARCHIVE_ROLES)
    runs = {}
    run_ids = set()
    for run in array(index["runs"], 4):
        require(type(run) is dict)
        identity = text(run.get("groupId"), SAFE_REFERENCE)
        run_id = text(run.get("runId"), SAFE_REFERENCE)
        require(identity in GROUP_LEVELS and identity not in runs and run_id not in run_ids)
        runs[identity] = run
        run_ids.add(run_id)
    compiled = {}
    compile_owners = {(g["reports"][0]["module"], g["reports"][0]["className"]): g["id"]
                      for g in policy["groups"] if g["level"] != "U"}
    for proof in array(index["compileProofs"], 3):
        require(type(proof) is dict)
        owner = (text(proof.get("module"), SAFE_PART), text(proof.get("className"), r"[A-Za-z0-9.]+"))
        require(owner in compile_owners and compile_owners[owner] not in compiled)
        compiled[compile_owners[owner]] = proof
    return index, runs, compiled


class ReportTreeBuilder(ElementTree.TreeBuilder):
    def doctype(self, name, public_id, system_id):
        raise InvalidEvidence


def parse_xml(raw):
    try:
        require(not raw.startswith(b"\xef\xbb\xbf"))
        document = raw.decode("utf-8")
        declaration = re.match(r"^<\?xml\s+([^?]+)\?>", document)
        if declaration:
            encoding = re.search(r"""\bencoding\s*=\s*['"]([^'"]+)['"]""", declaration[1])
            require(encoding is None or encoding[1].lower() == "utf-8")
        parser = ElementTree.XMLParser(target=ReportTreeBuilder())
        suite = ElementTree.fromstring(document, parser=parser)
        stack = [(suite, 0)]
        while stack:
            element, depth = stack.pop()
            require(depth <= 16)
            stack.extend((child, depth + 1) for child in element)
        return suite
    except (UnicodeError, ElementTree.ParseError):
        raise InvalidEvidence from None


def summarize_report(root, ref, owner, selectors):
    name = owner["className"]
    require(Path(file_ref(ref)["path"]).name == "TEST-" + name + ".xml")
    raw, _ = root.ref(ref, XML_LIMIT)
    root.xml_bytes += len(raw)
    require(root.xml_bytes <= ALL_XML_LIMIT)
    suite = parse_xml(raw)
    require(suite.tag == "testsuite" and suite.get("name") == name
            and all(child.tag in ("properties", "testcase", "system-out", "system-err") for child in suite))
    props = suite.findall("properties")
    require(len(props) == 1)
    fields = {}
    for key in ("test", "basedir"):
        values = [child.get("value") for child in props[0] if child.tag == "property" and child.get("name") == key]
        require(len(values) == 1 and values[0] is not None)
        fields[key] = values[0]
    test_selectors = fields["test"].split(",")
    require(unique(test_selectors) == selectors)
    basedir = fields["basedir"]
    require(basedir.startswith("/") and "\\" not in basedir
            and Path(os.path.normpath(basedir)).name == owner["module"])

    cases = set()
    counts = dict.fromkeys(COUNT_FIELDS, 0)
    flaky = False
    outcomes = {"failure": "failures", "error": "errors", "skipped": "skipped"}
    retries = ("flakyFailure", "flakyError", "rerunFailure", "rerunError")
    for case in suite.findall("testcase"):
        case_name = text(case.get("name"), limit=4096)
        require(case.get("classname") == name and case_name not in cases)
        cases.add(case_name)
        tags = [child.tag for child in case]
        require(all(tag in outcomes or tag in retries or tag in ("system-out", "system-err") for tag in tags))
        states = [outcomes[tag] for tag in tags if tag in outcomes]
        require(len(states) <= 1)
        if states:
            counts[states[0]] += 1
        flaky |= any(tag in retries for tag in tags)
    counts["tests"] = len(cases)
    counts["executed"] = counts["tests"] - counts["skipped"]
    for key in ("tests", "failures", "errors", "skipped"):
        stated = text(suite.get(key), r"(?:0|[1-9][0-9]{0,6})")
        require(int(stated) == counts[key])
    methods = set(owner["methods"])
    require(not methods or cases <= methods)
    complete = len(cases) >= owner["minimumTests"] and (not methods or cases == methods)
    return counts, flaky, complete


def summarize_reports(root, run, rule):
    owners = {(owner["module"], owner["className"]): owner for owner in rule["reports"]}
    selectors = {owner["selector"] for owner in owners.values()}
    require(unique(exact_strings(run["selectors"], len(owners), SAFE_PART)) == selectors)
    seen = set()
    totals = dict.fromkeys(COUNT_FIELDS, 0)
    flaky = False
    incomplete = False
    for report in array(run["reports"], len(owners)):
        shape(report, ("module", "className", "file"))
        key = (text(report["module"], SAFE_PART), text(report["className"], r"[A-Za-z0-9.]+"))
        require(key in owners and key not in seen)
        seen.add(key)
        counts, rerun, complete = summarize_report(root, report["file"], owners[key], selectors)
        for field, value in counts.items():
            totals[field] += value
        flaky |= rerun
        incomplete |= not complete
    return (totals if seen else None), flaky, (incomplete or seen != owners.keys())


def parse_manifest(raw, run, suite, test_classes):
    manifest = shape(strict_json(raw), ("schemaVersion", "runId", "runAuthorized", "suite", "checkoutRoot",
                     "privateRoot", "outputDirectory", "fixtureRoot", "revisions", "agent", "process",
                     "tenants", "stores", "tls", "endpoints", "limits"))
    require(integer(manifest["schemaVersion"]) == 1 and manifest["runId"] == run["runId"]
            and manifest["suite"] == suite and bool_value(manifest["runAuthorized"])
            and revisions(manifest["revisions"]) == run["revisions"])
    for field in ("checkoutRoot", "privateRoot", "outputDirectory", "fixtureRoot"):
        text(manifest[field])  # Captured references are never resolved.
    tree_matches = True
    for role in ARCHIVE_ROLES:
        lock = shape(manifest[role], ("execJar", "execJarSha256", "testClasses", "testClassesSha256"))
        text(lock["execJar"])
        text(lock["testClasses"])
        require(sha_text(lock["execJarSha256"]) == run["archiveShas"][role])
        tree_matches &= sha_text(lock["testClassesSha256"]) == test_classes[role]

    tenants = []
    issuers = []
    owner_subjects = set()
    for tenant in array(manifest["tenants"], 2):
        shape(tenant, ("tenantId", "issuer", "ownerSubject", "reviewerSubjects", "roleIds", "applicationId",
                       "processResource", "agentResource", "credentialFile"))
        tenants.append(integer(tenant["tenantId"], 1))
        for key in ("issuer", "ownerSubject", "applicationId", "processResource", "agentResource", "credentialFile"):
            text(tenant[key])
        issuers.append(tenant["issuer"])
        owner_subjects.add(text(tenant["ownerSubject"], SAFE_REFERENCE))
        reviewers = unique(exact_strings(tenant["reviewerSubjects"], 2))
        role_ids = unique(exact_strings(tenant["roleIds"], 3, r"[1-9][0-9]{0,18}"))
        require(len(reviewers) == 2 and tenant["ownerSubject"] not in reviewers and len(role_ids) == 3
                and tenant["processResource"] != tenant["agentResource"])
        for role_id in role_ids:
            integer(int(role_id), 1)
    tenant_ids = unique(tenants)
    require(len(tenant_ids) == 2 and len(unique(issuers)) == 2 and len(owner_subjects) == 1)

    store_owners = {"AGENT": "tianji_agent", "PROCESS": "tianji_process", "BUSINESS": "tianji_worker"}
    owners = []
    databases = []
    roles = []
    for store in array(manifest["stores"], 3):
        shape(store, ("owner", "jdbcUrl", "serverIdentity", "schema", "postgresVersion", "pgvectorVersion",
                      "historySha256", "setupRole", "runtimeRole", "observerRole", "credentialFile"))
        owner = text(store["owner"])
        require(owner in store_owners and store["schema"] == store_owners[owner])
        owners.append(owner)
        jdbc = re.fullmatch(r"jdbc:postgresql://(?:127\.0\.0\.1|\[::1\]):([0-9]{1,5})/(tianji_it_[a-z0-9_]{1,51})",
                            text(store["jdbcUrl"]))
        require(jdbc is not None and 0 < int(jdbc[1]) <= 65535)
        databases.append(jdbc[2])
        text(store["serverIdentity"], SAFE_REFERENCE)
        text(store["postgresVersion"], r"[0-9]{1,2}\.[0-9]{1,2}(?:\.[0-9]{1,2})?")
        if owner == "AGENT":
            text(store["pgvectorVersion"], r"[0-9]{1,2}\.[0-9]{1,2}\.[0-9]{1,2}")
        else:
            require(store["pgvectorVersion"] == "NONE")
        sha_text(store["historySha256"])
        text(store["credentialFile"])
        for role in ("setupRole", "runtimeRole", "observerRole"):
            roles.append(text(store[role], r"tianji_it_[a-z0-9_]{1,50}"))
    require(unique(owners) == store_owners.keys())
    unique(roles)
    stores = unique(databases)

    for field, keys in (
        ("tls", ("trustStore", "agentKeyStore", "processKeyStore", "relayKeyStore", "credentialFile")),
        ("endpoints", ("agent", "process", "linkedRelay", "resultRelay", "workerRelay", "collector")),
    ):
        for value in shape(manifest[field], keys).values():
            text(value)
    limits = shape(manifest["limits"], ("readinessSeconds", "pathSeconds", "manualSeconds", "recoverySeconds", "linkedHoldSeconds"))
    for value in limits.values():
        integer(value, 1, (1 << 31) - 1)
    return {"tenants": tenant_ids, "stores": stores, "treeMatches": tree_matches}


def child_identity(child, start, observed):
    shape(child, ("role", "generation", "pid", "startedAt"))
    require(child["role"] in ("AGENT", "PROCESS"))
    started = instant(child["startedAt"])
    require(start <= started <= observed)
    return child["role"], integer(child["generation"], 1, (1 << 31) - 1), integer(child["pid"], 1), started


def parse_witness(raw, run, suite, manifest, policy, start, finish):
    evidence = shape(strict_json(raw), ("schemaVersion", "runId", "suite", "passed", "observedAt", "children", "exits", "paths"))
    require(integer(evidence["schemaVersion"]) == 1 and evidence["runId"] == run["runId"] and evidence["suite"] == suite)
    passed = bool_value(evidence["passed"])
    observed = instant(evidence["observedAt"])
    require(start <= observed <= finish)
    children = unique([child_identity(value, start, observed) for value in array(evidence["children"], 64)])
    pids = unique([identity[2] for identity in children])
    exits = []
    for exit_info in array(evidence["exits"], 64):
        shape(exit_info, ("child", "exitCode"))
        exits.append(child_identity(exit_info["child"], start, observed))
        integer(exit_info["exitCode"], -(1 << 31), (1 << 31) - 1)
    exit_children = unique(exits)
    require(exit_children <= children and (not passed or exit_children == children))
    for role in ("AGENT", "PROCESS"):
        owned = sorted(value for value in children if value[0] == role)
        require([value[1] for value in owned] == list(range(1, len(owned) + 1))
                and all(left[3] < right[3] for left, right in zip(owned, owned[1:])))
        if passed:
            require(len(owned) >= (2 if suite == "RECOVERY" else 1))

    base = set(policy["baseWitnesses"])
    path_rules = policy["expensePaths"]
    all_witnesses = base.union(*(path["extraWitnesses"] for path in path_rules.values()))
    scenarios = {name for name, rule in path_rules.items() if rule["suite"] == suite}
    paths = {}
    for path in array(evidence["paths"], 2 * len(scenarios)):
        shape(path, ("tenantId", "scenario", "operationId", "businessKey", "correlationId", "passed", "witnesses"))
        tenant = integer(path["tenantId"], 1)
        scenario = text(path["scenario"])
        require(scenario in scenarios and (manifest is None or tenant in manifest["tenants"])
                and (tenant, scenario) not in paths)
        paths[tenant, scenario] = path
        for field in ("operationId", "businessKey", "correlationId"):
            text(path[field], SAFE_REFERENCE)
        path_passed = bool_value(path["passed"])
        require(not passed or path_passed)
        expected = base | set(path_rules[scenario]["extraWitnesses"])
        witnesses = shape(path["witnesses"], all_witnesses if path_passed else (), () if path_passed else all_witnesses)
        for kind, witness in witnesses.items():
            shape(witness, ("state", "reference"))
            state = witness["state"]
            require(state in ("PROVED", "NOT_REACHED", "UNAVAILABLE", "PENDING"))
            if state == "PROVED":
                text(witness["reference"], SAFE_REFERENCE)
            else:
                require(witness["reference"] is None)
            if path_passed:
                require(state == ("PROVED" if kind in expected else "NOT_REACHED"))
        if path_passed:
            for kind, field in (("DECISION_RULE", "ruleId"), ("AGENT_STATUS", "agentStatus"), ("PROCESS_END", "processEnd")):
                require(witnesses[kind]["reference"] == path_rules[scenario][field])

    if passed and manifest is not None:
        tenants = sorted(manifest["tenants"])
        require(paths.keys() == {(tenant, scenario) for tenant in tenants for scenario in scenarios})
        for scenario in scenarios:
            left, right = (paths[tenant, scenario] for tenant in tenants)
            require(all(left[field] == right[field] for field in ("operationId", "businessKey", "correlationId")))
            for kind in ("PROCESS_INSTANCE", "AGENT_RUN", "ORIGINAL_RECEIPT"):
                require(left["witnesses"][kind]["reference"] != right["witnesses"][kind]["reference"])
    return {"passed": passed, "pids": pids}


def read_environment(root, run, rule, policy, index, start, finish):
    env = run["environment"]
    if rule["level"] == "U":
        require(shape(env, ("kind",))["kind"] == "OFFLINE_FAKE")
        return None, None
    if env is None:
        return None, None
    if rule["level"] == "I":
        shape(env, ("kind", "postgresVersion", "flowableVersion", "environmentSha256"))
        require(env["kind"] == "ISOLATED_NATIVE_PG" and env["flowableVersion"] == "8.0.0")
        text(env["postgresVersion"], r"18\.[0-9]{1,2}(?:\.[0-9]{1,2})?")
        sha_text(env["environmentSha256"])
        return env, None

    shape(env, ("kind", "manifest", "witness"))
    require(env["kind"] == "EXPENSE_ISOLATED_E")
    suite = rule["id"].removeprefix("E_")
    manifest = None
    if env["manifest"] is not None:
        raw, _ = root.ref(env["manifest"], JSON_LIMIT)
        manifest = parse_manifest(raw, run, suite, index["testClasses"])
    witness = None
    if env["witness"] is not None:
        try:
            raw, _ = root.ref(env["witness"], JSON_LIMIT)
            witness = parse_witness(raw, run, suite, manifest, policy, start, finish)
        except (InvalidEvidence, OSError):
            return manifest, "INVALID_WITNESS"
    return manifest, witness


def gate(rule, status, reason, counts=None):
    return {"id": rule["id"], "level": rule["level"], "status": status, "reason": reason,
            **(dict.fromkeys(COUNT_FIELDS) if counts is None else counts)}


def bound_provenance(value, candidate_sha, revisions_lock, archives):
    recorded = sha_text(value["candidateSha256"]), revisions(value["revisions"]), archive_shas(value["archiveShas"])
    return recorded == (candidate_sha, revisions_lock, archives)


def read_compile_proof(root, proof, candidate_sha, revisions_lock, archives, audit, now):
    shape(proof, ("module", "className", "classFile", "candidateSha256", "revisions", "archiveShas", "capturedAt"))
    ref = file_ref(proof["classFile"])
    require(Path(ref["path"]).name == proof["className"].rsplit(".", 1)[1] + ".class")
    raw, _ = root.ref(ref, CLASS_LIMIT)
    require(len(raw) >= 10 and raw[:4] == b"\xca\xfe\xba\xbe")
    captured = instant(proof["capturedAt"])
    require(captured <= now)
    if not bound_provenance(proof, candidate_sha, revisions_lock, archives):
        return ("STALE", "SOURCE_LOCK_MISMATCH")
    if not audit[0] <= captured < audit[1]:
        return ("STALE", "AUDIT_WINDOW_MISS")
    return ("COMPILED_ONLY", "CLASS_COMPILED")


def classify_group(root, rule, run, proof, policy, index, candidate_sha, revisions_lock, archives, audit, now):
    compiled = None
    if proof is not None:
        try:
            compiled = read_compile_proof(root, proof, candidate_sha, revisions_lock, archives, audit, now)
        except (InvalidEvidence, OSError):
            return gate(rule, "INVALID_EVIDENCE", "INVALID_COMPILE_PROOF"), None
    if run is None:
        return gate(rule, *(compiled or ("NOT_EXECUTED", "NO_RUN"))), None

    try:
        shape(run, ("groupId", "runId", "level", "startedAt", "finishedAt", "exitCode", "candidateSha256",
                    "revisions", "archiveShas", "selectors", "environment", "reports"))
        require(run["level"] == rule["level"])
        start, finish = instant(run["startedAt"]), instant(run["finishedAt"])
        require(start <= finish <= now)
        exit_code = integer(run["exitCode"], -(1 << 31), (1 << 31) - 1)
        current = bound_provenance(run, candidate_sha, revisions_lock, archives)
        counts, flaky, incomplete = summarize_reports(root, run, rule)
    except (InvalidEvidence, OSError):
        return gate(rule, "INVALID_EVIDENCE", "INVALID_REPORT"), None

    try:
        manifest, witness = read_environment(root, run, rule, policy, index, start, finish)
    except (InvalidEvidence, OSError):
        return gate(rule, "INVALID_EVIDENCE", "INVALID_ENVIRONMENT", counts), None
    if witness == "INVALID_WITNESS":
        return gate(rule, "INVALID_EVIDENCE", "INVALID_WITNESS", counts), None
    if not current or (rule["level"] == "E" and manifest is not None and not manifest["treeMatches"]):
        return gate(rule, "STALE", "SOURCE_LOCK_MISMATCH", counts), None
    if not audit[0] <= finish < audit[1]:
        return gate(rule, "STALE", "AUDIT_WINDOW_MISS", counts), None
    if flaky or (counts is not None and (counts["failures"] or counts["errors"])):
        return gate(rule, "FAILED", "TEST_FAILURE", counts), None
    if exit_code:
        return gate(rule, "FAILED", "RUN_EXIT_NONZERO", counts), None
    if counts is not None and counts["skipped"]:
        return gate(rule, "SKIPPED", "TEST_SKIPPED", counts), None
    if witness is not None and not witness["passed"]:
        return gate(rule, "FAILED", "WITNESS_FAILED", counts), None
    if counts is None and compiled is not None:
        return gate(rule, *compiled), None
    if counts is not None and not counts["executed"]:
        return gate(rule, "NOT_EXECUTED", "NO_EXECUTED_TESTS", counts), None
    if incomplete:
        return gate(rule, "NOT_EXECUTED", "REQUIRED_REPORT_MISSING", counts), None
    if rule["level"] == "I" and manifest is None:
        return gate(rule, "INVALID_EVIDENCE", "INVALID_ENVIRONMENT", counts), None
    if rule["level"] == "E":
        if manifest is None or witness is None:
            return gate(rule, "INVALID_EVIDENCE", "WITNESS_MISSING", counts), None
        return gate(rule, "PASS", "VERIFIED", counts), (manifest, witness)
    return gate(rule, "PASS", "VERIFIED", counts), None


def validate_expense_pair(groups, facts):
    if facts[2] is None or facts[3] is None:
        return
    (business, business_witness), (recovery, recovery_witness) = facts[2:]
    if (business["tenants"] == recovery["tenants"] and business["stores"].isdisjoint(recovery["stores"])
            and business_witness["pids"].isdisjoint(recovery_witness["pids"])):
        return
    for row in groups[2:]:
        row.update(status="INVALID_EVIDENCE", reason="INVALID_ENVIRONMENT")


def run(argv, stdout, stderr, *, now_ns=None, git_runner=None):
    try:
        args = parse_argv(argv)
        now = time.time_ns() if now_ns is None else now_ns()
        audit = instant(args["--audit-from"]), instant(args["--audit-until"])
        if not audit[0] < audit[1] <= now:
            raise UsageError
        policy, policy_sha = read_policy()
        with PrivateRoot(args["--root"]) as root:
            index, runs, compiled = read_index(root, args["--index"], policy, policy_sha)
            source_lock = verify_checkout(args["--checkout"], args["--expect-revision"], index["revisions"],
                                          run_git if git_runner is None else git_runner)
            candidate_sha = args["--expect-candidate-sha256"]
            candidate, envelope_sha = parse_candidate(root, index["candidate"], candidate_sha)
            require(candidate["sourceRevision"] == source_lock["tianjiService"])
            archives = {}
            for role in ARCHIVE_ROLES:
                _, digest = root.ref(index["archives"][role], ARCHIVE_LIMIT, retain=False)
                require(candidate["packages"][role]["archiveSha256"] == digest)
                archives[role] = digest
            signature = bind_signature_reference(root, index, args.get(OPTIONAL_FLAG), candidate, candidate_sha, envelope_sha)
            evaluated = [classify_group(root, rule, runs.get(rule["id"]), compiled.get(rule["id"]), policy, index,
                                        candidate_sha, source_lock, archives, audit, now) for rule in policy["groups"]]
            groups, facts = map(list, zip(*evaluated))
            validate_expense_pair(groups, facts)
            ready = all(group["status"] == "PASS" for group in groups)
            output = canonical({
                "schemaVersion": 1, "profileId": PROFILE_ID, "policySha256": policy_sha, "candidateSha256": candidate_sha,
                "sourceRevision": source_lock["tianjiService"], "archiveShas": archives, "signatureProof": signature,
                "auditWindow": {"fromInclusive": args["--audit-from"], "untilExclusive": args["--audit-until"]},
                "verdict": "EXPENSE_EVIDENCE_READY_FOR_REVIEW" if ready else "BLOCKED", "gates": groups,
            }) + b"\n"
            require(len(output) <= OUTPUT_LIMIT)
        if stdout.write(output) != len(output):
            raise Unavailable
        stdout.flush()
        return 0 if ready else 1
    except UsageError:
        return diagnostic(stderr, 64, b"TIANJI_RELEASE_EVIDENCE_USAGE\n")
    except InvalidEvidence:
        return diagnostic(stderr, 65, b"TIANJI_RELEASE_EVIDENCE_INVALID\n")
    except (OSError, Unavailable, NotImplementedError):
        return diagnostic(stderr, 66, b"TIANJI_RELEASE_EVIDENCE_IO_UNAVAILABLE\n")


def diagnostic(stderr, code, message):
    try:
        stderr.write(message)
        stderr.flush()
    except OSError:
        return 66
    return code


def main():
    stdout, stderr = sys.stdout.buffer, sys.stderr.buffer
    return run(sys.argv[1:], getattr(stdout, "raw", stdout), getattr(stderr, "raw", stderr))


if __name__ == "__main__":
    sys.exit(main())
