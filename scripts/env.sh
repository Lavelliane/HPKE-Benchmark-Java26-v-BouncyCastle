#!/usr/bin/env bash
# Source from repo root:  source scripts/env.sh
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-$ROOT/.jdks/jdk-26.jdk/Contents/Home}"
if [[ -d "$ROOT/.maven/apache-maven-3.9.9/bin" ]]; then
  export PATH="$ROOT/.maven/apache-maven-3.9.9/bin:$PATH"
fi
