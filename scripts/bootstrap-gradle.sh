#!/usr/bin/env bash
set -euo pipefail
version=8.11.1
cache="${GRADLE_USER_HOME:-$HOME/.gradle}/atlas-bootstrap"
destination="$cache/gradle-$version"
if [ ! -x "$destination/bin/gradle" ]; then
    mkdir -p "$cache"
    archive="$cache/gradle-$version-bin.zip"
    url="https://downloads.gradle.org/distributions/gradle-$version-bin.zip"
    echo "A obter Gradle $version e checksum oficial…" >&2
    curl --fail --location --connect-timeout 10 --max-time 60 "$url.sha256" -o "$archive.sha256"
    curl --fail --location --connect-timeout 10 --max-time 60 "$url" -o "$archive.part"
    expected=$(tr -d '[:space:]' < "$archive.sha256")
    actual=$(sha256sum "$archive.part" | cut -d' ' -f1)
    [ "$actual" = "$expected" ] || { echo "Checksum Gradle inválido" >&2; rm -f "$archive.part"; exit 1; }
    mv "$archive.part" "$archive"
    unzip -q -o "$archive" -d "$cache"
    rm "$archive"
fi
printf '%s\n' "$destination/bin/gradle"
