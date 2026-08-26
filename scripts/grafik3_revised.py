"""Generate the revised manuscript Figure 3 as a vector PDF.

The plotted medians are the approved single-thread values reported in
Tables 3 and 4. This is a figure-generation script; it does not repeat the
run-level statistical analysis.
"""

import os
import tempfile
from pathlib import Path

os.environ.setdefault(
    "MPLCONFIGDIR", str(Path(tempfile.gettempdir()) / "gas_artifact_matplotlib")
)
import matplotlib.pyplot as plt
OUTPUT = Path(__file__).resolve().with_name("Figure_3.pdf")
FIGSIZE = (7.2, 4.8)

N = [1_000, 5_000, 10_000, 50_000, 100_000]
X = list(range(len(N)))
N_LABELS = ["1k", "5k", "10k", "50k", "100k"]

# Approved median TPS values from Tables 3 and 4.
SERIES = {
    "STANDARD": [469.4, 467.1, 466.5, 478.8, 478.9],
    "FINGERPRINT": [170.8, 159.9, 157.7, 159.4, 159.7],
    "CHECKPOINT (K=100)": [274.9, 275.0, 276.7, 270.4, 270.2],
    "STRICT": [149.0, 141.0, 141.1, 143.0, 142.0],
}

STYLE = {
    "STANDARD": {"color": "#1F77B4", "marker": "o", "linestyle": "-"},
    "FINGERPRINT": {"color": "#FF7F0E", "marker": "o", "linestyle": "-"},
    "CHECKPOINT (K=100)": {
        "color": "#2CA02C",
        "marker": "o",
        "linestyle": "-",
    },
    "STRICT": {"color": "#D62728", "marker": "o", "linestyle": "-"},
}

plt.rcParams.update(
    {
        "font.family": "DejaVu Sans",
        "font.size": 10.5,
        "axes.labelsize": 11.5,
        "legend.fontsize": 9.5,
        "xtick.labelsize": 9.5,
        "ytick.labelsize": 9.5,
        "pdf.fonttype": 42,
        "ps.fonttype": 42,
    }
)

fig, ax = plt.subplots(figsize=FIGSIZE)

for label, values in SERIES.items():
    ax.plot(
        X,
        values,
        label=label,
        linewidth=2.0,
        markersize=6.0,
        markeredgewidth=0.8,
        **STYLE[label],
    )

ax.set_xticks(X)
ax.set_xticklabels(N_LABELS)
ax.set_xlabel("Number of update operations (N)")
ax.set_ylabel("Median throughput (TPS)")
ax.set_ylim(0, 520)
ax.grid(axis="y", color="#D9D9D9", linewidth=0.7)
ax.set_axisbelow(True)

ax.legend(
    loc="upper left",
    bbox_to_anchor=(1.01, 1.0),
    ncol=1,
    frameon=True,
    borderaxespad=0,
    handlelength=2.4,
)

fig.subplots_adjust(left=0.13, right=0.72, bottom=0.16, top=0.97)
fig.savefig(
    OUTPUT,
    format="pdf",
    bbox_inches="tight",
    pad_inches=0.05,
    metadata={"Title": "Figure 3", "Creator": "Matplotlib", "CreationDate": None},
)
plt.close(fig)

print(OUTPUT)
