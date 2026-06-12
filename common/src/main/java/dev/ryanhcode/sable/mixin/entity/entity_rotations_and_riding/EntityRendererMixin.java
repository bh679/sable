package dev.ryanhcode.sable.mixin.entity.entity_rotations_and_riding;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinhelpers.camera.camera_rotation.EntitySubLevelRotationHelper;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    // PORT-NOTE(mc26.1): EntityRenderer.renderNameTag and EntityRenderDispatcher.cameraOrientation() no
    // longer exist — name tags are billboarded inside NameTagFeatureRenderer using
    // CameraRenderState.orientation, with no Entity in reach. require = 0 keeps the game bootable;
    // re-target when the render cluster is ported (likely needs a NameTagFeatureRenderer mixin plus an
    // orientation field on the entity render state).
    @Redirect(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;cameraOrientation()Lorg/joml/Quaternionf;"), require = 0)
    private Quaternionf sable$renderNameTag(final EntityRenderDispatcher instance, @Local(argsOnly = true) final Entity entity) {
        if (!EntitySubLevelUtil.shouldKick(entity)) {
            return instance.camera.rotation();
        }

        final float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        final Quaterniond orientation = EntitySubLevelRotationHelper.getEntityOrientation(entity, x -> ((ClientSubLevel) x).renderPose(), pt, EntitySubLevelRotationHelper.Type.ENTITY);
        if (orientation == null) {
            return instance.camera.rotation();
        }

        return new Quaternionf(orientation).conjugate().mul(instance.camera.rotation());
    }

}
