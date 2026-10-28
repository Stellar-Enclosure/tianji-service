#!/bin/sh
set -eu

task_root=$(CDPATH='' cd -- "$(dirname "$0")/.." && pwd -P)
task_components=${STELLAR_COMPONENTS_DIR:-"$task_root/../Stellar-Enclosure-Components"}
if [ ! -f "$task_components/pom.xml" ]; then
    printf 'Components source repository not found: %s\n' "$task_components" >&2
    exit 1
fi
task_reactor_dir=$(mktemp -d "${TMPDIR:-/tmp}/tianji-reactor.XXXXXX")
cleanup() {
    rm -f "$task_reactor_dir/pom.xml"
    rmdir "$task_reactor_dir"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
python3 - "$task_reactor_dir/pom.xml" "$task_components" "$task_root" <<'PY'
import os
import sys
from pathlib import Path
import xml.etree.ElementTree as ET

path = Path(sys.argv[1]).resolve()
project = ET.Element('project', {'xmlns': 'http://maven.apache.org/POM/4.0.0'})
for name, value in [('modelVersion', '4.0.0'), ('groupId', 'top.egon.internal'),
                    ('artifactId', 'tianji-source-verification'), ('version', '0.0.1'), ('packaging', 'pom')]:
    ET.SubElement(project, name).text = value
modules = ET.SubElement(project, 'modules')
for source in sys.argv[2:]:
    ET.SubElement(modules, 'module').text = os.path.relpath(Path(source).resolve(), path.parent)
ET.ElementTree(project).write(path, encoding='utf-8', xml_declaration=True)
PY
"$task_root/mvnw" -f "$task_reactor_dir/pom.xml" -pl :stellar-enclosure-tianji-client,:stellar-enclosure-tianji-agent-server -am "$@" test
