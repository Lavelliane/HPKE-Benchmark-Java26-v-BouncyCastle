# macOS benchmark host — hardware & OS

Recorded from the machine used for **`results/`** JMH runs.

## Operating system

| Field | Value |
|-------|--------|
| Device | MacBook Air (M3, 2024) |
| Architecture | AArch64 (Apple Silicon) |

## CPU

| Field | Value |
|-------|--------|
| Chip | Apple M3 |
| CPU cores | 8-core (4 performance + 4 efficiency) |
| GPU cores | 10-core (integrated) |
| Neural Engine | 16-core |
| Memory bandwidth | 100 GB/s |
| ISA (relevant to crypto) | ARMv8.4-A; **ARM Cryptography Extensions** (`AES`, `PMULL`, `SHA-256`, `SHA-512`), **NEON** (Advanced SIMD) — used by JDK 26 SunJCE for hardware AES-GCM on AArch64 |

## Memory

| Field | Value |
|-------|--------|
| RAM | Unified memory (8 / 16 / 24 GB depending on configuration) |

## Paper / methodology snippet

> Benchmarks on **macOS**, **Apple M3 MacBook Air (2024)** (AArch64, 8-core, 100 GB/s unified memory bandwidth). JDK 26 SunJCE uses **ARM Cryptography Extensions** (AES, PMULL) for hardware AES-GCM on this platform; results are not directly comparable to **Intel Core i5-13600K (x86_64)** Ubuntu runs without accounting for ISA and microarchitecture differences.
