# HPKE Benchmark: JDK 26 vs Bouncy Castle 1.83

Microbenchmarks comparing **JDK 26** native HPKE (`Cipher` + `HPKEParameterSpec`) with **Bouncy Castle 1.83** (`org.bouncycastle.crypto.hpke.HPKE`) across eight RFC 9180-style suites, keygen / seal / open operations, and cross-library interoperability tests.

## Requirements

| Tool | Version |
|------|---------|
| **JDK** | 26 (for `javax.crypto.spec.HPKEParameterSpec`) |
| **Maven** | 3.9+ |
| **Python** | 3.11+ with `pandas`, `matplotlib`, `numpy` (for `scripts/analyze.py`) |

### Installing JDK 26 (Ubuntu / Linux / macOS)

Ubuntu 24.04 packages may not ship OpenJDK 26 yet. Use a JDK 26 build under **`.jdks/jdk-26`** (ignored by git):

```bash
./scripts/install-jdk26.sh   # Temurin 26: tries GA, then EA from api.adoptium.net
source scripts/env.sh        # sets JAVA_HOME to .jdks/jdk-26 when present
```

While only **early-access** builds are published for your platform, the script downloads EA automatically; swap to a GA tarball later by re-running after GA appears, or set `JAVA_HOME` to a system install.

Optional local JDK (no `JAVA_HOME` needed if detected):

- **macOS**: `.jdks/jdk-26.jdk/Contents/Home` (standard `.jdk` bundle layout)
- **Linux / Ubuntu workspace**: extract a JDK 26 tarball so `bin/java` lives at `.jdks/jdk-26/bin/java` (flat layout — not `Contents/Home`)

Otherwise set `JAVA_HOME` explicitly to your JDK 26 root, for example:

```bash
# Typical Ubuntu/OpenJDK layout (exact directory name varies by package):
export JAVA_HOME=/usr/lib/jvm/java-26-openjdk-amd64
```

The repo can also use a bundled Maven under `.maven/` if present. Maven **3.9+** is recommended (`pom.xml` targets Java 26); older Maven may work if it runs with JDK 26 on `JAVA_HOME`.

## Quick start

```bash
source scripts/env.sh          # optional: prepends bundled Maven; sets JAVA_HOME only if .jdks layout matches
# If env.sh warned about JAVA_HOME, set it to JDK 26 first (see above).
mvn clean verify               # compile, tests, shaded JMH JAR → target/benchmarks.jar
```

**Ubuntu / CI note:** The default `java` on many Ubuntu images is not 26. You must install JDK 26 and point `JAVA_HOME` at it (or use `.jdks/jdk-26` as above); `mvn` picks the compiler from `JAVA_HOME` / `PATH`.

### Where results are written

After `source scripts/env.sh`, **`HPKE_RESULTS_ROOT`** points at the output tree for this machine:

| OS | Default root |
|----|----------------|
| **Linux** (incl. Ubuntu) | `results-ubuntu/` |
| **macOS** and others | `results/` |

JMH JSON and analysis CSVs go under **`<root>/raw/`**; figures and LaTeX under **`<root>/plots/`**. Set `export HPKE_RESULTS_ROOT=/path/to/dir` before sourcing `env.sh` to override (path may be relative to the repo root).

## Tests

```bash
mvn test
```

- `WrapperRoundTripTest` — per-library seal/open for all suites  
- `InteropTest` — JDK↔BC cross-library open, tamper rejection  

## Full benchmark run (slow)

Produces JSON under **`$HPKE_RESULTS_ROOT/raw/`** (on Ubuntu: `results-ubuntu/raw/`; on macOS: `results/raw/`). Several hours total for all three benchmark classes:

```bash
./scripts/run_benchmarks.sh
```

Configuration: 3 forks, 5×3s warmup, 10×3s measurement, GC profiler, average time in µs/op.

Requires `target/benchmarks.jar` from `mvn clean verify` (or `mvn package`).

## Faster OpenBenchmark (recovery)

If a full `OpenBenchmark` run stalls (large pre-seal batch × 64 KB payloads), the code uses `BATCH = 10` pre-sealed ciphertexts. You can rerun **only** Open with lighter JMH flags, e.g.:

```bash
source scripts/env.sh
mkdir -p "${HPKE_RESULTS_ROOT}/raw"
java -jar target/benchmarks.jar \
  -f 1 -wi 2 -w 2 -i 5 -r 2 \
  -tu us -bm avgt -t 1 -prof gc \
  -rf json -rff "${HPKE_RESULTS_ROOT}/raw/OpenBenchmark.json" \
  ph.jhury.hpke.benchmarks.OpenBenchmark
```

Adjust `-f`, `-wi`, `-i`, `-r` to trade runtime vs. confidence intervals.

## Analysis and plots

Merges all JSON under the default raw directory for your platform (`results-ubuntu/raw/` on Linux, `results/raw/` on macOS), writes `all_results.csv`, per-benchmark CSVs, LaTeX snippet, and PNG figures. Matches **`HPKE_RESULTS_ROOT`** if set in the environment.

```bash
python3 -m pip install -r requirements.txt   # or: pandas matplotlib numpy
python3 scripts/analyze.py
```

Outputs (under **`$HPKE_RESULTS_ROOT`** when using defaults):

- `<root>/raw/all_results.csv` — flattened scores  
- `<root>/raw/generate.csv`, `seal.csv`, `open.csv` — per benchmark  
- `<root>/plots/figure_keygen.png`, `figure_seal_by_payload.png` — 150 dpi  
- `<root>/plots/table_seal_1kb.tex` — seal at 1 KB payload  

Options: `--raw-dir`, `--plots-dir`. Override the default tree with `HPKE_RESULTS_ROOT` (same semantics as `env.sh`).

## Project layout

```
src/main/java/ph/jhury/hpke/
  suites/HpkeSuite.java          # KEM/KDF/AEAD identifiers (JDK + BC)
  crypto/KeyCodec.java          # raw key bytes for interop
  wrappers/                      # Jdk26HpkeWrapper, BcHpkeWrapper
  benchmarks/                  # KeygenBenchmark, SealBenchmark, OpenBenchmark
src/test/java/...               # JUnit 5 interop + round-trip
scripts/env.sh                  # optional JAVA_HOME / Maven PATH
scripts/install-jdk26.sh        # download Temurin 26 → .jdks/jdk-26
scripts/run_benchmarks.sh       # full JSON benchmark sweep
scripts/analyze.py              # CSV + LaTeX + PNG from JMH JSON
vectors/rfc9180_vectors.json  # placeholder (extend for RFC vector tests)
results/raw/                   # macOS default: JMH JSON/CSV (gitignored)
results/plots/
results-ubuntu/raw/            # Linux default: same role as results/raw/
results-ubuntu/plots/
```

## License / paper

Benchmark methodology follows JMH best practices (forks, warmups, `Blackhole` consumption). Cite JDK 26 and Bouncy Castle versions used in your paper’s experimental setup section.
