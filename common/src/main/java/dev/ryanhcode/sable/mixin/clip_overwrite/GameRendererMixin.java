package dev.ryanhcode.sable.mixin.clip_overwrite;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Makes sub-levels raycast against their render poses while picking.
 */
// PORT-NOTE(mc26.1): upstream wrapped the GameRenderer#pick(F)V call in GameRenderer#renderLevel;
// picking now lives in Minecraft#pick(F)V (which delegates to LocalPlayer#raycastHitResult — the
// distance-check redirects live in clip_overwrite.LocalPlayerMixin).
@Mixin(Minecraft.class)
public class GameRendererMixin {

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
