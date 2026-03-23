# HPKE Benchmark: JDK 26 vs Bouncy Castle 1.83

Microbenchmarks comparing **JDK 26** native HPKE (`Cipher` + `HPKEParameterSpec`) with **Bouncy Castle 1.83** (`org.bouncycastle.crypto.hpke.HPKE`) across eight RFC 9180-style suites, keygen / seal / open operations, and cross-library interoperability tests.

## Requirements

| Tool | Version |
|------|---------|
| **JDK** | 26 (for `javax.crypto.spec.HPKEParameterSpec`) |
| **Maven** | 3.9+ |
| **Python** | 3.11+ with `pandas`, `matplotlib` (for `scripts/analyze.py`) |

Optional: place a JDK 26 install at `.jdks/jdk-26.jdk/Contents/Home` (macOS) or set `JAVA_HOME` yourself. The repo can also use a bundled Maven under `.maven/` if present.

## Quick start

```bash
source scripts/env.sh          # sets JAVA_HOME / PATH when using local .jdks / .maven
mvn clean verify               # compile, tests, shaded JMH JAR → target/benchmarks.jar
```

## Tests

```bash
mvn test
```

- `WrapperRoundTripTest` — per-library seal/open for all suites  
- `InteropTest` — JDK↔BC cross-library open, tamper rejection  

## Full benchmark run (slow)

Produces JSON under `results/raw/` (several hours total for all three benchmark classes):

```bash
./scripts/run_benchmarks.sh
```

Configuration: 3 forks, 5×3s warmup, 10×3s measurement, GC profiler, average time in µs/op.

Requires `target/benchmarks.jar` from `mvn clean verify` (or `mvn package`).

## Faster OpenBenchmark (recovery)

If a full `OpenBenchmark` run stalls (large pre-seal batch × 64 KB payloads), the code uses `BATCH = 10` pre-sealed ciphertexts. You can rerun **only** Open with lighter JMH flags, e.g.:

```bash
source scripts/env.sh
java -jar target/benchmarks.jar \
  -f 1 -wi 2 -w 2 -i 5 -r 2 \
  -tu us -bm avgt -t 1 -prof gc \
  -rf json -rff results/raw/OpenBenchmark.json \
  ph.jhury.hpke.benchmarks.OpenBenchmark
```

Adjust `-f`, `-wi`, `-i`, `-r` to trade runtime vs. confidence intervals.

## Analysis and plots

Merges all `results/raw/*.json`, writes `all_results.csv`, per-benchmark CSVs, LaTeX snippet, and PNG figures:

```bash
python3 scripts/analyze.py
```

Outputs:

- `results/raw/all_results.csv` — flattened scores  
- `results/raw/generate.csv`, `seal.csv`, `open.csv` — per benchmark  
- `results/plots/figure_keygen.png`, `figure_seal_by_payload.png` — 150 dpi  
- `results/plots/table_seal_1kb.tex` — seal at 1 KB payload  

Options: `--raw-dir`, `--plots-dir`.

## Project layout

```
src/main/java/ph/jhury/hpke/
  suites/HpkeSuite.java          # KEM/KDF/AEAD identifiers (JDK + BC)
  crypto/KeyCodec.java          # raw key bytes for interop
  wrappers/                      # Jdk26HpkeWrapper, BcHpkeWrapper
  benchmarks/                  # KeygenBenchmark, SealBenchmark, OpenBenchmark
src/test/java/...               # JUnit 5 interop + round-trip
scripts/env.sh                  # optional JAVA_HOME / Maven PATH
scripts/run_benchmarks.sh       # full JSON benchmark sweep
scripts/analyze.py              # CSV + LaTeX + PNG from JMH JSON
vectors/rfc9180_vectors.json  # placeholder (extend for RFC vector tests)
results/raw/                   # JMH JSON/CSV (gitignored patterns apply)
results/plots/                 # generated figures
```

## License / paper

Benchmark methodology follows JMH best practices (forks, warmups, `Blackhole` consumption). Cite JDK 26 and Bouncy Castle versions used in your paper’s experimental setup section.
