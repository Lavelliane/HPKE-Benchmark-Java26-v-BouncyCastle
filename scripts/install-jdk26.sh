#!/usr/bin/env bash
# Download Eclipse Temurin JDK 26 into .jdks/jdk-26 (gitignored).
# Tries GA first; falls back to EA while Linux/mac GA binaries are unavailable.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/.jdks/jdk-26"

OS_RAW="$(uname -s)"
ARCH_RAW="$(uname -m)"
case "$OS_RAW" in
  Linux) OS_API=linux ;;
  Darwin) OS_API=mac ;;
  *) echo "Unsupported OS: $OS_RAW" >&2; exit 1 ;;
esac
case "$ARCH_RAW" in
  x86_64) ARCH_API=x64 ;;
  aarch64 | arm64) ARCH_API=aarch64 ;;
  *) echo "Unsupported architecture: $ARCH_RAW" >&2; exit 1 ;;
esac

mkdir -p "$ROOT/.jdks"
TMP="$(mktemp "${TMPDIR:-/tmp}/jdk26-XXXXXX.tar.gz")"
cleanup() { rm -f "$TMP"; }
trap cleanup EXIT

fetch() {
  local release_type=$1
  curl -fL --retry 3 -o "$TMP" \
    "https://api.adoptium.net/v3/binary/latest/26/${release_type}/${OS_API}/${ARCH_API}/jdk/hotspot/normal/eclipse?project=jdk"
}

if fetch ga 2>/dev/null; then
  echo "Downloaded JDK 26 (GA)."
else
  echo "JDK 26 GA not available for ${OS_API}/${ARCH_API}; using early-access build." >&2
  fetch ea
fi

rm -rf "$DEST"
tar -xzf "$TMP" -C "$ROOT/.jdks"
EXTRACTED="$(find "$ROOT/.jdks" -maxdepth 1 -type d -name 'jdk-26*' ! -path "$DEST" | head -1)"
if [[ -z "$EXTRACTED" ]]; then
  echo "Extracted JDK directory not found under .jdks" >&2
  ls -la "$ROOT/.jdks" >&2
  exit 1
fi
if [[ "$EXTRACTED" != "$DEST" ]]; then
  mv "$EXTRACTED" "$DEST"
fi

echo "JAVA_HOME=$DEST"
"$DEST/bin/java" -version
