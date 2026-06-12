package dev.ryanhcode.sable.mixin.world_border;

import dev.ryanhcode.sable.mixinterface.world_border.WorldBorderExtension;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * mc26.1: the worldBorder field moved off Level — link the border to its level
 * from the subclass constructors instead (see ServerLevelMixin for the server side).
 */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sable$linkWorldBorder(final CallbackInfo ci) {
        final ClientLevel self = (ClientLevel) (Object) this;
        ((WorldBorderExtension) self.getWorldBorder()).sable$setLevel((Level) (Object) this);
    }
}
