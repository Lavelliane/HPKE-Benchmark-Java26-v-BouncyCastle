#!/usr/bin/env bash
# Source from repo root:  source scripts/env.sh
#
# Sets JAVA_HOME when unset/empty to a repo-local JDK 26 if found (macOS .jdk bundle
# or Linux-style tree). If you install JDK 26 system-wide (apt, SDKMAN!, tarball),
# export JAVA_HOME before sourcing — e.g. export JAVA_HOME=/usr/lib/jvm/java-26-openjdk-amd64
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Benchmark/analysis output root: Linux → results-ubuntu (avoid clobbering macOS results/).
# Override: export HPKE_RESULTS_ROOT=/path/to/my-results
if [[ -z "${HPKE_RESULTS_ROOT:-}" ]]; then
  case "$(uname -s)" in
    Linux) export HPKE_RESULTS_ROOT="$ROOT/results-ubuntu" ;;
    *) export HPKE_RESULTS_ROOT="$ROOT/results" ;;
  esac
fi

_valid_java_home() {
  [[ -n "$1" ]] && [[ -x "$1/bin/java" ]]
}

if _valid_java_home "${JAVA_HOME:-}"; then
  export JAVA_HOME
elif _valid_java_home "$ROOT/.jdks/jdk-26.jdk/Contents/Home"; then
  # macOS .jdk bundle (e.g. from JetBrains / manual install)
  export JAVA_HOME="$ROOT/.jdks/jdk-26.jdk/Contents/Home"
elif _valid_java_home "$ROOT/.jdks/jdk-26"; then
  # Linux / generic tarball: e.g. tar -C .jdks -xzf OpenJDK26… && mv … jdk-26
  export JAVA_HOME="$ROOT/.jdks/jdk-26"
elif _valid_java_home "$ROOT/.jdks/jdk"; then
  export JAVA_HOME="$ROOT/.jdks/jdk"
fi

if [[ -d "$ROOT/.maven/apache-maven-3.9.9/bin" ]]; then
  export PATH="$ROOT/.maven/apache-maven-3.9.9/bin:$PATH"
fi

if ! _valid_java_home "${JAVA_HOME:-}"; then
  echo "scripts/env.sh: JAVA_HOME not set to a JDK with bin/java; export JAVA_HOME to JDK 26 (see README)." >&2
fi
