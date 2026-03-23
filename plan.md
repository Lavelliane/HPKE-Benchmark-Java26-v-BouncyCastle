## Project Architecture & Implementation Plan
### HPKE Benchmark: JDK 26 Native vs Bouncy Castle 1.83

---

## Tech Stack & Prerequisites

| Component | Choice | Reason |
|---|---|---|
| JDK | Oracle JDK 26 (March 2026 GA) | Native HPKE support |
| Build | Maven 3.9+ | JMH archetype support, cleaner than Gradle for benchmarks |
| Benchmark | JMH 1.37 | Industry standard for JVM microbenchmarks |
| Crypto lib | Bouncy Castle 1.83 (`bcprov-jdk18on`) | Latest, has HPKE lightweight API |
| Test vectors | RFC 9180 Appendix A | Official compliance source |
| Analysis | Python 3 + pandas + matplotlib | Post-processing JMH JSON output |
| IDE | Cursor | As specified |

**M3-specific note**: AArch64 JVM. JDK 26's SunJCE has AES-GCM intrinsics on AArch64 — this is a meaningful hardware advantage worth calling out in the paper. NEON SIMD will accelerate both AES and SHA operations. BC's pure Java path will not benefit the same way.

---

## Repository Structure

```
hpke-benchmark/
├── pom.xml                          # Parent POM, JMH + BC deps
├── src/
│   ├── main/java/
│   │   └── ph/jhury/hpke/
│   │       ├── suites/
│   │       │   └── HpkeSuite.java   # Enum of all tested suites
│   │       ├── wrappers/
│   │       │   ├── HpkeWrapper.java       # Common interface
│   │       │   ├── Jdk26HpkeWrapper.java  # JDK 26 impl
│   │       │   └── BcHpkeWrapper.java     # Bouncy Castle impl
│   │       └── vectors/
│   │           └── Rfc9180TestVector.java # POJO for test vectors
│   ├── test/java/
│   │   └── ph/jhury/hpke/
│   │       └── interop/
│   │           └── InteropTest.java       # Experiment B
│   └── benchmarks/java/
│       └── ph/jhury/hpke/
│           ├── KeygenBenchmark.java
│           ├── SealBenchmark.java
│           └── OpenBenchmark.java
├── vectors/
│   └── rfc9180_vectors.json         # Test vectors from RFC 9180 Appendix A
├── results/
│   ├── raw/                         # JMH JSON output dumps
│   └── plots/                       # Generated figures
└── scripts/
    ├── run_benchmarks.sh            # Orchestration script
    └── analyze.py                   # Result processing + plot generation
```

---

## Core Design Decisions

### 1. The Wrapper Interface Pattern

The entire benchmark pivots on a single `HpkeWrapper` interface that both JDK 26 and BC implement identically. This is the most important design decision — it ensures you're benchmarking the same operations, not different interpretations of HPKE.

The interface exposes exactly four operations:
- `generateKeyPair(suite)` → keypair
- `seal(suite, recipientPubKey, plaintext, aad, info)` → (enc, ciphertext)
- `open(suite, recipientPrivKey, enc, ciphertext, aad, info)` → plaintext
- `exportSecret(suite, context, exporterContext, length)` → bytes

Both wrappers must accept and return plain byte arrays at the boundary. No library-specific types leak across the interface. This is what makes the interop test in Experiment B elegant — you literally pass BC's output bytes directly into the JDK 26 wrapper's `open()`.

### 2. Suite Enum as the Control Variable

`HpkeSuite` is a typed enum that pairs a human-readable label with the three numeric identifiers (KEM\_id, KDF\_id, AEAD\_id) from RFC 9180. All benchmarks are parameterized over this enum using JMH's `@Param`. This means:

- One benchmark class covers all suite combinations automatically
- Adding or removing a suite is a one-line change
- The suite label appears directly in JMH output, making result parsing trivial

**Suites to include** (8 total — enough for meaningful analysis, fits in one table):

| Label | KEM | KDF | AEAD |
|---|---|---|---|
| P256-SHA256-A128 | DHKEM(P-256) | HKDF-SHA256 | AES-128-GCM |
| P256-SHA256-A256 | DHKEM(P-256) | HKDF-SHA256 | AES-256-GCM |
| P256-SHA256-CCP | DHKEM(P-256) | HKDF-SHA256 | ChaCha20Poly1305 |
| X25519-SHA256-A128 | DHKEM(X25519) | HKDF-SHA256 | AES-128-GCM |
| X25519-SHA256-A256 | DHKEM(X25519) | HKDF-SHA256 | AES-256-GCM |
| X25519-SHA256-CCP | DHKEM(X25519) | HKDF-SHA256 | ChaCha20Poly1305 |
| P384-SHA384-A256 | DHKEM(P-384) | HKDF-SHA384 | AES-256-GCM |
| X448-SHA512-A256 | DHKEM(X448) | HKDF-SHA512 | AES-256-GCM |

X25519-SHA256-A128 is the most common real-world suite (used in ECH, MLS). P384 and X448 give you the "heavier curve" data points.

---

## Experiment A — Benchmark Flow

### JMH Configuration

```
Warmup:       5 iterations × 3 seconds
Measurement:  10 iterations × 3 seconds
Forks:        3 (separate JVM processes)
Mode:         AverageTime
TimeUnit:     microseconds (keygen, seal) / nanoseconds (open for small payloads)
Threads:      1 (single-threaded — you want latency, not throughput)
GC profiler:  enabled (captures allocation rate per op)
```

Forks = 3 is important. Each fork is a fresh JVM with cold JIT. This catches JIT compilation variance, which matters especially for comparing a JCA path (JDK 26, goes through SunJCE provider dispatch) vs BC's direct lightweight API (no JCA indirection).

### Three Benchmark Classes

**KeygenBenchmark**
- `@Param`: suite (all 8), library (JDK26, BC)
- `@State(Scope.Thread)`: pre-initialized wrapper instance
- Measures: `wrapper.generateKeyPair(suite)` only
- Why separate: keygen cost is amortized in practice but matters for session setup latency analysis

**SealBenchmark**
- `@Param`: suite (all 8), library (JDK26, BC), payloadSize (64, 1024, 65536)
- `@State(Scope.Thread)`: pre-generated recipient keypair, pre-filled payload byte array, fixed `info` and `aad` bytes
- Measures: `wrapper.seal(...)` only
- The keypair must be pre-generated in `@Setup` so you're not measuring keygen inside seal

**OpenBenchmark**
- `@Param`: suite (all 8), library (JDK26, BC), payloadSize (64, 1024, 65536)
- `@State(Scope.Thread)`: pre-generated recipient keypair + pre-sealed (enc, ciphertext) pairs
- Measures: `wrapper.open(...)` only
- Pre-seal in `@Setup` — the sealed payload is ready before the benchmark clock starts

### State Management (Critical Detail)

The `@State` objects for Seal and Open benchmarks must pre-generate keys and ciphertexts in `@Setup(Level.Trial)` — meaning once per fork, not once per invocation. This is the correct level because you want the same keys across all measurement iterations within a fork (realistic), but fresh keys across forks (avoids JIT over-specializing on a single key).

For OpenBenchmark specifically: pre-seal a batch of 100 ciphertexts in `@Setup`, then round-robin through them per invocation using an `AtomicInteger` counter. This prevents the JVM from dead-code-eliminating the open operation (since each input is different) while keeping setup cost out of the measurement window.

---

## Experiment B — Interoperability Flow

This runs as standard JUnit 5 tests, not JMH benchmarks. Fast to run, deterministic, binary pass/fail output.

### Test Vector Source

RFC 9180 Appendix A contains official test vectors for each KEM variant. Each vector specifies:
- `ikm_R` (recipient key material), `ikm_E` (ephemeral key material)
- `enc` (encapsulated key), `shared_secret`
- `key_schedule_context`, `secret`, `key`, `base_nonce`
- Sequence of (aad, plaintext, ciphertext) tuples

Store these as a JSON file in `vectors/rfc9180_vectors.json`. Load via Jackson in test setup. This keeps the test data separate from code and makes it easy to add vectors later.

### Four Test Categories

**Test 1 — RFC Vector Compliance (JDK 26)**
For each vector: feed the specified ikm into JDK 26 wrapper, verify the derived key material and encrypted outputs match the vector exactly. This confirms JDK 26 is spec-compliant.

**Test 2 — RFC Vector Compliance (BC 1.83)**
Same as Test 1 but using the BC wrapper. Establishes BC as the compliance baseline.

**Test 3 — Cross-Library Seal→Open (JDK 26 seals, BC opens)**
Generate a fresh keypair using JDK 26. Seal with JDK 26. Pass raw bytes (enc + ciphertext) to BC's open. Assert plaintext equality. Run for all 8 suites × 3 payload sizes.

**Test 4 — Cross-Library Seal→Open (BC seals, JDK 26 opens)**
Inverse of Test 3. Generate keypair with BC. Seal with BC. Open with JDK 26. Assert plaintext equality.

### Assertion Design

Beyond plaintext equality, also assert:
- `enc` length matches the KEM's `Nenc` value from RFC 9180 Table 2
- Ciphertext length equals plaintext length + AEAD tag length (Nt = 16 for all standard AEADs)
- A tampered ciphertext (flip one byte) causes `open()` to throw, not return garbage — this verifies AEAD authentication is active in both libraries

---

## Execution Flow

```
Phase 1 — Interop Tests (fast, ~2 minutes)
  mvn test
  → junit reports to target/surefire-reports/
  → All 4 test categories, all 8 suites

Phase 2 — Benchmarks (slow, ~90 minutes total)
  ./scripts/run_benchmarks.sh
  → Runs KeygenBenchmark, SealBenchmark, OpenBenchmark sequentially
  → Each outputs JSON to results/raw/*.json
  → Close all other apps, plug in power, disconnect wifi before running

Phase 3 — Analysis (~5 minutes)
  python3 scripts/analyze.py
  → Reads all JSON from results/raw/
  → Generates Table 1 (latency comparison CSV)
  → Generates Figure 2 (bar chart: seal latency by payload × library)
  → Outputs to results/plots/
```

`run_benchmarks.sh` should pass `-prof gc` to JMH to capture GC allocation stats, and output with `-rf json`. That JSON is structured and easy to parse in Python — each result entry has the benchmark name, params, score, and error already broken out.

---

## Analysis Script Design

The Python script does three things:

**1. Parse & Flatten**: Load all JMH JSON output files, flatten into a pandas DataFrame with columns: `benchmark`, `library`, `suite`, `payloadSize`, `score_us`, `error_us`, `alloc_rate_mb_per_op`.

**2. Generate Table 1**: Pivot the DataFrame to produce the paper's main result table. Group by suite, show JDK 26 vs BC side-by-side for seal at 1KB payload (the representative case). Export as CSV and also as a LaTeX table snippet directly.

**3. Generate Figure 2**: Bar chart with grouped bars — x-axis is payload size (64B, 1KB, 64KB), two bars per group (JDK 26, BC), one subfigure per suite variant. Use a 2×4 subplot grid. Export as PDF (vector) at 300dpi for IEEE submission.

---

## M3-Specific Considerations for the Paper

These are worth one paragraph in your setup section:

- **AArch64 AES intrinsics**: JDK 26's SunJCE uses `AESENC`/`AESGCM` ARM intrinsics. BC's lightweight API is pure Java — no intrinsic path. This means AES-GCM suites likely favor JDK 26 more than ChaCha20Poly1305 suites, which is an interesting finding.
- **Unified memory**: M3's unified memory means no NUMA effects, which simplifies interpretation — latency variance will be lower than on a typical x86 server.
- **Efficiency cores**: JMH's `Threads = 1` will pin to a performance core. Make sure to document that you didn't explicitly set core affinity (you can't easily on macOS), but variance across forks handles this stochastically.
- **JDK build**: Verify you're running the AArch64 native build of JDK 26, not Rosetta. `java -version` output should say `aarch64`.

---
