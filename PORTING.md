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

- [x] Toolchain bump (NeoForm 26.1.2 pipeline + JDK 25 provisioning verified working)
- [x] Compat strip
- [x] De-Veil: networking (`SablePacketContext`/`SablePacketSink` shims; declarative `SableTCPPackets` + NeoForge `RegisterPayloadHandlersEvent` registration)
- [x] De-Veil: render features deleted (fancy path, dynamic shading, sky-light shadows, water occlusion, ImGui gizmo/inspector, sodium reach-around)
- [x] De-Veil: registries (`SableRegistrationProvider` shim), mixin plugin (`SableLoaderPlatform.isModLoaded`)
- [x] Mechanical 26.1 renames (Identifier, ChunkPos record, NBT Optional API, isClientSide(), height accessors, Profiler.get(), RenderType/BlockAndTintGetter moves)
- [x] **Common module compiles** (1212 → 0 over 9 passes; render cluster rebuilt on prepareChunkRenders/SectionMesh, plot core + long tail ported)
- [x] NeoForge module compiles; `sable-neoforge-26.1.2-1.3.0-dt.1.jar` assembles (game tests excluded pending 26.1 test-framework port)
- [x] **runClient boots to title screen and into a world** (39 boot iterations of mixin-application fixes; player joins, server ticks, Rapier natives load)
- [x] **Plot assembly works server-side** — `/sable assemble area` created a 729-block sub-level, `assemble connected` a 5-block one; Rapier scenes initialized; sub-level saving runs; no crash
- [ ] **ACTIVE BUG: plots are invisible client-side and can't be targeted** ← current

## Active investigation: client pose corruption (plots invisible)

**Symptoms** (in-game test 2026-06-12): assembled/spawned plots don't render, blocks
can't be placed on them, and the log spams `Aborting entity get for abnormally large
AABB` (Render thread, via `LocalPlayer.pick` → `SubLevelInclusiveLevelEntityGetter`).
The AABB always has one sane corner at the real plot location (~x 2-7, y 56-60) and one
corner at ±millions, with different magnitudes every frame. One root cause explains all
three symptoms: the client-side **render pose** is garbage, so the plot is drawn (and
raycast against) millions of blocks away.

**Hypotheses, ranked:**
1. **Snapshot interpolation/extrapolation math** in `SubLevelSnapshotInterpolator` /
   `ClientSubLevel.renderPose(pt)` — per-frame-varying overshoot with a sane anchor fits
   a broken time source (port converted `getTimer()`→`getDeltaTracker()` and reworked
   profiler/time APIs; check the units used to timestamp vs sample snapshots — game
   time vs millis vs partial ticks).
2. **UDP/local-channel snapshot serialization** — agent rework renamed
   `NoOpFrameEncoder/Decoder`→`LocalFrameEncoder/Decoder`+`MonitoredLocalFrameDecoder`
   and rebuilt `EventLoopGroupHolder` usage. Misaligned frame reads would produce
   exactly this random garbage. NOTE: setting `attempt_udp_networking = false` in
   sable-client.toml did NOT stop "Client UDP channel active" in single player — find
   where `ATTEMPT_UDP_NETWORKING` is consumed and whether the local channel bypasses
   it; a hard early-return in the UDP activation path is the clean A/B test.
3. TCP `ClientboundSableSnapshotDualPacket` codec drift (least likely — but log its
   received poses to rule out).

**Debug plan:** add temporary LOGGER lines for (a) server `logicalPose` per tick for
one sub-level, (b) client received snapshot poses in the dual-packet/UDP handlers,
(c) interpolator output in `renderPose`. Compare where sanity is lost: received-garbage
→ hypothesis 2 (diff `network/udp/` against upstream `48f5c24`); received-sane but
output-garbage → hypothesis 1 (time math). Also verify the TCP fallback actually
delivers snapshots when UDP is killed.

**Also pending:** `minecraft:chain`→`minecraft:iron_chain` in
`tags/block/{quarter_volume,super_light}.json` and deleting
`physics_block_properties/flywheel.json` (Create leftover) — fix was prepared but not
yet applied/confirmed.

## Runtime-dormant injectors (require = 0) — polish-pass backlog

Fixed properly during the boot loop: BlockAndLightGetter retarget (plot light), Block
`updateEntityMovementAfterFallOn`, Player `canFallAtLeast(DDD)`/interaction-range renames,
InterpolationHandler lerp rewrite, `snapTo`, arrow `isInGround()`, popResource LVT,
camera rotate-with-plot on 2-arg `setRotation`, `Minecraft#pick` retarget, furnace
`recipesUsed` type, world-border per-subclass link, `LevelChunk.setBlockState` flags,
ValueInput/ValueOutput save hooks, sea-level-during-ctor guard, config-loading
null-guard, moved-package target strings (`projectile/arrow/`), `tickInGameSound`,
swimming camera near-plane/level shadows.

Dormant (feature degrades to vanilla; re-port deliberately): single-block plot draw,
sub-level block entities (chests on plots invisible!), entity render-pose smoothing +
rotation sync (`entity_rotations_and_riding` Entity/ServerEntity — silent-apply
mystery, bisect-confirmed), block-destroy decals, debug boxes/gizmo, name tags,
crosshair attack indicator, leashes, sleeping pose, weather-on-plots, sculk lambdas,
loadEntityRecursive riding restore, particles on plots (spawn transforms partly dormant),
fluid spread at plot edges, arrows embedding in plot blocks, swim-on-plot (NeoForge
fluid-extension API removed), scroll zoom, anti-cheat creative check
(`entity_sublevel_collision.ServerGamePacketListenerImplMixin` — affects moving-plot
rubber-banding, HIGH priority), teleport-packet plot data (codec rework needed,
multiplayer), game tests.
- [ ] Rust natives build via fork CI (`.github/workflows`, MC-version-agnostic)
- [ ] runClient boots; plot assembles and moves
- [ ] Save/quit clean (fix upstream #679 — PlotChunkHolder shutdown leak — properly here)
- [ ] GitHub Release `sable-neoforge-*+mc26.1.2`

## Error-wall trajectory (`./gradlew :common:compileJava`)

| Pass | Errors | What changed before it |
|------|--------|------------------------|
| 1 | 1212 | toolchain bump + compat strip only |
| 2 | 529 | de-Veil (networking/registries/render features) + bulk renames (Identifier, NBT Optional API, ChunkPos pack/unpack, …) |
| 3 | 413 | line-targeted ChunkPos record accessors/ctors, misc renames |
| 4 | 393 | ticket-system rewrite (TicketStorage/registered TicketType), wave-3 renames (sections, registry lookup, camera position(), AbstractArrow move) |

## Remaining work (next session)

**Render cluster (~80 errors)** — needs the 26.1 architecture, mapped as follows:
- Chunk layers: `RenderType` → **`ChunkSectionLayer`** enum (`client.renderer.chunk`); draws go through `SectionMesh`/`CompiledSectionMesh` + `ChunkSectionsToRender` (GpuBuffer/RenderPass world, no more ShaderInstance/Uniform/VertexBuffer).
- `SectionRenderDispatcher` + `RenderRegionCache` still exist; `RenderChunkRegion` → `RenderSectionRegion`; compiled-section API now `SectionMesh`.
- Files: `sublevel/render/vanilla/VanillaChunkedSubLevelRenderData` (28), `mixinhelpers/block_outline_render/SubLevelCamera` (18), `mixin/sublevel_render/impl/vanilla/LevelRendererMixin` (13), `SimpleCulledRenderRegion` (8), `mixin/entity/entity_rendering/EntityRendererMixin` (8), `mixin/debug_render/LevelRendererMixin` (7), `VanillaSingleSubLevelRenderData`, `SubLevelRenderData` interface (compileSections signature), `LightTexture`→`Lightmap` call sites (~16), `DebugRenderer.renderLineBox`→`ShapeRenderer.renderLineBox`.
- Interface change already staged: `SubLevelRenderDispatcher.renderSectionLayer` lost the ShaderInstance param; switch its `RenderType` to `ChunkSectionLayer` while porting.

**Plot/chunk core (~50)** — `ServerLevelPlot` (26), `SubLevelHoldingChunkMap` (12), `EmbeddedPlotLevelAccessor` (6), `mixin/plot/ServerChunkCacheMixin` (7): ChunkHolder future/API drift (`getTimer`, ChunkHolder futures renamed), `ChunkProgressListener` changes, `onBlockStateChange` signature.

**Long tail (~260)** — per-site: commands (`SableSpawnCommands` 12, suggestion/argument API), `SubLevelAssemblyHelper` (9), entity collision (7, `lerpTo` signature, `is(TagKey)`), `respawn_point` mixins (RespawnConfig rework), 4 player mixins (Player ctor signature), Optional unwraps from the NBT sweep (~15), `toLong()` leftovers (12, mostly SectionPos/ColumnPos receivers — verify each), `getTimer()` (11, DeltaTracker rework), fastutil `Strategy` imports (4).

**Then:** neoforge module (~10 expected: SableNeoForge glue compiles against ported common), gametest module check, AT audit for renamed targets (`ChunkHolder` futures, `LevelRenderer.cullingFrustum`, `Particle` fields, `RenderStateShard.name`), `architectury.common.json`/`sable.accesswidener` cleanup, natives CI on the fork, runClient smoke.

## Reference tooling (established this session)

- 26.1.2 compiled+sources jar (API bible): `~/.gradle/caches/neoformruntime/intermediate_results/mergeWithSources_6fbfc7ada1d9d9535f752d6ecbd648001fc0d25b_output.jar` — `unzip -p $JAR net/minecraft/path/Class.java` to read any vanilla source.
- Full 26.1.2 class index: `/tmp/mc26-classes.txt` (regenerate: `unzip -l $JAR | grep '\.class$' | sed 's/\.class$//; s|/|.|g'`).
- Compile logs: `/tmp/sable-compile-N.log`; bucket with `grep ': error:' | cut -d: -f1 | sort | uniq -c | sort -rn`.

## Key migration references

- NeoForged primers: <https://github.com/neoforged/.github/tree/main/primers> (1.21.2→…→26.1 cumulative; biggest: 1.21.2 entity render states, 1.21.4 item models/ItemStackRenderState, 1.21.6 GUI/render pipeline, 26.1 Java 25 + deobf + loot unrolling + data-component lazy init)
- NeoForge 26.1 announcement: <https://neoforged.net/news/26.1release/>
