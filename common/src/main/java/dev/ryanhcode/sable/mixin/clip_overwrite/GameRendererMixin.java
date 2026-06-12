package dev.ryanhcode.sable.mixin.clip_overwrite;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Changes the block picking distance check to take into account sublevels
 */
// mc26.1: GameRenderer#pick/filterHitResult moved into Minecraft#pick(float).
@Mixin(Minecraft.class)
public class GameRendererMixin {

    @Redirect(method = "pick(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;closerThan(Lnet/minecraft/core/Position;D)Z"), require = 0) // PORT-NOTE(mc26.1): filterHitResult folded into pick
    private static boolean sable$closerThan(final Vec3 a, final Position b, final double d) {
        return Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, a, new Vec3(b.x(), b.y(), b.z())) < d * d;
    }

    @Redirect(method = "pick(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"), require = 0)
    private double sable$distanceToSqr(final Vec3 instance, final Vec3 other) {
        return Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, instance, other);
    }

    @Redirect(method = "pick(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;"), require = 0)
    private Vec3 sable$getEyePosition(final Entity instance, final float partialTicks) {
        return Sable.HELPER.getEyePositionInterpolated(instance, partialTicks);
    }

    @WrapMethod(method = "pick(F)V")
    private void sable$pickWithRenderPoses(final float f, final Operation<Void> original) {
        final Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.level == null) {
            original.call(f);
            return;
        }
        final LevelPoseProviderExtension extension = ((LevelPoseProviderExtension) minecraft.level);

        extension.sable$pushPoseSupplier((subLevel) -> ((ClientSubLevel) subLevel).renderPose(f));
        original.call(f);
        extension.sable$popPoseSupplier();
    }

}
