#!/usr/bin/env bash
# This branch's version, from git alone: releasing is tagging. git describe against the
# v<major>.<minor>.<patch> tags gives v58.1.0 on a tag, v58.1.0-3-gabc1234 after one, and a bare
# short hash before the first. Upstream's integer tags (58, 59, ...) do not count. Outside git it
# is "unknown".
set -euo pipefail
cd "$(dirname "$0")/.."
git describe --tags --always --dirty --match 'v[0-9]*.[0-9]*.[0-9]*' 2> /dev/null || echo unknown
