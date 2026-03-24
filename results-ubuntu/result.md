# Benchmark Results & Interpretation (Ubuntu)
**Platform:** Ubuntu 24.04 LTS, x86_64, JDK 26, Bouncy Castle 1.83  
**Method:** JMH 1.37, average time (µs/op), same suite list as `results/result.md`  
**Data:** `results-ubuntu/raw/*.json` (KeygenBenchmark, SealBenchmark). **OpenBenchmark** was not available or failed to export for this run — see note in §3.

---

## 1. Key Generation (KeygenBenchmark)

| Suite | JDK 26 (µs/op) | BC (µs/op) | BC/JDK |
|-------|---------------:|----------:|--------|
| P256_SHA256_A128 | 87.18 | 44.79 | **0.51× (BC 1.95× faster)** |
| P256_SHA256_A256 | 87.12 | 45.03 | **0.52× (BC 1.93× faster)** |
| P256_SHA256_CCP | 86.98 | 44.87 | **0.52× (BC 1.94× faster)** |
| P384_SHA384_A256 | 620.94 | 133.23 | **0.21× (BC 4.66× faster)** |
| X25519_SHA256_A128 | 89.49 | 17.94 | **0.20× (BC 4.99× faster)** |
| X25519_SHA256_A256 | 89.38 | 17.82 | **0.20× (BC 5.01× faster)** |
| X25519_SHA256_CCP | 89.95 | 18.10 | **0.20× (BC 4.97× faster)** |
| X448_SHA512_A256 | 306.07 | 72.81 | **0.24× (BC 4.20× faster)** |

**Note:** A BC/JDK ratio below 1 means BC is faster (same convention as `results/result.md`).


---

## 2a. Seal @ 64 B (SealBenchmark)

| Suite | JDK 26 (µs/op) | BC (µs/op) | BC/JDK |
|-------|---------------:|----------:|--------|
| P256_SHA256_A128 | 690.27 | 162.26 | **0.24× (BC 4.25× faster)** |
| P256_SHA256_A256 | 690.10 | 162.35 | **0.24× (BC 4.25× faster)** |
| P256_SHA256_CCP | 693.80 | 158.70 | **0.23× (BC 4.37× faster)** |
| P384_SHA384_A256 | 2456.65 | 500.95 | **0.20× (BC 4.90× faster)** |
| X25519_SHA256_A128 | 187.19 | 82.41 | **0.44× (BC 2.27× faster)** |
| X25519_SHA256_A256 | 188.46 | 82.27 | **0.44× (BC 2.29× faster)** |
| X25519_SHA256_CCP | 186.10 | 78.98 | **0.42× (BC 2.36× faster)** |
| X448_SHA512_A256 | 622.77 | 280.19 | **0.45× (BC 2.22× faster)** |

---

## 2b. Seal @ 1 KB (SealBenchmark)

| Suite | JDK 26 (µs/op) | BC (µs/op) | BC/JDK |
|-------|---------------:|----------:|--------|
| P256_SHA256_A128 | 689.82 | 168.46 | **0.24× (BC 4.09× faster)** |
| P256_SHA256_A256 | 690.11 | 168.81 | **0.24× (BC 4.09× faster)** |
| P256_SHA256_CCP | 694.58 | 161.69 | **0.23× (BC 4.30× faster)** |
| P384_SHA384_A256 | 2457.58 | 509.20 | **0.21× (BC 4.83× faster)** |
| X25519_SHA256_A128 | 186.96 | 87.23 | **0.47× (BC 2.14× faster)** |
| X25519_SHA256_A256 | 186.16 | 87.97 | **0.47× (BC 2.12× faster)** |
| X25519_SHA256_CCP | 187.92 | 82.74 | **0.44× (BC 2.27× faster)** |
| X448_SHA512_A256 | 624.33 | 286.52 | **0.46× (BC 2.18× faster)** |

---

## 2c. Seal @ 64 KB (SealBenchmark)

| Suite | JDK 26 (µs/op) | BC (µs/op) | BC/JDK |
|-------|---------------:|----------:|--------|
| P256_SHA256_A128 | 703.58 | 486.92 | **0.69× (BC 1.44× faster)** |
| P256_SHA256_A256 | 706.67 | 545.62 | **0.77× (BC 1.30× faster)** |
| P256_SHA256_CCP | 790.92 | 403.74 | **0.51× (BC 1.96× faster)** |
| P384_SHA384_A256 | 2463.62 | 896.29 | **0.36× (BC 2.75× faster)** |
| X25519_SHA256_A128 | 198.75 | 405.18 | **2.04× (JDK 2.04× faster)** |
| X25519_SHA256_A256 | 199.57 | 467.15 | **2.34× (JDK 2.34× faster)** |
| X25519_SHA256_CCP | 275.03 | 323.14 | **1.17× (JDK 1.17× faster)** |
| X448_SHA512_A256 | 638.17 | 664.73 | **1.04× (JDK 1.04× faster)** |

---

## 3. Open (OpenBenchmark)

> **Not included:** `results-ubuntu/raw/OpenBenchmark.json` is missing or empty. Re-run JMH for `ph.jhury.hpke.benchmarks.OpenBenchmark` and regenerate this section.


---

## 4. How this file was produced

Regenerate after new runs: parse `results-ubuntu/raw/*.json` or run `python3 scripts/analyze.py` (plots/CSVs) and update this narrative manually if needed.
