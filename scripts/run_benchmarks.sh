#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
source "$ROOT/scripts/env.sh"

if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "JAVA_HOME must point to JDK 26 (with bin/java). On Ubuntu: export JAVA_HOME=/usr/lib/jvm/<your-jdk-26> — see README." >&2
  exit 1
fi

JAR="$ROOT/target/benchmarks.jar"
OUT="${HPKE_RESULTS_ROOT}/raw"
mkdir -p "$OUT"

if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR — run: mvn -q clean verify" >&2
  exit 1
fi

for bench in KeygenBenchmark SealBenchmark OpenBenchmark; do
  echo "Running $bench..."
  "$JAVA_HOME/bin/java" -jar "$JAR" \
    -f 3 -wi 5 -i 10 -r 3 -w 3 \
    -tu us -bm avgt -t 1 \
    -prof gc \
    -rf json -rff "$OUT/${bench}.json" \
    "ph.jhury.hpke.benchmarks.$bench"
done

echo "Done. JSON results in $OUT/"
