package dev.ryanhcode.sable.sublevel.render.vanilla;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.sublevel_render.vanilla.RenderSectionExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.*;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;

/**
 * A renderer and view area for a {@link dev.ryanhcode.sable.sublevel.SubLevel}.
 *
 * <p>mc26.1 port: drawing no longer happens here directly. Plot sections are
 * appended into vanilla's {@code ChunkSectionsToRender} draw groups inside
 * {@code LevelRenderer#prepareChunkRenders} (see the impl.vanilla
 * LevelRendererMixin) — each section carries its own model-view matrix, which
 * is how the plot's rotation/translation is applied.
 */
public class VanillaChunkedSubLevelRenderData implements SubLevelRenderData {

    private final Vector3d origin = new Vector3d();
    /**
     * The origin(minimum) of the render section grid
     */
    private final Vector3i chunkOrigin = new Vector3i();
    /**
     * The sub-level this renderer is for
     */
    private final ClientSubLevel subLevel;
    /**
     * The size of the render section grid
     */
    private final Vector3i size = new Vector3i();
    /**
     * All render sections this renderer stores
     */
    private final ObjectList<SectionRenderDispatcher.RenderSection> allRenderSections = new ObjectArrayList<>();
    /**
     * All dirty render sections this renderer stores
     */
    private final ObjectList<SectionRenderDispatcher.RenderSection> dirtyRenderSections = new ObjectArrayList<>();
    /**
     * The grid of render sections
     */
    private SectionRenderDispatcher.RenderSection[] renderSections = null;
    /**
     * The section render dispatcher to build sections through
     */
    private final SectionRenderDispatcher sectionRenderDispatcher;

    /**
     * Creates a new renderer for the given sub-level
     *
     * @param subLevel the sub-level to render
     */
    public VanillaChunkedSubLevelRenderData(final ClientSubLevel subLevel, final SectionRenderDispatcher sectionRenderDispatcher) {
        this.subLevel = subLevel;
        this.sectionRenderDispatcher = sectionRenderDispatcher;
        this.resize();
    }

    /**
     * Gets a section in global section coordinates
     */
    private static SectionRenderDispatcher.RenderSection getSection(final SectionRenderDispatcher.RenderSection[] sections, final Vector3i size, final Vector3i origin, final int x, final int y, final int z) {
        final int relX = (x - origin.x());
        final int relY = (y - origin.y());
        final int relZ = (z - origin.z());

        if (relX < 0 || relY < 0 || relZ < 0) {
            return null;
        }

        if (relX >= size.x() || relY >= size.y() || relZ >= size.z()) {
            return null;
        }

        return sections[relX + relY * size.x() + relZ * size.x() * size.y()];
    }

    /**
     * Gets an index in the render section grid from a global position
     */
    private int getIndex(final int x, final int y, final int z) {
        return (x - this.chunkOrigin.x()) + (y - this.chunkOrigin.y()) * this.size.x() + (z - this.chunkOrigin.z()) * this.size.x() * this.size.y();
    }

    /**
     * Checks if a global section coordinate is in bounds
     */
    private boolean inBounds(final int x, final int y, final int z) {
        final int localX = x - this.chunkOrigin.x();
        final int localY = y - this.chunkOrigin.y();
        final int localZ = z - this.chunkOrigin.z();
        return localX >= 0 && localY >= 0 && localZ >= 0 &&
                localX < this.size.x() && localY < this.size.y() && localZ < this.size.z();

    }

    // TODO(port-debug): remove [RENDER-DBG] counters once plot invisibility is fixed
    private static int sable$dbgAppendCallCounter;

    public void resize() {
        // TODO(port-debug): remove [RENDER-DBG] logging once plot invisibility is fixed
        dev.ryanhcode.sable.Sable.LOGGER.info("[RENDER-DBG] resize plot={} bounds={}",
                this.subLevel.getPlot().plotPos, this.subLevel.getPlot().getBoundingBox());

        final SectionRenderDispatcher.RenderSection[] oldRenderSections = this.renderSections;
        final Collection<SectionRenderDispatcher.RenderSection> oldRenderSectionsList = new ObjectArrayList<>(this.allRenderSections);

        this.renderSections = null;
        this.allRenderSections.clear();
        this.dirtyRenderSections.clear();

        final BoundingBox3ic bounds = this.subLevel.getPlot().getBoundingBox();

        if (bounds != null && !bounds.equals(BoundingBox3i.EMPTY) && bounds.volume() > 0.0) {
            final Vector3i minChunkPos = new Vector3i(bounds.minX() >> 4, bounds.minY() >> 4, bounds.minZ() >> 4);
            final Vector3i maxChunkPos = new Vector3i(bounds.maxX() >> 4, bounds.maxY() >> 4, bounds.maxZ() >> 4);

            final Vector3i oldSize = new Vector3i(this.size);
            final Vector3i oldOrigin = new Vector3i(this.chunkOrigin);

            this.size.set(maxChunkPos.x() - minChunkPos.x() + 1, maxChunkPos.y() - minChunkPos.y() + 1, maxChunkPos.z() - minChunkPos.z() + 1);
            this.chunkOrigin.set(minChunkPos);
            this.origin.set(minChunkPos.x() << 4, minChunkPos.y() << 4, minChunkPos.z() << 4);

            this.renderSections = new SectionRenderDispatcher.RenderSection[this.size.x() * this.size.y() * this.size.z()];

            for (int x = minChunkPos.x(); x <= maxChunkPos.x(); x++) {
                for (int y = minChunkPos.y(); y <= maxChunkPos.y(); y++) {
                    for (int z = minChunkPos.z(); z <= maxChunkPos.z(); z++) {
                        final SectionRenderDispatcher.RenderSection oldSection = getSection(oldRenderSections, oldSize, oldOrigin, x, y, z);
                        final SectionRenderDispatcher.RenderSection newSection;

                        if (oldRenderSections != null && oldSection != null) {
                            newSection = oldSection;
                        } else {
                            newSection = this.sectionRenderDispatcher.new RenderSection(-1, SectionPos.asLong(x, y, z));
                            ((RenderSectionExtension) newSection).sable$addDirtyListener(this.dirtyRenderSections::add);
                        }

                        if (newSection.isDirty()) {
                            this.dirtyRenderSections.add(newSection);
                        }
                        this.renderSections[this.getIndex(x, y, z)] = newSection;
                        this.allRenderSections.add(newSection);
                    }
                }
            }

            // free old chunks
            if (oldRenderSections != null) {
                for (final SectionRenderDispatcher.RenderSection oldSection : oldRenderSectionsList) {
                    // if not in bounds
                    final SectionPos oldSectionPos = SectionPos.of(oldSection.getRenderOrigin());
                    if (oldSectionPos.getX() < minChunkPos.x() || oldSectionPos.getX() > maxChunkPos.x() ||
                            oldSectionPos.getY() < minChunkPos.y() || oldSectionPos.getY() > maxChunkPos.y() ||
                            oldSectionPos.getZ() < minChunkPos.z() || oldSectionPos.getZ() > maxChunkPos.z()) {

                        oldSection.reset();
                    }
                }
            }
        }
    }

    @Override
    public void rebuild() {
        for (final SectionRenderDispatcher.RenderSection renderSection : this.allRenderSections) {
            renderSection.setDirty(true);
        }
    }

    @Override
    public void compileSections(final PrioritizeChunkUpdates chunkUpdates, final RenderRegionCache renderRegionCache, final Camera camera) {
        if (this.dirtyRenderSections.isEmpty()) {
            return;
        }

        // TODO(port-debug): remove [RENDER-DBG] logging once plot invisibility is fixed
        dev.ryanhcode.sable.Sable.LOGGER.info("[RENDER-DBG] compileSections scheduling {} dirty sections (total {})",
                this.dirtyRenderSections.size(), this.allRenderSections.size());

        final ProfilerFiller profiler = net.minecraft.util.profiling.Profiler.get();
        final Vector3d cameraPos = JOMLConversion.atCenterOf(camera.blockPosition()).sub(8, 8, 8);
        this.subLevel.logicalPose().transformPositionInverse(cameraPos);

        for (final SectionRenderDispatcher.RenderSection renderSection : this.dirtyRenderSections) {
            ((RenderSectionExtension) renderSection).sable$setListening(false);

            boolean buildSync = false;
            if (chunkUpdates == PrioritizeChunkUpdates.NEARBY) {
                final BlockPos origin = renderSection.getRenderOrigin();
                buildSync = cameraPos.distanceSquared(origin.getX(), origin.getY(), origin.getZ()) < 768.0 || renderSection.isDirtyFromPlayer();
            } else if (chunkUpdates == PrioritizeChunkUpdates.PLAYER_AFFECTED) {
                buildSync = renderSection.isDirtyFromPlayer();
            }

            if (buildSync) {
                profiler.push("sublevel_build_near_sync");
                this.sectionRenderDispatcher.rebuildSectionSync(renderSection, renderRegionCache);
                profiler.pop();
            } else {
                profiler.push("sublevel_schedule_async_compile");
                renderSection.rebuildSectionAsync(renderRegionCache);
                profiler.pop();
            }

            renderSection.setNotDirty();
            ((RenderSectionExtension) renderSection).sable$setListening(true);
        }
        this.dirtyRenderSections.clear();
    }

    @Override
    public int getVisibleSectionCount() {
        return this.allRenderSections.size();
    }

    @Override
    public ClientSubLevel getSubLevel() {
        return this.subLevel;
    }

    @Override
    public boolean isSectionCompiled(final int x, final int y, final int z) {
        if (this.renderSections == null) {
            return false;
        }

        if (!this.inBounds(x, y, z)) {
            return true;
        }

        final int index = this.getIndex(x, y, z);
        return index >= 0 && index < this.renderSections.length && this.renderSections[index].getSectionMesh() != CompiledSectionMesh.UNCOMPILED;
    }

    @Override
    public void setDirty(final int x, final int y, final int z, final boolean playerChanged) {
        if (this.renderSections == null) {
            return;
        }

        if (!this.inBounds(x, y, z)) {
            return;
        }

        final int index = this.getIndex(x, y, z);
        if (index >= 0 && index < this.renderSections.length) {
            this.renderSections[index].setDirty(playerChanged);
        }
    }

    /**
     * @return all render sections this renderer stores
     */
    public ObjectList<SectionRenderDispatcher.RenderSection> allRenderSections() {
        return this.allRenderSections;
    }

    /**
     * Appends this sub-level's section draws into vanilla's draw groups built
     * by {@code LevelRenderer#prepareChunkRenders}.
     *
     * <p>Math (terrain.vsh: {@code pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset},
     * {@code CameraOffset = floor(cam) - cam}): we pass
     * {@code ChunkPosition = sectionOriginPlotLocal + F} with the int fog
     * fudge {@code F = floor(renderPos)}, so {@code pos = u + F - cam} where
     * {@code u} is the plot-local position. The per-section matrix then maps
     * that exactly onto the rotated camera-relative position:
     * {@code M = V · translate(renderPos - cam) · rotate(q) · translate(cam - F)}.
     * Fog distances see {@code u + F - cam} ≈ unrotated camera-relative
     * position — same approximation Sable used pre-port.
     */
    public void appendChunkDraws(final EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<com.mojang.blaze3d.buffers.GpuBufferSlice[]>>>> drawGroups,
                                 final List<DynamicUniforms.ChunkSectionInfo> sectionInfos,
                                 final int uboIndexOffset,
                                 final int[] maxIndexCount,
                                 final Matrix4fc vanillaModelView,
                                 final double camX, final double camY, final double camZ,
                                 final int atlasWidth, final int atlasHeight) {
        if (this.renderSections == null || this.allRenderSections.isEmpty()) {
            return;
        }

        final Pose3dc renderPose = this.subLevel.renderPose();
        final Vector3d renderPos = new Vector3d(renderPose.position());
        final Quaterniondc renderRot = renderPose.orientation();
        final Vector3d renderCOR = renderRot.transform(new Vector3d(renderPose.rotationPoint()).sub(this.origin));
        renderPos.sub(renderCOR);

        // Int fog fudge: keeps shader-side positions near camera-relative magnitudes.
        final int fx = Mth.floor(renderPos.x());
        final int fy = Mth.floor(renderPos.y());
        final int fz = Mth.floor(renderPos.z());

        // M = vanillaMV · translate(renderPos − cam) · rotate(q) · translate(cam − F)
        final Matrix4f sectionMatrix = new Matrix4f(vanillaModelView);
        sectionMatrix.translate((float) (renderPos.x() - camX), (float) (renderPos.y() - camY), (float) (renderPos.z() - camZ));
        sectionMatrix.rotate(new Quaternionf(renderRot));
        sectionMatrix.translate((float) (camX - fx), (float) (camY - fy), (float) (camZ - fz));

        final long now = net.minecraft.util.Util.getMillis();
        int uboIndex = -1;

        // TODO(port-debug): remove [RENDER-DBG] census once plot invisibility is fixed
        final boolean dbgLogThisCall = (sable$dbgAppendCallCounter++ % 120) == 0;
        int dbgCompiledMeshes = 0;
        int dbgNullDraws = 0;
        int dbgNullSlices = 0;
        int dbgDrawsAppended = 0;

        for (final SectionRenderDispatcher.RenderSection renderSection : this.allRenderSections) {
            final SectionMesh sectionMesh = renderSection.getSectionMesh();
            final BlockPos sectionOrigin = renderSection.getRenderOrigin();
            int sectionUboIndex = -1;

            if (sectionMesh != CompiledSectionMesh.UNCOMPILED) {
                dbgCompiledMeshes++;
            }

            for (final ChunkSectionLayer layer : ChunkSectionLayer.values()) {
                final SectionMesh.SectionDraw draw = sectionMesh.getSectionDraw(layer);
                final SectionRenderDispatcher.RenderSectionBufferSlice slice = this.sectionRenderDispatcher.getRenderSectionSlice(sectionMesh, layer);
                if (slice == null || draw == null || (draw.hasCustomIndexBuffer() && slice.indexBuffer() == null)) {
                    if (draw == null) {
                        dbgNullDraws++;
                    } else if (slice == null) {
                        dbgNullSlices++;
                    }
                    continue;
                }
                dbgDrawsAppended++;

                if (sectionUboIndex == -1) {
                    sectionUboIndex = uboIndexOffset + sectionInfos.size();
                    sectionInfos.add(new DynamicUniforms.ChunkSectionInfo(
                            new Matrix4f(sectionMatrix),
                            (int) (sectionOrigin.getX() - this.origin.x()) + fx,
                            (int) (sectionOrigin.getY() - this.origin.y()) + fy,
                            (int) (sectionOrigin.getZ() - this.origin.z()) + fz,
                            renderSection.getVisibility(now),
                            atlasWidth,
                            atlasHeight
                    ));
                }

                final VertexFormat vertexFormat = layer.pipeline().getVertexFormat();
                final GpuBuffer vertexBuffer = slice.vertexBuffer();

                int firstIndex = 0;
                final GpuBuffer indexBuffer;
                final VertexFormat.IndexType indexType;
                if (!draw.hasCustomIndexBuffer()) {
                    if (draw.indexCount() > maxIndexCount[0]) {
                        maxIndexCount[0] = draw.indexCount();
                    }

                    indexBuffer = null;
                    indexType = null;
                } else {
                    indexBuffer = slice.indexBuffer();
                    indexType = draw.indexType();
                    firstIndex = (int) (slice.indexBufferOffset() / indexType.bytes);
                }

                // Unique-ish group hash per sub-level section batch — plot
                // sections never share UBO entries with vanilla terrain.
                final int combinedHash = 31 * (31 * 7919 + System.identityHashCode(vertexBuffer)) + sectionUboIndex;

                final int finalUboIndex = sectionUboIndex;
                final int baseVertex = (int) (slice.vertexBufferOffset() / vertexFormat.getVertexSize());
                final List<RenderPass.Draw<com.mojang.blaze3d.buffers.GpuBufferSlice[]>> draws = drawGroups.get(layer).computeIfAbsent(combinedHash, k -> new java.util.ArrayList<>());
                draws.add(new RenderPass.Draw<>(
                        0,
                        vertexBuffer,
                        indexBuffer,
                        indexType,
                        firstIndex,
                        draw.indexCount(),
                        baseVertex,
                        (sectionUbos, uploader) -> uploader.upload("ChunkSection", sectionUbos[finalUboIndex])
                ));
            }
        }

        // TODO(port-debug): remove [RENDER-DBG] census once plot invisibility is fixed
        if (dbgLogThisCall) {
            dev.ryanhcode.sable.Sable.LOGGER.info(
                    "[RENDER-DBG] appendChunkDraws plot={} sections={} compiledMeshes={} drawsAppended={} nullDraws={} nullSlices={} origin=({},{},{}) renderPos=({},{},{})",
                    this.subLevel.getPlot().plotPos, this.allRenderSections.size(), dbgCompiledMeshes, dbgDrawsAppended,
                    dbgNullDraws, dbgNullSlices,
                    this.origin.x(), this.origin.y(), this.origin.z(),
                    String.format("%.2f", renderPos.x()), String.format("%.2f", renderPos.y()), String.format("%.2f", renderPos.z()));
        }
    }

    @Override
    public void close() {
        for (final SectionRenderDispatcher.RenderSection section : this.allRenderSections) {
            section.reset();
        }
        this.allRenderSections.clear();
        this.renderSections = null;
    }

    public SectionRenderDispatcher.RenderSection getRenderSection(final SectionPos sectionPos) {
        if (this.renderSections == null) {
            return null;
        }

        final int index = this.getIndex(sectionPos.getX(), sectionPos.getY(), sectionPos.getZ());

        if (index < 0 || index >= this.renderSections.length) {
            return null;
        }

        return this.renderSections[index];
    }
}
