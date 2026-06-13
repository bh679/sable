**Unofficial port of [Sable](https://github.com/ryanhcode/sable) to Minecraft 26.1.2 / NeoForge 26.1.2.75** (upstream publishes 1.21.1). Built for [Dungeon Train](https://github.com/bh679/dungeon-train-mc); offered upstream once stable. Licence unchanged: PolyForm Shield 1.0.0.

First fork build (`1.3.0-dt.1`), verified in-game:
 - Plots assemble (`/sable assemble`, `/sable spawn block|platform`), simulate with Rapier physics, render, and can be targeted, stood on, and built on
 - Sub-levels persist across save/quit/rejoin
 - Rust natives built by fork CI from unmodified upstream sources (mac/linux/windows, x86_64 + aarch64)

Known differences from upstream 1.21.1 (see `PORTING.md` for the full list):
 - Veil rendering features stripped (no 26.1 Veil build exists): fancy render path, dynamic shading, sky-light shadows, water occlusion, ImGui gizmo/inspector
 - All compat-mod integrations stripped (Create, Sodium/Iris, Jade, etc. — none ship 26.1 builds); Fabric module disabled (NeoForge only)
 - Dormant pending re-port: block entities on plots (chests invisible), entity render-pose smoothing/rotation sync on moving plots, block-destroy decals, name tags, weather on plots, particles on plots (partial), fluid spread at plot edges, arrows embedding in plot blocks, swim-on-plot, scroll zoom, single-block draw fast path (single-block plots render via the chunked path), game tests
