# Ubuntu benchmark host — hardware & OS

Recorded from the machine used for **`results-ubuntu/`** JMH runs. Re-run the capture commands below after hardware or OS changes.

## Operating system

| Field | Value |
|-------|--------|
| Distribution | Ubuntu 24.04.4 LTS (noble) |
| Kernel | Linux 6.17.0-19-generic (x86_64) |
| Hostname | ubuntu-chan |

## CPU

| Field | Value |
|-------|--------|
| Model | Intel 13th Gen Core i5-13600K |
| Microarchitecture | Raptor Lake (client) |
| Sockets | 1 |
| Performance cores (reported) | 14 |
| Logical CPUs | 20 (SMT / hybrid: P+E cores) |
| Frequency (reported) | 800 MHz – 5.1 GHz (variable; `lscpu`) |
| ISA (relevant to crypto) | x86-64; **AES-NI** (`aes`), **SHA extensions** (`sha_ni`), **AVX / AVX2** (`avx`, `avx2`), **VAES / VPCLMULQDQ** (`vaes`, `vpclmulqdq`), **GFNI** (`gfni`) |
| L1d / L1i | 544 KiB / 704 KiB (aggregate) |
| L2 | 20 MiB (aggregate) |
| L3 | 24 MiB |
| NUMA nodes | 1 |

## Memory

| Field | Value |
|-------|--------|
| RAM (total) | ~62 GiB |
| Swap | 8 GiB |

*(Snapshot from `free -h`; values vary with workload.)*

## Storage

| Field | Value |
|-------|--------|
| Root filesystem | `/dev/nvme0n1p6` (~248 GiB volume; NVMe) |

*(Use `df -h` / `lsblk` for current use.)*

## Graphics (not used by headless JVM benchmarks)

| Device | Notes |
|--------|--------|
| Intel UHD Graphics 770 | Raptor Lake-S GT1 (integrated) |
| NVIDIA GeForce RTX 4070 Ti | Discrete (driver stack not enumerated here) |

## How this file was captured

```bash
uname -a
lsb_release -a
lscpu
free -h
lsblk
df -h /
lspci | grep -iE 'vga|3d|display'
```

## Paper / methodology snippet

> Benchmarks on **Ubuntu 24.04 LTS**, **Linux 6.17**, **Intel Core i5-13600K** (Raptor Lake, **x86_64**), **~62 GiB RAM**, single NUMA node. JDK 26 SunJCE can use **AES-NI** and related x86 crypto extensions on this CPU; results are not directly comparable to **Apple M3 (AArch64)** runs without accounting for ISA and microarchitecture differences.
