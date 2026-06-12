package dev.ryanhcode.sable.mixin.entity.entity_rendering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    // PORT-TODO(mc26.1): LevelRenderer#renderEntity and the direct
    // EntityRenderDispatcher#render call no longer exist — entity rendering
    // moved to extract/submit (extractVisibleEntities/submitEntities with
    // EntityRenderState). Both injections below are marked require = 0 so they
    // skip silently; entities on moving sub-levels render at their synced
    // world coordinates (no render-pose smoothing/rotation) until this is
    // re-targeted at EntityRenderer#extractRenderState.
    @Inject(method = "renderEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getYRot()F"), require = 0)
    private void renderEntityOnSubLevel(final Entity entity,
                                        final double cameraX,
                                        final double cameraY,
                                        final double cameraZ,
                                        final float partialTick,
                                        final PoseStack poseStack,
                                        final MultiBufferSource multiBufferSource,
                                        final CallbackInfo ci,
                                        @Local(ordinal = 3) final LocalDoubleRef entityX,
                                        @Local(ordinal = 4) final LocalDoubleRef entityY,
                                        @Local(ordinal = 5) final LocalDoubleRef entityZ,
                                        @Share("renderPose") final LocalRef<Pose3dc> renderPoseShare) {
        // Render the entity on the data
        final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(entity);

        if (subLevel == null) {
            // Tracking sub-levels
            final SubLevel trackingSubLevel = Sable.HELPER.getTrackingSubLevel(entity);

            if (trackingSubLevel instanceof final ClientSubLevel clientSubLevel && !entity.isPassenger()) {
                final Vector3d oldTrackingPosLocal = trackingSubLevel.lastPose().transformPositionInverse(new Vector3d(entity.xOld, entity.yOld, entity.zOld));
                final Vector3d newTrackingPosLocal = trackingSubLevel.logicalPose().transformPositionInverse(JOMLConversion.toJOML(entity.position()));

                final Vector3d interpolatedTrackingPosLocal = new Vector3d(
                        Mth.lerp(partialTick, oldTrackingPosLocal.x, newTrackingPosLocal.x),
                        Mth.lerp(partialTick, oldTrackingPosLocal.y, newTrackingPosLocal.y),
                        Mth.lerp(partialTick, oldTrackingPosLocal.z, newTrackingPosLocal.z)
                );

                final Pose3dc renderPose = clientSubLevel.renderPose(partialTick);
                renderPose.transformPosition(interpolatedTrackingPosLocal);

                entityX.set(interpolatedTrackingPosLocal.x);
                entityY.set(interpolatedTrackingPosLocal.y);
                entityZ.set(interpolatedTrackingPosLocal.z);
            }

            return;
        }

        final Pose3dc renderPose = subLevel.renderPose(partialTick);
        final Vector3d transformedPosition = renderPose.transformPosition(new Vector3d(entityX.get(), entityY.get(), entityZ.get()));

        renderPoseShare.set(renderPose);

        entityX.set(transformedPosition.x);
        entityY.set(transformedPosition.y);
        entityZ.set(transformedPosition.z);
    }

    @WrapOperation(method = "renderEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"), require = 0)
    private void renderEntity(final EntityRenderDispatcher instance,
                              final Entity entity,
                              final double x,
                              final double y,
                              final double z,
                              final float g,
                              final float h,
                              final PoseStack poseStack,
                              final MultiBufferSource multiBufferSource,
                              final int i,
                              final Operation<Void> original,
                              @Share("renderPose") final LocalRef<Pose3dc> renderPoseShare) {
        final Pose3dc pose = renderPoseShare.get();
        if (pose != null) {
            // mc26.1: Veil's MatrixStack bridge replaced with vanilla PoseStack ops.
            poseStack.pushPose();
            poseStack.rotateAround(new Quaternionf(pose.orientation()), (float) x, (float) y, (float) z);
            original.call(instance, entity, x, y, z, g, h, poseStack, multiBufferSource, i);
            poseStack.popPose();
        } else {
            original.call(instance, entity, x, y, z, g, h, poseStack, multiBufferSource, i);
        }
    }
}
