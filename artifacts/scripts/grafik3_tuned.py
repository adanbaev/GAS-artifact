import matplotlib.pyplot as plt

FIGSIZE = (7.2, 4.8)

N = [1_000, 5_000, 10_000, 50_000, 100_000]

# Updated data — 30-run benchmark (median TPS)
tps_standard       = [299.58, 307.47, 307.85, 310.15, 305.27]
tps_signature_only = [311.08, 311.28, 310.34, 307.20, 301.09]
tps_chain_only     = [277.06, 276.98, 274.61, 270.35, 270.50]
tps_full_hybrid    = [273.83, 272.96, 273.73, 268.39, 268.46]

plt.rcParams.update({
    "font.size": 11,
    "axes.labelsize": 12,
    "legend.fontsize": 10.5,
    "xtick.labelsize": 10.5,
    "ytick.labelsize": 10.5,
})

fig, ax = plt.subplots(figsize=FIGSIZE)
ax.plot(N, tps_standard,       marker="o", linewidth=2, label="STANDARD")
ax.plot(N, tps_signature_only, marker="o", linewidth=2, label="SIGNATURE_ONLY")
ax.plot(N, tps_chain_only,     marker="o", linewidth=2, label="CHAIN_ONLY")
ax.plot(N, tps_full_hybrid,    marker="o", linewidth=2, label="FULL_HYBRID")

ax.set_xscale("log")
ax.set_xlabel("Number of records (N)")
ax.set_ylabel("Median throughput (TPS)")

# Legend outside plot area
ax.legend(loc="upper left",
          bbox_to_anchor=(1.01, 1.0),
          borderaxespad=0,
          frameon=True)

plt.tight_layout()
plt.savefig("Figure_3_tuned.pdf", bbox_inches="tight")
plt.show()
