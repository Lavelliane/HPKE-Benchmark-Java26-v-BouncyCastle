#!/usr/bin/env python3
"""
Parse JMH JSON outputs under results/raw/, export combined/per-benchmark CSVs,
and generate LaTeX table + PDF figures for the paper.
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import sys
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


def _repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def _default_results_root() -> Path:
    """Linux → results-ubuntu/ so Ubuntu runs do not overwrite macOS results/."""
    root = _repo_root()
    env = os.environ.get("HPKE_RESULTS_ROOT")
    if env:
        p = Path(env).expanduser()
        if not p.is_absolute():
            p = root / p
        return p.resolve()
    if sys.platform.startswith("linux"):
        return root / "results-ubuntu"
    return root / "results"


def _png_basename(plots_dir: Path, stem: str) -> str:
    """Use figure_*_ubuntu.png under .../results-ubuntu/... so plots do not clash with macOS names."""
    if "results-ubuntu" in plots_dir.resolve().parts:
        return f"{stem}_ubuntu.png"
    return f"{stem}.png"


def _memory_md_stem(plots_dir: Path) -> str:
    return "memory_allocation_ubuntu" if "results-ubuntu" in plots_dir.resolve().parts else "memory_allocation"


def _md_table_gc(pivot: pd.DataFrame) -> str:
    lines = [
        "| Suite | JDK26 (B/op) | BC (B/op) |\n",
        "|-------|-------------:|----------:|\n",
    ]
    for suite in sorted(pivot.index):
        j = pivot.loc[suite].get("JDK26")
        b = pivot.loc[suite].get("BC")
        jv = f"{j:,.0f}" if pd.notna(j) else "—"
        bv = f"{b:,.0f}" if pd.notna(b) else "—"
        lines.append(f"| {suite} | {jv} | {bv} |\n")
    return "".join(lines)


def write_memory_allocation_md(df: pd.DataFrame, plots_dir: Path) -> Path | None:
    """
    Summarize JMH `gc.alloc.rate.norm` (bytes per invocation) for Keygen + Seal.
    Written next to figures; under results-ubuntu/ → memory_allocation_ubuntu.md.
    """
    if "gc_alloc_rate_norm" not in df.columns:
        return None
    sub = df[df["gc_alloc_rate_norm"].notna()].copy()
    if sub.empty:
        return None
    plots_dir.mkdir(parents=True, exist_ok=True)
    out = plots_dir / f"{_memory_md_stem(plots_dir)}.md"
    parts: list[str] = [
        "# Memory allocation (JMH `-prof gc`)\n\n",
        "Source: secondary metric **`gc.alloc.rate.norm`** — **bytes per benchmark invocation** (B/op).\n\n",
        "Requires benchmarks run with **`-prof gc`** (see `scripts/run_benchmarks.sh`).\n\n",
    ]
    kg = sub[sub["benchmark"].str.contains("KeygenBenchmark", na=False)]
    if not kg.empty:
        pivot = kg.pivot_table(
            index="suite", columns="library", values="gc_alloc_rate_norm", aggfunc="first"
        )
        parts.append("## KeygenBenchmark\n\n")
        parts.append(_md_table_gc(pivot))
        parts.append("\n")
    seal = sub[sub["benchmark"].str.contains("SealBenchmark", na=False)]
    if not seal.empty and "payloadSize" in seal.columns:
        seal = seal.copy()
        seal["payloadSize"] = seal["payloadSize"].astype(str)
        for psz, title in [("64", "64 B"), ("1024", "1 KB"), ("65536", "64 KB")]:
            chunk = seal[seal["payloadSize"] == psz]
            if chunk.empty:
                continue
            pivot = chunk.pivot_table(
                index="suite", columns="library", values="gc_alloc_rate_norm", aggfunc="first"
            )
            parts.append(f"## SealBenchmark @ {title}\n\n")
            parts.append(_md_table_gc(pivot))
            parts.append("\n")
    out.write_text("".join(parts), encoding="utf-8")
    return out


def _flatten_jmh_entry(entry: dict) -> dict:
    pm = entry.get("primaryMetric") or {}
    params = entry.get("params") or {}
    row = {
        "benchmark": entry.get("benchmark", ""),
        "mode": entry.get("mode", ""),
        "library": params.get("library", ""),
        "suite": params.get("suite", ""),
        "payloadSize": params.get("payloadSize", ""),
        "score": pm.get("score"),
        "score_error": pm.get("scoreError"),
        "unit": pm.get("scoreUnit", ""),
    }
    sm = entry.get("secondaryMetrics") or {}
    gc = sm.get("gc.alloc.rate.norm") or {}
    if isinstance(gc, dict):
        row["gc_alloc_rate_norm"] = gc.get("score")
        row["gc_alloc_rate_norm_unit"] = gc.get("scoreUnit", "")
    else:
        row["gc_alloc_rate_norm"] = None
        row["gc_alloc_rate_norm_unit"] = ""
    return row


def load_jmh_json_files(raw_dir: Path) -> pd.DataFrame:
    rows: list[dict] = []
    for p in sorted(raw_dir.glob("*.json")):
        try:
            data = json.loads(p.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            print(f"Skip invalid JSON {p}: {e}", file=sys.stderr)
            continue
        if not isinstance(data, list):
            continue
        for entry in data:
            if isinstance(entry, dict):
                rows.append(_flatten_jmh_entry(entry))
    return pd.DataFrame(rows)


def _normalize_jmh_csv(df: pd.DataFrame) -> pd.DataFrame:
    out = pd.DataFrame()
    out["benchmark"] = df.get("Benchmark", "")
    out["mode"] = df.get("Mode", "")
    out["library"] = df.get("Param: library", "")
    out["suite"] = df.get("Param: suite", "")
    out["payloadSize"] = df.get("Param: payloadSize", "")
    out["score"] = df.get("Score")
    out["score_error"] = df.get("Score Error (99.9%)")
    out["unit"] = df.get("Unit", "")
    out["gc_alloc_rate_norm"] = None
    out["gc_alloc_rate_norm_unit"] = ""
    return out


def load_jmh_csv_files(raw_dir: Path) -> pd.DataFrame:
    """Fallback: merge JMH-exported CSV files (e.g. from -rf csv)."""
    frames: list[pd.DataFrame] = []
    for p in sorted(raw_dir.glob("*.csv")):
        if p.name == "all_results.csv":
            continue
        try:
            raw = pd.read_csv(p)
            norm = _normalize_jmh_csv(raw)
            if not norm.empty:
                frames.append(norm)
        except Exception as e:
            print(f"Skip CSV {p}: {e}", file=sys.stderr)
    if not frames:
        return pd.DataFrame()
    return pd.concat(frames, ignore_index=True)


def csv_from_dataframe(df: pd.DataFrame, out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(out, index=False)


def write_latex_table_seal_1k(df: pd.DataFrame, out: Path) -> None:
    seal = df[df["benchmark"].str.contains("SealBenchmark", na=False)]
    if seal.empty or "payloadSize" not in seal.columns:
        out.write_text("% No SealBenchmark data for 1KB table\n", encoding="utf-8")
        return
    sub = seal[seal["payloadSize"].astype(str) == "1024"].copy()
    if sub.empty:
        out.write_text("% No payloadSize=1024 rows\n", encoding="utf-8")
        return
    pivot = sub.pivot_table(
        index="suite", columns="library", values="score", aggfunc="first"
    )
    lines = [
        r"\begin{tabular}{lrr}",
        r"\hline",
        r"Suite & JDK26 (us/op) & BC (us/op) \\",
        r"\hline",
    ]
    for suite in pivot.index:
        j = pivot.loc[suite].get("JDK26", float("nan"))
        b = pivot.loc[suite].get("BC", float("nan"))
        lines.append(f"{suite} & {j:.3f} & {b:.3f} \\\\")
    lines.extend([r"\hline", r"\end{tabular}"])
    out.write_text("\n".join(lines) + "\n", encoding="utf-8")


def plot_seal_by_payload(df: pd.DataFrame, plots_dir: Path) -> None:
    seal = df[df["benchmark"].str.contains("SealBenchmark", na=False)]
    if seal.empty:
        return
    seal = seal.copy()
    seal["payloadSize"] = seal["payloadSize"].astype(str)
    suites = sorted(seal["suite"].dropna().unique())
    if not suites:
        return
    payloads = ["64", "1024", "65536"]
    fig, axes = plt.subplots(2, 4, figsize=(14, 7))
    axes = axes.flatten()
    width = 0.35
    x = np.arange(len(payloads))
    for ax, suite in zip(axes, suites[:8]):
        sub = seal[seal["suite"] == suite]
        vals_jdk = []
        vals_bc = []
        for ps in payloads:
            rj = sub[(sub["library"] == "JDK26") & (sub["payloadSize"] == ps)]
            rb = sub[(sub["library"] == "BC") & (sub["payloadSize"] == ps)]
            vals_jdk.append(float(rj["score"].iloc[0]) if len(rj) else 0.0)
            vals_bc.append(float(rb["score"].iloc[0]) if len(rb) else 0.0)
        ax.bar(x - width / 2, vals_jdk, width, label="JDK26", color="tab:blue", alpha=0.85)
        ax.bar(x + width / 2, vals_bc, width, label="BC", color="tab:orange", alpha=0.85)
        ax.set_title(suite, fontsize=9)
        ax.set_xticks(x)
        ax.set_xticklabels(payloads)
        ax.set_xlabel("payload bytes")
        ax.set_ylabel("us/op")
        ax.legend(fontsize=7)
    plt.tight_layout()
    plots_dir.mkdir(parents=True, exist_ok=True)
    fig.savefig(plots_dir / _png_basename(plots_dir, "figure_seal_by_payload"), dpi=150, bbox_inches="tight")
    plt.close(fig)


def plot_keygen(df: pd.DataFrame, plots_dir: Path) -> None:
    kg = df[df["benchmark"].str.contains("KeygenBenchmark", na=False)]
    if kg.empty:
        return
    pivot = kg.pivot_table(index="suite", columns="library", values="score", aggfunc="first")
    ax = pivot.plot(kind="bar", figsize=(10, 5), rot=45)
    ax.set_ylabel("us/op")
    ax.set_title("Key generation latency")
    plt.tight_layout()
    plots_dir.mkdir(parents=True, exist_ok=True)
    plt.savefig(plots_dir / _png_basename(plots_dir, "figure_keygen"), dpi=150, bbox_inches="tight")
    plt.close()


def main() -> int:
    results_root = _default_results_root()
    ap = argparse.ArgumentParser(
        description="Analyze JMH JSON/CSV under <results-root>/raw/ (see HPKE_RESULTS_ROOT / platform defaults)."
    )
    ap.add_argument(
        "--raw-dir",
        type=Path,
        default=results_root / "raw",
        help="Directory containing JMH .json and/or .csv outputs",
    )
    ap.add_argument(
        "--plots-dir",
        type=Path,
        default=results_root / "plots",
        help="Output directory for figures and LaTeX",
    )
    args = ap.parse_args()

    df = load_jmh_json_files(args.raw_dir)
    if df.empty:
        df = load_jmh_csv_files(args.raw_dir)
        if df.empty:
            print("No data: add JMH .json or .csv under", args.raw_dir, file=sys.stderr)
            return 1

    args.raw_dir.mkdir(parents=True, exist_ok=True)
    args.plots_dir.mkdir(parents=True, exist_ok=True)
    csv_from_dataframe(df, args.raw_dir / "all_results.csv")

    for bench in df["benchmark"].dropna().unique():
        short = str(bench).split(".")[-1]
        sub = df[df["benchmark"] == bench]
        csv_from_dataframe(sub, args.raw_dir / f"{short}.csv")

    write_latex_table_seal_1k(df, args.plots_dir / "table_seal_1kb.tex")
    plot_seal_by_payload(df, args.plots_dir)
    plot_keygen(df, args.plots_dir)
    mem_md = write_memory_allocation_md(df, args.plots_dir)

    seal_png = args.plots_dir / _png_basename(args.plots_dir, "figure_seal_by_payload")
    print("Wrote:", args.raw_dir / "all_results.csv", seal_png)
    if mem_md:
        print("Wrote:", mem_md)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
