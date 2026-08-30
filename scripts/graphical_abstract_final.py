#!/usr/bin/env python3
"""Generate the final graphical abstract for the GAS minor revision.

The concurrency panel uses the approved median aggregate TPS values from
Table 5. Scalability is computed as TPS(T) / TPS(1) for each strategy.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import matplotlib as mpl
import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch
import numpy as np


THREADS = np.array([1, 2, 4, 8, 16, 32])
MEDIAN_TPS = {
    "STANDARD": np.array([480.0, 917.7, 1257.5, 1338.0, 1266.9, 1253.5]),
    "FINGERPRINT": np.array([162.9, 297.9, 470.4, 671.0, 685.8, 934.2]),
    "CHECKPOINT": np.array([272.2, 476.6, 686.6, 644.5, 521.2, 596.5]),
    "STRICT": np.array([145.5, 272.5, 303.1, 309.1, 297.7, 265.9]),
}

COLORS = {
    "ink": "#18334D",
    "arrow": "#36526F",
    "STANDARD": "#1F77B4",
    "FINGERPRINT": "#FF7F0E",
    "CHECKPOINT": "#2CA02C",
    "STRICT": "#D62728",
}

FONT_SCALE = 1.0


def add_box(ax, xy, width, height, text, *, face, edge, fontsize=9.2,
            linewidth=1.25, radius=0.02, weight="semibold", zorder=3):
    """Add a rounded labeled box in axes-fraction coordinates."""
    x, y = xy
    patch = FancyBboxPatch(
        (x, y), width, height,
        boxstyle=f"round,pad=0.012,rounding_size={radius}",
        transform=ax.transAxes,
        facecolor=face,
        edgecolor=edge,
        linewidth=linewidth,
        zorder=zorder,
    )
    ax.add_patch(patch)
    ax.text(
        x + width / 2,
        y + height / 2,
        text,
        transform=ax.transAxes,
        ha="center",
        va="center",
        fontsize=fontsize * FONT_SCALE,
        color=COLORS["ink"],
        fontweight=weight,
        linespacing=1.05,
        zorder=zorder + 1,
    )
    return patch


def add_arrow(ax, start, end, *, connectionstyle="arc3,rad=0", color=None,
              mutation_scale=6.2, linewidth=0.75, zorder=6):
    """Add a directional arrow in axes-fraction coordinates."""
    arrow = FancyArrowPatch(
        start,
        end,
        transform=ax.transAxes,
        arrowstyle="->",
        mutation_scale=mutation_scale,
        linewidth=linewidth,
        color=color or COLORS["arrow"],
        connectionstyle=connectionstyle,
        shrinkA=0,
        shrinkB=0,
        zorder=zorder,
    )
    ax.add_patch(arrow)
    return arrow


def draw_architecture(ax):
    ax.set_xlim(0, 1)
    ax.set_ylim(0, 1)
    ax.axis("off")

    add_box(
        ax, (0.025, 0.770), 0.310, 0.125,
        "Authenticated\nwrite",
        face="#EDF3F8", edge="#36526F", fontsize=6.9,
    )
    add_box(
        ax, (0.415, 0.770), 0.495, 0.125,
        "Per-request temporal\ncontrol",
        face="#FFF0E0", edge="#D98016", fontsize=7.0,
    )
    # Rounded-box padding extends beyond nominal coordinates. These endpoints
    # leave a deliberate white gap on both sides of every arrow.
    add_arrow(ax, (0.355, 0.832), (0.395, 0.832))

    add_box(
        ax, (0.090, 0.515), 0.320, 0.135,
        "Service-layer\nMyISAM UPDATE",
        face="#F0EFF9", edge="#5A5A91", fontsize=6.8,
    )
    add_box(
        ax, (0.515, 0.515), 0.435, 0.135,
        "Fresh DB re-read +\n23-field HMAC",
        face="#E4F3E8", edge="#31934F", fontsize=6.9,
    )
    add_arrow(
        ax, (0.665, 0.735), (0.250, 0.675),
    )
    add_arrow(ax, (0.435, 0.582), (0.490, 0.582))

    evidence = FancyBboxPatch(
        (0.040, 0.225), 0.920, 0.205,
        boxstyle="round,pad=0.012,rounding_size=0.02",
        transform=ax.transAxes,
        facecolor="#FBF7FB",
        edgecolor="#9A3A97",
        linewidth=1.15,
        zorder=1,
    )
    ax.add_patch(evidence)
    ax.text(
        0.497, 0.392, "Post-write evidence strategy",
        transform=ax.transAxes, ha="center", va="center",
        fontsize=7.0 * FONT_SCALE, color=COLORS["ink"], fontweight="semibold", zorder=4,
    )

    add_box(
        ax, (0.060, 0.247), 0.270, 0.100,
        "FINGERPRINT\nper-write",
        face="#FFF1E5", edge=COLORS["FINGERPRINT"], fontsize=6.7,
        linewidth=1.0, radius=0.014,
    )
    add_box(
        ax, (0.375, 0.247), 0.260, 0.100,
        "CHECKPOINT\nK = 100",
        face="#EAF5EA", edge=COLORS["CHECKPOINT"], fontsize=6.7,
        linewidth=1.0, radius=0.014,
    )
    add_box(
        ax, (0.690, 0.247), 0.235, 0.100,
        "STRICT\nglobal chain",
        face="#FBEAEA", edge=COLORS["STRICT"], fontsize=6.7,
        linewidth=1.0, radius=0.014,
    )
    # Keep the straight connector clear of both the HMAC and evidence frames.
    add_arrow(ax, (0.725, 0.490), (0.505, 0.450))

    ax.text(
        0.50, 0.105,
        "Detects protected-field modification;\n"
        "same-domain evidence does not prove\n"
        "completeness or freshness.",
        transform=ax.transAxes,
        ha="center",
        va="center",
        fontsize=6.8 * FONT_SCALE,
        color="#4A4A4A",
        fontstyle="italic",
    )


def draw_scalability(ax):
    positions = np.arange(len(THREADS))
    scalability = {name: values / values[0] for name, values in MEDIAN_TPS.items()}

    for name in ("STANDARD", "FINGERPRINT", "CHECKPOINT", "STRICT"):
        ax.plot(
            positions,
            scalability[name],
            color=COLORS[name],
            marker="o",
            markersize=2.8,
            linewidth=1.05,
            label=name,
            zorder=3,
        )

    ax.axhline(1.0, color="#9A9A9A", linestyle="--", linewidth=0.70, zorder=1)
    ax.set_xticks(positions, [str(t) for t in THREADS])
    ax.set_xlim(-0.18, 5.18)
    ax.set_ylim(0.8, 6.15)
    ax.set_xlabel("")
    ax.set_ylabel("TPS(T) / TPS(1)", fontsize=6.7 * FONT_SCALE)
    ax.tick_params(axis="both", labelsize=6.7 * FONT_SCALE, width=0.8)
    ax.grid(True, color="#C8C8C8", alpha=0.38, linewidth=0.50)
    ax.set_axisbelow(True)
    ax.set_title(
        "Scalability by worker threads",
        fontsize=7.2 * FONT_SCALE,
        fontweight="bold",
        pad=8,
    )
    # Direct labels include absolute TPS so the normalized curves cannot be
    # mistaken for an absolute-throughput ranking.
    ax.text(
        4.55, 5.43,
        "FINGERPRINT 5.73×\n→ 934 TPS at 32",
        fontsize=6.6 * FONT_SCALE,
        color="#B65300",
        ha="right",
        va="center",
        linespacing=1.02,
        zorder=5,
    )
    ax.text(
        2.70, 3.25,
        "CHECKPOINT\n686.6 TPS at 4",
        fontsize=6.6 * FONT_SCALE,
        color="#1E7D27",
        ha="left",
        va="center",
        linespacing=1.02,
        zorder=5,
    )
    ax.plot([-0.02, 0.30], [5.58, 5.58], color=COLORS["STANDARD"], linewidth=1.05)
    ax.text(
        0.38, 5.48, "STANDARD\n1338 TPS at 8",
        fontsize=6.6 * FONT_SCALE,
        color="#145C92",
        ha="left",
        va="center",
        linespacing=1.02,
        zorder=5,
    )
    ax.text(
        2.55, 1.52,
        "STRICT\n~300 TPS at 4–16",
        fontsize=6.6 * FONT_SCALE,
        color="#A41515",
        ha="left",
        va="center",
        linespacing=1.02,
        zorder=5,
    )


def build_figure():
    mpl.rcParams.update({
        "font.family": "DejaVu Sans",
        "pdf.fonttype": 42,
        "ps.fonttype": 42,
        "axes.unicode_minus": False,
    })
    # Use the journal's intended physical display size, not inches with the
    # same numeric values. Font sizes therefore remain their true point size
    # when the graphical abstract is viewed at approximately 13 x 5 cm.
    fig = plt.figure(figsize=(13.28 / 2.54, 5.31 / 2.54), facecolor="white")
    fig.suptitle(
        "Application-layer integrity + temporal control in centralized registries",
        x=0.5, y=0.975, fontsize=8.3, fontweight="bold",
    )
    architecture_ax = fig.add_axes([0.018, 0.060, 0.495, 0.830])
    scalability_ax = fig.add_axes([0.565, 0.135, 0.415, 0.665])
    for spine in scalability_ax.spines.values():
        spine.set_linewidth(0.8)
    draw_architecture(architecture_ax)
    draw_scalability(scalability_ax)
    return fig


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path(__file__).resolve().parent,
        help="Directory for PNG and PDF outputs (default: script directory).",
    )
    parser.add_argument(
        "--basename",
        default="Graphical_Abstract_final",
        help="Output filename stem.",
    )
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)

    fig = build_figure()
    png_path = args.output_dir / f"{args.basename}.png"
    pdf_path = args.output_dir / f"{args.basename}.pdf"
    fig.savefig(
        png_path,
        dpi=400,
        facecolor="white",
        metadata={"Title": "Graphical Abstract", "Software": "Matplotlib"},
    )
    fig.savefig(
        pdf_path,
        facecolor="white",
        metadata={
            "Title": "Graphical Abstract",
            "Creator": "Matplotlib",
            "CreationDate": None,
        },
    )
    plt.close(fig)

    print(png_path)
    print(pdf_path)


if __name__ == "__main__":
    main()
