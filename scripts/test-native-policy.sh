#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
binary=$(mktemp /tmp/atlas-native-policy.XXXXXX)
trap 'rm -f "$binary"' EXIT
g++ -std=c++17 -O2 -Wall -Wextra -Werror tests/native/cache_policy_test.cpp -o "$binary"
"$binary"
