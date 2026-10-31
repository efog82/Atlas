#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
version=b5046
destination=native/llama.cpp
if [ -d "$destination/.git" ]; then
    actual=$(git -C "$destination" describe --tags --exact-match)
    [ "$actual" = "$version" ] || { echo "Versão llama.cpp inesperada: $actual" >&2; exit 1; }
else
    [ ! -e "$destination" ] || { echo "Destino nativo já existe e não é um clone Git." >&2; exit 1; }
    git -c http.connectTimeout=15 clone --depth 1 --branch "$version" https://github.com/ggml-org/llama.cpp.git "$destination"
fi
git -C "$destination" rev-parse HEAD > native/llama-revision.txt
mkdir -p app/src/main/assets/licenses
cp "$destination/LICENSE" app/src/main/assets/licenses/llama.cpp.LICENSE.txt
echo "llama.cpp $version preparado. Licença MIT em native/llama.cpp/LICENSE."
