# Cross-Platform Benchmark Comparison: macOS M3 vs Ubuntu x86_64

Synthesises **`results/result.md`** (Apple M3, AArch64) and **`results-ubuntu/result.md`** (Intel i5-13600K, x86_64).
Raw data: `results/raw/*.json` (macOS) and `results-ubuntu/raw/*.json` (Ubuntu).
Full hardware details: [`macos-spec.md`](macos-spec.md) and [`ubuntu-spec.md`](ubuntu-spec.md).

---

## Platform summary

| Field | macOS (M3 Air) | Ubuntu 24.04 LTS |
|-------|---------------|-----------------|
| CPU | Apple M3 (4P+4E cores) | Intel Core i5-13600K (Raptor Lake) |
| Architecture | AArch64 (Apple Silicon) | x86_64 |
| HW crypto (JDK path) | ARM Crypto Ext: AES, PMULL, SHA | AES-NI, VAES, VPCLMULQDQ, SHA-NI |
| RAM | Unified memory (100 GB/s) | ~62 GiB DDR |
| JDK | JDK 26 (SunJCE, AArch64 native) | Oracle JDK 26 (x86_64) |
| BC version | 1.83 | 1.83 |
| JMH (Seal/Keygen) | 3 forks, 5 warmup, 10 measurement | 3 forks, 5 warmup, 10 measurement |
| JMH (Open) | 1 fork, 5 measurement | 1 fork, 2 warmup, 5 measurement |

> **Important:** Absolute latency values are **not directly comparable** — the two machines differ in ISA, microarchitecture, clock speed, and memory subsystem. The meaningful cross-platform signal is the **BC/JDK ratio** (library advantage) and whether the same **crossover patterns** appear on both platforms.

---

## 1. Key Generation

### Absolute latency (µs/op)

| Suite | macOS JDK 26 | Ubuntu JDK 26 | macOS BC | Ubuntu BC |
|-------|-------------:|--------------:|---------:|----------:|
| P256\_SHA256\_A128 | 130.17 | 87.18 | 95.60 | 44.79 |
| P256\_SHA256\_CCP | 132.83 | 86.98 | 96.72 | 44.87 |
| P384\_SHA384\_A256 | 1085.16 | 620.94 | 261.46 | 133.23 |
| X25519\_SHA256\_A128 | 154.74 | 89.49 | 30.14 | 17.94 |
| X25519\_SHA256\_CCP | 155.12 | 89.95 | 30.07 | 18.10 |
| X448\_SHA512\_A256 | 621.61 | 306.07 | 129.82 | 72.81 |

### BC/JDK ratio (< 1 = BC faster)

| Suite | macOS ratio | Ubuntu ratio | Consistent? |
|-------|------------:|-------------:|:-----------:|
| P256\_SHA256\_A128 | 0.73× | 0.51× | ✓ direction |
| P384\_SHA384\_A256 | 0.24× | 0.21× | ✓ |
| X25519\_SHA256\_A128 | 0.19× | 0.20× | ✓ near-identical |
| X448\_SHA512\_A256 | 0.21× | 0.24× | ✓ |

**Findings:**
- BC is faster than JDK 26 for keygen on **both platforms** across all suites.
- **X25519 ratios are nearly identical** across platforms (~0.19–0.20×): the JCA overhead is the dominant cost and is platform-independent.
- **P-256 shows more variance** (macOS 0.73× vs Ubuntu 0.51×): JDK 26 is relatively stronger on M3 for P-256 keygen, possibly reflecting differences in the JDK AArch64 vs x86_64 ECC implementations.
- Ubuntu absolute times are ~1.5–1.7× faster than macOS on both libraries — consistent with the i5-13600K's higher clock speed and hardware crypto throughput.

---

## 2. Seal — Small/Medium Payload (64 B and 1 KB)

KEM cost dominates at these sizes; AEAD overhead is negligible.

### 64 B — BC/JDK ratio comparison

| Suite | macOS BC/JDK | Ubuntu BC/JDK | Consistent? |
|-------|-------------:|--------------:|:-----------:|
| P256\_SHA256\_A128 | 0.37× | 0.24× | ✓ direction |
| P256\_SHA256\_CCP | 0.36× | 0.23× | ✓ |
| X25519\_SHA256\_A128 | 0.45× | 0.44× | ✓ near-identical |
| X25519\_SHA256\_CCP | 0.43× | 0.42× | ✓ near-identical |
| P384\_SHA384\_A256 | 0.27× | 0.20× | ✓ direction |
| X448\_SHA512\_A256 | 0.42× | 0.45× | ✓ |

### 1 KB — BC/JDK ratio comparison

| Suite | macOS BC/JDK | Ubuntu BC/JDK | Consistent? |
|-------|-------------:|--------------:|:-----------:|
| P256\_SHA256\_A128 | 0.38× | 0.24× | ✓ direction |
| P256\_SHA256\_CCP | 0.37× | 0.23× | ✓ |
| X25519\_SHA256\_A128 | 0.48× | 0.47× | ✓ near-identical |
| X25519\_SHA256\_CCP | 0.46× | 0.44× | ✓ |
| P384\_SHA384\_A256 | 0.26× | 0.21× | ✓ |
| X448\_SHA512\_A256 | 0.43× | 0.46× | ✓ |

**Findings:**
- The **direction is consistent on every suite**: BC is faster than JDK 26 at small/medium payloads on both platforms.
- **X25519 ratios are the most portable** (~0.43–0.48×): the result transfers almost exactly across ISAs. For X25519 the KEM cost is proportionally the same fraction of JCA overhead on both CPUs.
- **P-256 and P-384 ratios differ more**: Ubuntu shows a larger BC advantage (~0.20–0.24×) vs macOS (~0.26–0.38×). JDK 26's AArch64 build is relatively more competitive for P-curve operations, likely due to M3's native vectorised ECC path.

---

## 3. Seal — Large Payload (64 KB)

AEAD (encryption) cost begins to dominate; hardware acceleration diverges by platform.

### Absolute latency (µs/op)

| Suite | macOS JDK 26 | Ubuntu JDK 26 | macOS BC | Ubuntu BC |
|-------|-------------:|--------------:|---------:|----------:|
| P256\_SHA256\_A128 | 1120.90 | 703.58 | 1034.64 | 486.92 |
| P256\_SHA256\_A256 | 1050.40 | 706.67 | 1162.47 | 545.62 |
| P256\_SHA256\_CCP | 1132.74 | 790.92 | 921.81 | 403.74 |
| P384\_SHA384\_A256 | 4275.47 | 2463.62 | 1916.33 | 896.29 |
| X25519\_SHA256\_A128 | 338.33 | 198.75 | 793.90 | 405.18 |
| X25519\_SHA256\_A256 | 342.25 | 199.57 | 930.47 | 467.15 |
| X25519\_SHA256\_CCP | 425.46 | 275.03 | 652.64 | 323.14 |
| X448\_SHA512\_A256 | 1269.14 | 638.17 | 1327.36 | 664.73 |

### BC/JDK ratio at 64 KB

| Suite | macOS BC/JDK | Ubuntu BC/JDK | JDK wins on? |
|-------|-------------:|--------------:|:------------:|
| P256\_SHA256\_A128 | **0.92×** (BC just ahead) | **0.69×** (BC ahead) | Neither |
| P256\_SHA256\_A256 | **1.11×** (JDK wins) | **0.77×** (BC ahead) | macOS only |
| P256\_SHA256\_CCP | **0.81×** (BC ahead) | **0.51×** (BC ahead) | Neither |
| P384\_SHA384\_A256 | **0.45×** (BC ahead) | **0.36×** (BC ahead) | Neither |
| X25519\_SHA256\_A128 | **2.35×** (JDK wins) | **2.04×** (JDK wins) | **Both** ✓ |
| X25519\_SHA256\_A256 | **2.72×** (JDK wins) | **2.34×** (JDK wins) | **Both** ✓ |
| X25519\_SHA256\_CCP | **1.53×** (JDK wins) | **1.17×** (JDK wins) | **Both** ✓ |
| X448\_SHA512\_A256 | **1.05×** (JDK just ahead) | **1.04×** (JDK just ahead) | **Both** ✓ |

**Findings:**

### 3a. X25519 AES crossover — consistent on both platforms
JDK 26 decisively outperforms BC at 64 KB for **X25519 AES-GCM** on both M3 and x86_64. The mechanism is different but the outcome is the same:
- **macOS (M3):** SunJCE uses NEON + ARM AES/PMULL intrinsics; BC uses pure-Java AES-GCM
- **Ubuntu (i5-13600K):** SunJCE uses AES-NI + VPCLMULQDQ; BC uses pure-Java AES-GCM
- macOS JDK advantage is slightly larger (A128: 2.35× vs 2.04×), but both cross the 2× threshold — this is a **robust, platform-independent finding**.

### 3b. P-256 crossover — platform-dependent
The P-256 64 KB result **differs across platforms**:
- **macOS:** JDK 26 wins A256 (1.11×); A128 is near-parity (0.92×). NEON acceleration brings JDK's AEAD cost low enough to overcome its KEM overhead.
- **Ubuntu:** BC wins at all P-256 payloads including 64 KB (0.69–0.77×). The KEM overhead gap between JDK and BC is smaller (~4.25× vs ~2.71×) so the AES-NI payoff at 64 KB is not enough to flip the result on x86_64 with this JDK build.

### 3c. ChaCha20-Poly1305 crossover — consistent
JDK 26 wins CCP at 64 KB on both platforms, but with a smaller margin than AES-GCM (macOS 1.53×, Ubuntu 1.17×). SunJCE has hardware ChaCha acceleration on both AArch64 and x86_64 in JDK 26.

### 3d. X448 — near-parity on both
X448 at 64 KB is essentially tied on both platforms (macOS 1.05×, Ubuntu 1.04×), consistent with the KEM dominating and AEAD overhead being similar.

---

## 4. Open — Cross-Platform Summary

Open mirrors Seal directionally on both platforms.

### BC/JDK ratio comparison (select suites)

| Suite & Payload | macOS BC/JDK | Ubuntu BC/JDK | Consistent? |
|----------------|-------------:|--------------:|:-----------:|
| P256\_A128, 64 B | 0.26× | 0.17× | ✓ direction |
| P384\_A256, 64 B | 0.18× | 0.15× | ✓ |
| X25519\_A128, 64 B | 0.36× | 0.35× | ✓ near-identical |
| X25519\_CCP, 64 B | 0.34× | 0.34× | ✓ identical |
| X25519\_A128, 64 KB | **2.50×** (JDK wins) | **1.88×** (JDK wins) | ✓ both JDK |
| X25519\_CCP, 64 KB | **1.02×** (near parity) | **1.43×** (JDK wins) | ✓ direction |
| P384\_A256, 64 KB | 0.34× | 0.29× | ✓ |
| X448\_A256, 64 KB | **0.92×** (near parity) | **0.90×** (near parity) | ✓ near-identical |

**Finding:** X25519 open ratios are highly portable across platforms — nearly identical at small payloads (~0.34–0.36×), and JDK 26 wins at 64 KB on both platforms. The magnitude of JDK's advantage at 64 KB is larger on macOS (~2.50×) than Ubuntu (~1.88×), reflecting the stronger relative NEON AES-GCM acceleration on M3.

---

## 5. Memory Allocation (gc_alloc_rate_norm)

| Operation | macOS JDK 26 | Ubuntu JDK 26 | macOS BC | Ubuntu BC | Winner |
|-----------|-------------:|--------------:|---------:|----------:|--------|
| P256 keygen | ~16–17 KB/op | ~16.5 KB/op | ~50 KB/op | ~51 KB/op | **JDK 26** (both) |
| X25519 keygen | ~6 KB/op | ~6.2 KB/op | ~3 KB/op | ~3.1 KB/op | **BC** (both) |
| P256 seal 64 B | ~87–89 KB/op | ~88–89 KB/op | ~206–216 KB/op | ~206–216 KB/op | **JDK 26** (both) |
| X25519 seal 64 B | ~32–34 KB/op | ~33–34 KB/op | ~15–25 KB/op | ~15–25 KB/op | **BC** (both) |
| P384 seal 1 KB | ~134 KB/op | ~134 KB/op | ~1063 KB/op | ~1063 KB/op | **JDK 26** (both, 8×) |
| P256 seal 64 KB | ~153 KB/op | ~153 KB/op | ~412 KB/op | ~412 KB/op | **JDK 26** (both) |
| X25519 seal 64 KB | ~98 KB/op | ~98 KB/op | ~222 KB/op | ~222 KB/op | **JDK 26** (both) |

**Finding:** Memory allocation profiles are **nearly identical across platforms**. This is expected — allocation is driven by JCA/BC object creation patterns and JMH harness internals, not by hardware. The JDK 26 vs BC memory ranking is fully reproducible across both machines.

---

## 6. Key Cross-Platform Findings

### Finding A — BC/JDK ratios are portable for X25519
X25519 seal BC/JDK ratios at 64 B and 1 KB differ by < 3% between macOS and Ubuntu. Any paper claiming BC is ~2–2.3× faster than JDK 26 for X25519 HPKE seal at small payloads can cite both platforms as corroboration.

### Finding B — Hardware AES crossover is universal for X25519 AES-GCM
JDK 26 outperforms BC at 64 KB for X25519 AES-GCM on **both** M3 (NEON AES) and i5-13600K (AES-NI). The JDK advantage is ~2× on Ubuntu and ~2.4× on macOS. This is a robust, platform-independent result attributable to SunJCE hardware AEAD intrinsics vs BC's pure-Java implementation.

### Finding C — P-256 crossover is platform-dependent
The 64 KB P-256 crossover only appears on macOS (JDK wins A256). On Ubuntu, BC remains faster for P-256 at all payload sizes. **Do not generalize** the macOS P-256 finding to x86_64 without qualification.

### Finding D — Absolute performance: Ubuntu faster on this hardware pair
The i5-13600K produces ~1.5–1.7× lower absolute latency than the M3 Air for both libraries on most operations. This reflects CPU clock speed, memory bandwidth, and microarchitecture differences — **not a library quality difference**. For the paper, report ratios rather than absolute times when making cross-platform claims.

### Finding E — Memory allocation is hardware-independent
GC allocation rates per operation are identical to within measurement noise across platforms. Hardware has no effect on this metric for pure JVM workloads.

### Finding F — P-384 is consistently JDK 26's weakest point
JDK 26 is ~4–5× slower than BC for P-384 on **both** platforms. This finding is fully portable and can be stated as a platform-independent conclusion.

---

## 7. Summary: What Transfers Across Platforms

| Claim | Transfers? | Notes |
|-------|:----------:|-------|
| BC faster at small/medium seal (all suites) | ✓ Yes | Ratios differ; direction universal |
| X25519 ratios ~0.44× at 64 B seal | ✓ Yes | < 3% difference |
| JDK 26 wins X25519 AES at 64 KB | ✓ Yes | Magnitude varies (~2× Ubuntu, ~2.4× macOS) |
| JDK 26 wins P-256 AES at 64 KB | ✗ No | macOS only; Ubuntu BC still leads |
| JDK 26 wins CCP at 64 KB | ✓ Yes | Larger margin on macOS |
| P-384 JDK 26 is ~4× slower than BC | ✓ Yes | Consistent on both |
| Memory: JDK 26 leaner for P-curve | ✓ Yes | Nearly identical allocation |
| Memory: BC leaner for X25519 small seal | ✓ Yes | Consistent |
| Interoperability (RFC 9180 round-trip) | ✓ Yes | Tests pass on both platforms |
