# Sable → MC 26.1.2 Port (unofficial, Dungeon Train)

Working branch: `port/mc26.1` · Upstream: [ryanhcode/sable](https://github.com/ryanhcode/sable) (1.21.1)
Target: **Minecraft 26.1.2 / NeoForge 26.1.2.75 / Java 25** · Started 2026-06-12

This fork exists because [Dungeon Train](https://github.com/bh679/dungeon-train-mc) needs
Sable on the latest Minecraft and upstream currently publishes 1.21.1 only. It is an
unofficial port, not a substitute for Sable — the port will be offered upstream as a PR
once stable, and this fork retires if upstream adopts or ships its own.
Licence: PolyForm Shield 1.0.0 (unchanged, `LICENSE.md`).

## Decisions

| # | Decision | Why |
|---|----------|-----|
| 1 | Target 26.1.2 (not 26.2) | 26.2 lands 2026-06-16 with beta-only NeoForge + Vulkan renderer churn; 26.1.2 has stable NeoForge 26.1.2.75 and porting primers. |
| 2 | **Strip Veil** instead of porting it | Veil (rendering lib, jarJar'd upstream) has no 26.1 build and no 26.x branch at FoundryMC/Veil. Usage is bounded: packet-context API (~19 network files) → replace with NeoForge payload contexts; "fancy" render path (Veil shaders/FBO/VAO) → delete, keep the vanilla `SubLevelRenderDispatcher` path. |
| 3 | Strip all compat mods | Create/Ponder/Flywheel/Registrate/Sodium/Iris/Jade/CC/Exposure/Vista/Moonlight/PMWeather/DistantHorizons — none ship 26.1 builds; Dungeon Train uses none. Deleted: 155-file Create tree (neoforge), common `mixin/compatibility`, sodium render impl. Mixin configs: common 215→203, neoforge 158→7. Restore selectively as upstreams port. |
| 4 | Fabric module disabled | DT is NeoForge-only; halves loader-glue work. Re-enable later if wanted. |
| 5 | Parchment removed | No 26.1 mappings exist; 26.1+ ships deobfuscated with official names. |
| 6 | `sable-companion` stays at the `-1.21.1` artifact (1.6.0) | Pure math library (Pose3d/BoundingBox3d), no MC imports; `sable_companion_mc` property pins the artifact name. |
| 7 | Version scheme `1.3.0-dt.N` | Distinguishes fork builds from any future upstream 1.3.x. Published as `sable-neoforge-26.1.2-<version>`. |

## Status

- [x] Toolchain bump (Gradle config evaluates clean)
- [x] Compat strip
- [ ] **Common module compiles** ← current
- [ ] De-Veil: networking (PacketContext → NeoForge `IPayloadContext` shim)
- [ ] De-Veil: render (delete `sublevel/render/fancy`, de-Veil vanilla dispatcher + water occlusion / sky-light shadow / dynamic shade)
- [ ] NeoForge module compiles
- [ ] Rust natives build via fork CI (`.github/workflows`, MC-version-agnostic)
- [ ] runClient boots; plot assembles and moves
- [ ] Save/quit clean (fix upstream #679 — PlotChunkHolder shutdown leak — properly here)
- [ ] GitHub Release `sable-neoforge-*+mc26.1.2`

## Error-wall trajectory (`./gradlew :common:compileJava`)

| Pass | Errors | Notes |
|------|--------|-------|
| 0 | (pending) | first attempt after toolchain+strip |

## Key migration references

- NeoForged primers: <https://github.com/neoforged/.github/tree/main/primers> (1.21.2→…→26.1 cumulative; biggest: 1.21.2 entity render states, 1.21.4 item models/ItemStackRenderState, 1.21.6 GUI/render pipeline, 26.1 Java 25 + deobf + loot unrolling + data-component lazy init)
- NeoForge 26.1 announcement: <https://neoforged.net/news/26.1release/>
