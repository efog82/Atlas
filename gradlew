#!/usr/bin/env bash
set -euo pipefail
project_dir=$(cd "$(dirname "$0")" && pwd)
if command -v gradle >/dev/null 2>&1; then
    exec gradle -p "$project_dir" "$@"
fi
gradle_bin=$(bash "$project_dir/scripts/bootstrap-gradle.sh")
exec "$gradle_bin" -p "$project_dir" "$@"
