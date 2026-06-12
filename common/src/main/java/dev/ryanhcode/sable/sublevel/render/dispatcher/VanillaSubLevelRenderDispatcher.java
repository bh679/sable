package dev.ryanhcode.sable.sublevel.render.dispatcher;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4fc;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;

public class VanillaSubLevelRenderDispatcher implements SubLevelRenderDispatcher {

    // mc26.1 port branch: the Veil-driven dynamic shading / sky-light shadow
    // uniforms and the spherical-fog override were stripped together with the
    // Veil shader preprocessors; plots use plain vanilla chunk shading.

    public VanillaSubLevelRenderDispatcher() {
    }

    /**
     * Checks if this sub-level is a single block, and therefore can use simpler batched rendering.
     *
     * <p>PORT-TODO(mc26.1): the single-block fast path (VanillaSingleSubLevelRenderData) drew
     * through a Tesselator pass driven by ShaderInstance uniforms, which no longer exist.
     * Until it is rebuilt on the new draw pipeline, single-block sub-levels render through the
     * chunked path — correct, just not batched — so this always reports false.
     */
    public static boolean isSingleBlock(final ClientSubLevel subLevel) {
        return false;
    }

    @Override
    public void onResourceManagerReload(@NotNull final ResourceManager resourceManager) {
    }

    @Override
    public SubLevelRenderData resize(final ClientSubLevel subLevel, final SubLevelRenderData renderData) {
        if (renderData instanceof VanillaSingleSubLevelRenderData ^ isSingleBlock(subLevel)) {
            renderData.close();

            // Force-rebuild the data
            final SubLevelRenderData data = this.createRenderData(subLevel);
            if (data instanceof VanillaChunkedSubLevelRenderData) {
                data.compileSections(PrioritizeChunkUpdates.NEARBY, new RenderRegionCache(), Minecraft.getInstance().gameRenderer.getMainCamera());
            }

            return data;
        }

        if (renderData instanceof final VanillaChunkedSubLevelRenderData chunkedRenderData) {
            chunkedRenderData.resize();
            chunkedRenderData.compileSections(PrioritizeChunkUpdates.NEARBY, new RenderRegionCache(), Minecraft.getInstance().gameRenderer.getMainCamera());
        }
        return renderData;
    }

    @Override
    public SubLevelRenderData createRenderData(final ClientSubLevel subLevel) {
        if (isSingleBlock(subLevel)) {
            return new VanillaSingleSubLevelRenderData(subLevel);
        }

        final SectionRenderDispatcher sectionRenderDispatcher = Minecraft.getInstance().levelRenderer.getSectionRenderDispatcher();
        return new VanillaChunkedSubLevelRenderData(subLevel, sectionRenderDispatcher);
    }

    @Override
    public void updateCulling(final Iterable<ClientSubLevel> sublevels, final double cameraX, final double cameraY, final double cameraZ, final Frustum cullFrustum, final boolean isSpectator) {
        // TODO
    }

    @Override
    public void appendChunkDraws(final Iterable<ClientSubLevel> sublevels,
                                 final EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> drawGroups,
                                 final List<DynamicUniforms.ChunkSectionInfo> sectionInfos,
                                 final int uboIndexOffset,
                                 final int[] maxIndexCount,
                                 final Matrix4fc vanillaModelView,
                                 final double cameraX, final double cameraY, final double cameraZ,
                                 final int atlasWidth, final int atlasHeight) {
        for (final ClientSubLevel sublevel : sublevels) {
            final SubLevelRenderData data = sublevel.getRenderData();

            // PORT-TODO(mc26.1): single-block sub-levels previously batched
            // through a Tesselator pass driven by ShaderInstance uniforms,
            // which no longer exist. They are skipped until rebuilt on the new
            // draw pipeline. (Dungeon Train plots are always multi-block.)
            if (!(data instanceof final VanillaChunkedSubLevelRenderData chunkedRenderData)) {
                continue;
            }

            chunkedRenderData.appendChunkDraws(drawGroups, sectionInfos, uboIndexOffset, maxIndexCount, vanillaModelView, cameraX, cameraY, cameraZ, atlasWidth, atlasHeight);
        }
    }

    @Override
    public void renderBlockEntities(final Iterable<ClientSubLevel> sublevels, final BlockEntityRenderer blockEntityRenderer, final double cameraX, final double cameraY, final double cameraZ, final float partialTick) {
        // PORT-TODO(mc26.1): block entities on sub-levels are not rendered yet.
        // The vanilla path moved to extract/submit (LevelRenderState +
        // SubmitNodeCollector); the old camera-spoofing dispatcher hook and
        // per-BE render call were removed with it. Re-implement by extracting
        // plot block entities with the plot transform applied to their poses.
    }

    @Override
    public void addDebugInfo(final Consumer<String> consumer) {
    }

    @Override
    public void free() {
    }
}
