# Sable → MC 26.1.2 Port (unofficial, Dungeon Train)

Working branch: `port/mc26.1` (fork default branch — `main` stays the pristine upstream
baseline `48f5c24` for diffing) · Upstream: [ryanhcode/sable](https://github.com/ryanhcode/sable) (1.21.1)
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
- [x] **Plots render, can be targeted, stood on, and broken client-side** (verified in-game 2026-06-12; single-block and multi-block — `/sable spawn platform`)
- [x] **Sub-level persistence verified** (2026-06-13: spawn → quit-to-title → rejoin restores plots, visually confirmed; verbose serialization trace showed save → holding-chunk write → disk read → re-allocation all healthy)

## Resolved: "client pose corruption" (plots invisible + untargetable, 2026-06-12)

The pose pipeline was never broken — `[POSE-DBG]` instrumentation at all four stages
(server `logicalPose` at send → client receive → interpolator output → `renderPose`)
showed sane values end-to-end. Two independent port regressions explained all symptoms:

1. **Pick distance checks silently no-opped** (`clip_overwrite.GameRendererMixin`):
   upstream redirects `Vec3.distanceToSqr`/`closerThan` in `GameRenderer#pick`/
   `#filterHitResult` to be sub-level aware, because Sable's clip returns hit locations
   in **plot space** (~2.05e7 blocks out; the "abnormally large AABB" min corners
   decomposed exactly as `unitViewVector × 2.9e7` = distance to the plot-space hit).
   26.1 moved that logic to `LocalPlayer#raycastHitResult`/`#pick`/`#filterHitResult`;
   the port's retarget at `Minecraft#pick(F)V` with `require = 0` matched nothing, so
   `filterHitResult` discarded every plot hit as an out-of-range MISS (→ untargetable)
   and `pick` sized the entity-search AABB at 2.9e7 (→ log spam). Fixed by
   `clip_overwrite.LocalPlayerMixin` (registered required, fails loudly on retarget).

2. **Single-block plots used the dormant fast-path renderer**: `/sable spawn block`
   plots route through `VanillaSingleSubLevelRenderData`, a Tesselator/ShaderInstance
   path that is PORT-TODO on this branch — skipped in `appendChunkDraws`, i.e.
   invisible by omission. `VanillaSubLevelRenderDispatcher.isSingleBlock` now returns
   false so single-block sub-levels render through the chunked path (correct, just not
   batched); rebuild the fast path on the new draw pipeline later for floating-block-
   heavy scenes.

The chunked draw path itself (`compileSections` → vanilla `SectionRenderDispatcher`
uber-buffers → draws appended in `prepareChunkRenders`'s `ChunkSectionsToRender`)
verified healthy: census showed meshes compiled, GPU slices found, draws appended at
sane `renderPos` every frame, zero GL errors.

**UDP findings worth keeping** (hypothesis 2 was dead on arrival for SP): in single
player, `SableUDPServer.sendUDPPacket` sees the player's `LocalAddress` and calls
`sendUDPPacketLocal` — a same-thread serialize→deserialize round trip handed straight
to the client network event loop. **The netty local UDP channel is never used for SP
snapshots** ("Client UDP channel active" is unconditional from `ConnectionMixin`).
`sable-client.toml attempt_udp_networking` only gates the client's response to the
auth handshake (remote servers); the SP gate is the **server** config flag in
`sable-common.toml`. Real netty UDP framing (`LocalFrameEncoder`/`HiddenByteBuf`
rename, KQueue datagram branch) remains untested until a dedicated-server test.

## Resolved: sub-level persistence (2026-06-13)

The earlier "plot missing after rejoin" observation (2026-06-12, 20:38 → 21:15) did not
reproduce on the current build: with `verbose_serialization_logging = true` (server
config, world-independent in `run/config/sable-common.toml`), the full cycle — saveAll
("Moving sub-level …" with pointer assignment) → chunk unload ("Unloading … as holding
sub-level") → disk write → rejoin ("Loaded chunk at [0, 0] from disk", "Attempting to
read pointer …") → re-allocation → client tracking — completed cleanly across two
quit-to-title/rejoin cycles, and the restored plots were visually confirmed in-game.
The earlier miss likely predated the render fixes (plots may have restored but been
invisible) — re-open only if it recurs. The upstream #679 PlotChunkHolder shutdown
leak remains unaudited (separate backlog item).

**Command syntax note** (cost a test run): `/sable assemble area` requires two corner
arguments — `/sable assemble area <x1> <y1> <z1> <x2> <y2> <z2>`; bare `area` is an
incomplete command. `/sable assemble connected` works with no args (assembles blocks
connected to the block under your feet). `/sable spawn platform` spawns a ready-made
multi-block plot.

## Runtime-dormant injectors (require = 0) — polish-pass backlog

Fixed properly during the boot loop: BlockAndLightGetter retarget (plot light), Block
`updateEntityMovementAfterFallOn`, Player `canFallAtLeast(DDD)`/interaction-range renames,
InterpolationHandler lerp rewrite, `snapTo`, arrow `isInGround()`, popResource LVT,
camera rotate-with-plot on 2-arg `setRotation`, `Minecraft#pick` retarget, furnace
`recipesUsed` type, world-border per-subclass link, `LevelChunk.setBlockState` flags,
ValueInput/ValueOutput save hooks, sea-level-during-ctor guard, config-loading
null-guard, moved-package target strings (`projectile/arrow/`), `tickInGameSound`,
swimming camera near-plane/level shadows.

Dormant (feature degrades to vanilla; re-port deliberately): single-block plot draw
fast path (single-block plots now render via the chunked path — only the batched
Tesselator fast path is missing), sub-level block entities (chests on plots invisible!),
entity render-pose smoothing +
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
- [x] runClient boots; plot assembles, moves, renders, persists (2026-06-13)
- [x] Save/quit clean — verified across quit-to-title/rejoin cycles (upstream #679 PlotChunkHolder shutdown-leak audit still pending)
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
