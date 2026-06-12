package dev.ryanhcode.sable.sublevel.render.dispatcher;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.index.SableTags;
import dev.ryanhcode.sable.mixinterface.BlockEntityRenderDispatcherExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaSingleSubLevelRenderData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Consumer;

public class VanillaSubLevelRenderDispatcher implements SubLevelRenderDispatcher {

    // mc26.1 port branch: the Veil-driven dynamic shading / sky-light shadow
    // uniforms and the spherical-fog override were stripped together with the
    // Veil shader preprocessors; plots use plain vanilla chunk shading.

    public VanillaSubLevelRenderDispatcher() {
    }

    /**
     * Checks if this sub-level is a single block, and therefore can use simpler batched rendering
     */
    public static boolean isSingleBlock(final ClientSubLevel subLevel) {
        final BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        final boolean isSingle = bounds != null && bounds.minX() == bounds.maxX() && bounds.minY() == bounds.maxY() && bounds.minZ() == bounds.maxZ();
        if (!isSingle) {
            return false;
        }

        final BlockState blockState = subLevel.getLevel().getBlockState(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()));
        return !blockState.is(SableTags.ALWAYS_CHUNK_RENDERING);
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
    public void renderSectionLayer(final Iterable<ClientSubLevel> sublevels, final RenderType renderType, final double cameraX, final double cameraY, final double cameraZ, final Matrix4f modelView, final Matrix4f projection, final float partialTicks) {
        for (final ClientSubLevel sublevel : sublevels) {
            final SubLevelRenderData data = sublevel.getRenderData();

            // PORT-TODO(mc26.1): single-block sub-levels previously batched
            // into a Tesselator pass driven by ShaderInstance uniforms, which
            // no longer exist. They are skipped until the single-block path is
            // rebuilt on the 26.1 draw pipeline. (Dungeon Train plots are
            // always multi-block, so this does not affect DT.)
            if (!(data instanceof final VanillaChunkedSubLevelRenderData chunkedRenderData)) {
                continue;
            }

            chunkedRenderData.renderChunkedSubLevel(renderType, modelView, cameraX, cameraY, cameraZ);
        }
    }

    @Override
    public void renderAfterSections(final Iterable<ClientSubLevel> sublevels, final double cameraX, final double cameraY, final double cameraZ, final Matrix4f modelView, final Matrix4f projection, final float partialTicks) {
        // PORT-TODO(mc26.1): see renderSectionLayer — single-block batch pass
        // removed with ShaderInstance; rebuild on the new draw pipeline.
    }

    @Override
    public void renderBlockEntities(final Iterable<ClientSubLevel> sublevels, final BlockEntityRenderer blockEntityRenderer, final double cameraX, final double cameraY, final double cameraZ, final float partialTick) {
        final Vector3f cameraPosition = new Vector3f();
        final Vector3d chunkOffset = new Vector3d();
        final Matrix4f transformation = new Matrix4f();
        final Matrix4f transformationInverse = new Matrix4f();
        final BlockEntityRenderDispatcherExtension dispatcher = (BlockEntityRenderDispatcherExtension) blockEntityRenderer.getBlockEntityRenderDispatcher();

        for (final ClientSubLevel sublevel : sublevels) {
            final SubLevelRenderData data = sublevel.getRenderData();

            sublevel.renderPose().rotationPoint().negate(chunkOffset.zero());
            data.getTransformation(cameraX, cameraY, cameraZ, transformation);

            transformation.invert(transformationInverse).transformPosition(cameraPosition.zero());
            dispatcher.sable$setCameraPosition(new Vec3(cameraPosition.x - chunkOffset.x(), cameraPosition.y - chunkOffset.y(), cameraPosition.z - chunkOffset.z()));

            final PoseStack matrices = new PoseStack();
            matrices.mulPose(transformation);
            if (data instanceof final VanillaChunkedSubLevelRenderData chunkedRenderData) {
                for (final SectionRenderDispatcher.RenderSection renderSection : chunkedRenderData.allRenderSections()) {
                    final List<BlockEntity> blockEntities = renderSection.getCompiled().getRenderableBlockEntities();
                    if (!blockEntities.isEmpty()) {
                        blockEntityRenderer.renderBlockEntities(blockEntities, matrices, partialTick, -chunkOffset.x, -chunkOffset.y, -chunkOffset.z);
                    }
                }
            } else if (data instanceof final VanillaSingleSubLevelRenderData singleRenderData) {
                final BlockEntity renderBlockEntity = singleRenderData.getRenderBlockEntity();
                if (renderBlockEntity != null) {
                    blockEntityRenderer.renderSingleBE(renderBlockEntity, matrices, partialTick, -chunkOffset.x, -chunkOffset.y, -chunkOffset.z);
                }
            }
        }

        dispatcher.sable$setCameraPosition(null);
    }

    @Override
    public void addDebugInfo(final Consumer<String> consumer) {
    }

    @Override
    public void free() {
    }
}
