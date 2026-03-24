# Benchmark Results & Interpretation (Ubuntu)

**Platform:** Ubuntu 24.04 LTS, x86\_64, JDK 26, Bouncy Castle 1.83  
**Method:** JMH 1.37, average time (µs/op), GC allocation rate (B/op) via `-prof gc` (`gc.alloc.rate.norm`)  
**Suites tested:** P-256/SHA-256/{AES-128-GCM, AES-256-GCM, ChaCha20-Poly1305}, P-384/SHA-384/AES-256-GCM, X25519/SHA-256/{AES-128-GCM, AES-256-GCM, ChaCha20-Poly1305}, X448/SHA-512/AES-256-GCM  
**Payload sizes (seal/open):** 64 B, 1 KB, 64 KB  
**Raw data:** `results-ubuntu/raw/*.json` (see `.gitignore`; regenerate with `scripts/run_benchmarks.sh`). Full GC tables: `results-ubuntu/plots/memory_allocation_ubuntu.md`.

---

## 1. Key Generation (KeygenBenchmark)

| Suite | JDK 26 (µs/op) | BC (µs/op) | BC/JDK ratio |
|-------|---------------:|-----------:|-------------|
| P256\_SHA256\_A128 | 87.18 | 44.79 | **0.51× (BC 1.95× faster)** |
| P256\_SHA256\_A256 | 87.12 | 45.03 | **0.52×** |
| P256\_SHA256\_CCP | 86.98 | 44.87 | **0.52×** |
| P384\_SHA384\_A256 | 620.94 | 133.23 | **0.21× (BC 4.66× faster)** |
| X25519\_SHA256\_A128 | 89.49 | 17.94 | **0.20× (BC 4.99× faster)** |
| X25519\_SHA256\_A256 | 89.38 | 17.82 | **0.20×** |
| X25519\_SHA256\_CCP | 89.95 | 18.10 | **0.20×** |
| X448\_SHA512\_A256 | 306.07 | 72.81 | **0.24× (BC 4.20× faster)** |

**Finding:** Bouncy Castle key generation is faster than JDK 26 on every suite in this run. The gap is largest for **X25519 (~5×)** and **P-384 (~4.7×)**; for **P-256**, BC is still about **2×** faster. The same interpretation as on macOS applies: JCA `KeyPairGenerator` / provider dispatch adds per-call overhead that BC’s lightweight API avoids.

**Memory note:** For P-256 keygen, BC allocates about **51 KB/op** vs JDK **~16.5 KB/op** (BC moves more bytes despite lower latency). For X25519, BC is about **~3.1 KB/op** vs JDK **~6.2 KB/op**. (Source: `memory_allocation_ubuntu.md`.)

---

## 2. Seal (SealBenchmark)

### 2a. Small payload (64 B) — KEM cost dominates

| Suite | JDK 26 (µs/op) | BC (µs/op) | BC/JDK ratio |
|-------|---------------:|-----------:|-------------|
| P256\_SHA256\_A128 | 690.27 | 162.26 | **0.24× (BC 4.25× faster)** |
| P256\_SHA256\_A256 | 690.10 | 162.35 | **0.24×** |
| P256\_SHA256\_CCP | 693.80 | 158.70 | **0.23×** |
| P384\_SHA384\_A256 | 2456.65 | 500.95 | **0.20× (BC 4.90× faster)** |
| X25519\_SHA256\_A128 | 187.19 | 82.41 | **0.44× (BC 2.27× faster)** |
| X25519\_SHA256\_A256 | 188.46 | 82.27 | **0.44×** |
| X25519\_SHA256\_CCP | 186.10 | 78.98 | **0.42×** |
| X448\_SHA512\_A256 | 622.77 | 280.19 | **0.45× (BC 2.22× faster)** |

### 2b. Medium payload (1 KB) — representative real-world use

| Suite | JDK 26 (µs/op) | BC (µs/op) | BC/JDK ratio |
|-------|---------------:|-----------:|-------------|
| P256\_SHA256\_A128 | 689.82 | 168.46 | **0.24×** |
| P256\_SHA256\_A256 | 690.11 | 168.81 | **0.24×** |
| P256\_SHA256\_CCP | 694.58 | 161.69 | **0.23×** |
| P384\_SHA384\_A256 | 2457.58 | 509.20 | **0.21×** |
| X25519\_SHA256\_A128 | 186.96 | 87.23 | **0.47×** |
| X25519\_SHA256\_A256 | 186.16 | 87.97 | **0.47×** |
| X25519\_SHA256\_CCP | 187.92 | 82.74 | **0.44×** |
| X448\_SHA512\_A256 | 624.33 | 286.52 | **0.46×** |

### 2c. Large payload (64 KB) — AEAD cost dominates

| Suite | JDK 26 (µs/op) | BC (µs/op) | BC/JDK ratio |
|-------|---------------:|-----------:|-------------|
| P256\_SHA256\_A128 | 703.58 | 486.92 | **0.69× (BC 1.44× faster)** |
| P256\_SHA256\_A256 | 706.67 | 545.62 | **0.77×** |
| P256\_SHA256\_CCP | 790.92 | 403.74 | **0.51×** |
| P384\_SHA384\_A256 | 2463.62 | 896.29 | **0.36×** |
| X25519\_SHA256\_A128 | 198.75 | 405.18 | **2.04× (JDK 26 2.04× faster)** |
| X25519\_SHA256\_A256 | 199.57 | 467.15 | **2.34×** |
| X25519\_SHA256\_CCP | 275.03 | 323.14 | **1.17×** |
| X448\_SHA512\_A256 | 638.17 | 664.73 | **1.04× (JDK 26 slightly ahead)** |

**Crossover (x86\_64):** For **X25519** with **AES-GCM**, JDK 26 is faster than BC at **64 KB** (e.g. A128: **~199 µs** vs **~405 µs**), while BC is faster at **64 B / 1 KB**. BC’s seal time grows much more with payload than JDK’s on this machine. For **P-256**, BC remains faster even at **64 KB** in this run (unlike the M3 macOS data, where P-256 becomes nearly even or JDK wins A256). **P-384** remains KEM-dominated; BC stays faster at all payload sizes here.

---

## 3. Open (OpenBenchmark)

> **Not included for this Ubuntu run:** `results-ubuntu/raw/OpenBenchmark.json` was missing or empty when this document was last aligned. On macOS (`results/result.md`), Open used a **lighter JMH configuration** (1 fork, 5 iterations) than Seal/Keygen (3 forks, 10 iterations).

When Open results exist, mirror the macOS layout:

### 64 B payload

*(No data — run `ph.jhury.hpke.benchmarks.OpenBenchmark` and re-run `scripts/analyze.py`.)*

### 64 KB payload

*(No data.)*

**Finding (placeholder):** Once Open JSON is available, expect a pattern similar to Seal: BC ahead at small payloads; JDK 26 competitive or ahead at 64 KB for X25519 AES suites on x86\_64.

---

## 4. Memory Allocation (gc\_alloc\_rate\_norm)

Summary derived from **`results-ubuntu/plots/memory_allocation_ubuntu.md`** (JMH `gc.alloc.rate.norm`, B/op):

| Operation | JDK 26 typical | BC typical | Winner |
|-----------|---------------:|-----------:|--------|
| P256 keygen | ~16.5 KB/op | ~51 KB/op | **JDK 26** |
| X25519 keygen | ~6.2 KB/op | ~3.1 KB/op | **BC** |
| P256 seal 64 B | ~88–89 KB/op | ~206–216 KB/op | **JDK 26** |
| X25519 seal 64 B | ~33–34 KB/op | ~15–25 KB/op | **BC** |
| P384 seal 1 KB | ~134 KB/op | **~1,063 KB/op** | **JDK 26** |
| P256 seal 64 KB | ~153 KB/op | **~413 KB/op** | **JDK 26** |
| X25519 seal 64 KB (A256) | ~98 KB/op | **~222 KB/op** | **JDK 26** |

**Finding:** JDK 26 allocates far less than BC for **P-384 seal** and for **P-256 seal** at medium/large payloads. For **X25519** at **small** seal payloads, BC allocates less; by **64 KB**, JDK’s allocation stays lower than BC’s for the AES-GCM suites measured.

---

## 5. Suite-Level Ranking (by seal latency at 1 KB)

Ranked fastest to slowest by **BC** time (then JDK for reference):

1. **X25519\_SHA256\_CCP** — BC: 82.7 µs, JDK 26: 187.9 µs  
2. **X25519\_SHA256\_A128** — BC: 87.2 µs, JDK 26: 187.0 µs  
3. **X25519\_SHA256\_A256** — BC: 88.0 µs, JDK 26: 186.2 µs  
4. **P256\_SHA256\_CCP** — BC: 161.7 µs, JDK 26: 694.6 µs  
5. **P256\_SHA256\_A128** — BC: 168.5 µs, JDK 26: 689.8 µs  
6. **P256\_SHA256\_A256** — BC: 168.8 µs, JDK 26: 690.1 µs  
7. **X448\_SHA512\_A256** — BC: 286.5 µs, JDK 26: 624.3 µs  
8. **P384\_SHA384\_A256** — BC: 509.2 µs, JDK 26: 2457.6 µs  

X25519 suites are several times faster than P-256 for seal at 1 KB on both libraries. P-384 remains the slowest suite, especially on JDK 26 (~2.5 ms vs ~0.5 ms BC).

---

## 6. Main Findings & Contributions (Ubuntu / x86\_64)

### Finding 1 — JCA overhead hurts JDK 26 at small and medium seal sizes

At **64 B** and **1 KB**, BC seals **P-256** in roughly **~160–170 µs** vs JDK **~690 µs** (about **4×**). **X25519** at 1 KB: BC **~83–88 µs** vs JDK **~186–188 µs** (**~2.1–2.3×**). This aligns with provider / `Cipher` API cost on top of the KEM+AEAD work.

### Finding 2 — JDK 26 leads on X25519 AES at 64 KB on this x86\_64 box

For **X25519\_SHA256\_A128** at 64 KB: JDK **~199 µs** vs BC **~405 µs** (**~2×**). SunJCE still benefits from **hardware-accelerated AES-GCM** on x86\_64; BC’s Java AEAD path scales up more with payload. **ChaCha20-Poly1305** (**CCP**) at 64 KB is closer (**JDK ~275 µs** vs **BC ~323 µs**).

### Finding 3 — P-256 at 64 KB still favors BC here

Unlike the M3 macOS run, **P-256** seal at **64 KB** remains **faster in BC** on this Ubuntu data. Platform and JDK build differences can change the crossover point; document **CPU model**, **JDK vendor/build**, and **JMH forks** when comparing across machines.

### Finding 4 — P-384

JDK 26 **~2.46 ms** vs BC **~0.51 ms** per seal at 1 KB (**~4.8×**). BC is preferable when P-384 is required and raw latency matters.

### Finding 5 — Allocation vs latency

BC often wins on **time** for small/medium operations but can allocate **much more** (notably **P-384 seal**, **~1 MB/op** vs **~134 KB/op** at 1 KB). JDK 26 is preferable under tight **heap / GC** budgets for those paths.

### Finding 6 — Interoperability

Same as macOS: **JUnit** interop tests in the repo confirm cross-library HPKE bytes; cite the same methodology in the paper.

---

## 7. Summary Table

| Metric | Winner (small payload ≤1 KB) | Winner (large payload 64 KB) |
|--------|----------------------------|------------------------------|
| Seal latency — P256 | BC (~4×) | **BC** (~1.3–1.4×; ChaCha ~2×) |
| Seal latency — X25519 AES | BC (~2.1×) | **JDK 26 (~2×)** |
| Seal latency — X25519 CCP | BC (~2.3×) | JDK 26 (modest) |
| Open latency — X25519 | *Not measured* | *Not measured* |
| Keygen | BC (~2–5×) | BC (~2–5×) |
| GC alloc (P-curve) | JDK 26 | **JDK 26** |
| GC alloc (X-curve small seal) | BC | JDK 26 (AES 64 KB) |
| P-384 seal | BC (~4.8× @ 1 KB) | BC (~2.8×) |
| Interoperability | ✓ Both (tests) | ✓ Both (tests) |

---

## 8. How this file was produced

Tables §1–§2 match **`results-ubuntu/raw/KeygenBenchmark.json`** and **`SealBenchmark.json`** (JMH primary metric, µs/op). §4 matches **`memory_allocation_ubuntu.md`** from `python3 scripts/analyze.py --raw-dir results-ubuntu/raw --plots-dir results-ubuntu/plots`. After new benchmark runs, regenerate analysis artifacts and update this narrative.
