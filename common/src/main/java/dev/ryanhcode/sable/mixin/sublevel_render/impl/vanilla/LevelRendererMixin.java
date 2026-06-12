package dev.ryanhcode.sable.mixin.sublevel_render.impl.vanilla;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.SubLevelRenderData;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.List;

@Mixin(value = LevelRenderer.class, priority = 1002)
public abstract class LevelRendererMixin {

    @Shadow
    private @Nullable ClientLevel level;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "compileSections", at = @At("TAIL"))
    private void sable$compileSections(final Camera camera, final CallbackInfo ci) {
        final Iterable<ClientSubLevel> sublevels = ((ClientSubLevelContainer) ((SubLevelContainerHolder) this.level).sable$getPlotContainer()).getAllSubLevels();
        final RenderRegionCache renderRegionCache = new RenderRegionCache();
        final PrioritizeChunkUpdates chunkUpdates = Minecraft.getInstance().options.prioritizeChunkUpdates().get();

        for (final ClientSubLevel sublevel : sublevels) {
            sublevel.getRenderData().compileSections(chunkUpdates, renderRegionCache, camera);
        }
    }

    @Inject(method = "cullTerrain", at = @At("HEAD"))
    public void sable$cull(final Camera camera, final Frustum frustum, final boolean spectator, final CallbackInfo ci) {
        final SubLevelRenderDispatcher dispatcher = SubLevelRenderDispatcher.get();
        dispatcher.preRenderChunks(camera);

        final ProfilerFiller profiler = net.minecraft.util.profiling.Profiler.get();
        profiler.push("sub_level_section_occlusion_graph");

        final Iterable<ClientSubLevel> sublevels = ((ClientSubLevelContainer) ((SubLevelContainerHolder) this.level).sable$getPlotContainer()).getAllSubLevels();
        final Vec3 cameraPosition = camera.position();
        dispatcher.updateCulling(sublevels, cameraPosition.x, cameraPosition.y, cameraPosition.z, frustum, spectator);

        profiler.pop();
    }

    @Inject(method = "isSectionCompiledAndVisible", at = @At("HEAD"), cancellable = true)
    private void sable$isSectionCompiled(final BlockPos blockPos, final CallbackInfoReturnable<Boolean> cir) {
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container == null) {
            return;
        }

        if (container.inBounds(blockPos)) {
            final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(this.level, blockPos);

            if (subLevel == null) {
                cir.setReturnValue(false);
            } else {
                final SubLevelRenderData renderData = subLevel.getRenderData();
                final SectionPos sectionPos = SectionPos.of(blockPos);
                cir.setReturnValue(renderData.isSectionCompiled(sectionPos.x(), sectionPos.y(), sectionPos.z()));
            }
        }
    }

    /**
     * Appends sub-level section draws to the frame's chunk renders.
     *
     * <p>mc26.1: implemented as a return-value modification — the returned
     * {@code ChunkSectionsToRender} record's draw-group maps are appended to
     * in place, and our per-section uniforms are written as an additional
     * {@code DynamicUniforms} batch whose slices are concatenated after
     * vanilla's (draw uploaders index into the merged array via an offset).
     */
    @ModifyReturnValue(method = "prepareChunkRenders", at = @At("RETURN"))
    private ChunkSectionsToRender sable$appendSubLevelDraws(final ChunkSectionsToRender original,
                                                            @Local(argsOnly = true) final Matrix4fc modelViewMatrix) {
        if (this.level == null) {
            return original;
        }

        final Iterable<ClientSubLevel> sublevels = ((ClientSubLevelContainer) ((SubLevelContainerHolder) this.level).sable$getPlotContainer()).getAllSubLevels();
        final Camera camera = this.minecraft.gameRenderer.getMainCamera();
        final Vec3 cameraPosition = camera.position();

        final var atlas = this.minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        final int atlasWidth = atlas.getWidth(0);
        final int atlasHeight = atlas.getHeight(0);

        final GpuBufferSlice[] vanillaSlices = original.chunkSectionInfos();
        final java.util.List<DynamicUniforms.ChunkSectionInfo> ourInfos = new java.util.ArrayList<>();
        final int[] maxIndexCount = {original.maxIndicesRequired()};

        SubLevelRenderDispatcher.get().appendChunkDraws(sublevels, original.drawGroupsPerLayer(), ourInfos, vanillaSlices.length,
                maxIndexCount, modelViewMatrix, cameraPosition.x, cameraPosition.y, cameraPosition.z, atlasWidth, atlasHeight);

        if (ourInfos.isEmpty()) {
            return original;
        }

        final GpuBufferSlice[] ourSlices = com.mojang.blaze3d.systems.RenderSystem.getDynamicUniforms()
                .writeChunkSections(ourInfos.toArray(new DynamicUniforms.ChunkSectionInfo[0]));
        final GpuBufferSlice[] merged = new GpuBufferSlice[vanillaSlices.length + ourSlices.length];
        System.arraycopy(vanillaSlices, 0, merged, 0, vanillaSlices.length);
        System.arraycopy(ourSlices, 0, merged, vanillaSlices.length, ourSlices.length);

        return new ChunkSectionsToRender(original.textureView(), original.drawGroupsPerLayer(), maxIndexCount[0], merged);
    }
}