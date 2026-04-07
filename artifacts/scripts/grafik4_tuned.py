import matplotlib.pyplot as plt

FIGSIZE = (7.2, 4.8)

N = [1_000, 5_000, 10_000, 50_000, 100_000]

# Updated data — 30-run benchmark (median overhead %)
over_signature_only = [-3.70, -1.22, -0.80,  0.96,  1.39]
over_chain_only     = [ 8.13, 11.01, 12.10, 14.72, 12.85]
over_full_hybrid    = [ 9.41, 12.65, 12.46, 15.56, 13.71]

plt.rcParams.update({
    "font.size": 11,
    "axes.labelsize": 12,
    "legend.fontsize": 10.5,
    "xtick.labelsize": 10.5,
    "ytick.labelsize": 10.5,
})

fig, ax = plt.subplots(figsize=FIGSIZE)
ax.plot(N, over_signature_only, marker="o", linewidth=2, label="SIGNATURE_ONLY")
ax.plot(N, over_chain_only,     marker="o", linewidth=2, label="CHAIN_ONLY")
ax.plot(N, over_full_hybrid,    marker="o", linewidth=2, label="FULL_HYBRID")

ax.axhline(y=0, color="gray", linestyle="--", linewidth=0.8, alpha=0.6)

ax.set_xscale("log")
ax.set_xlabel("Number of records (N)")
ax.set_ylabel("Median overhead vs STANDARD (%)")

# Legend outside plot area
ax.legend(loc="upper left",
          bbox_to_anchor=(1.01, 1.0),
          borderaxespad=0,
          frameon=True)

plt.tight_layout()
plt.savefig("Figure_4_tuned.pdf", bbox_inches="tight")
plt.show()
