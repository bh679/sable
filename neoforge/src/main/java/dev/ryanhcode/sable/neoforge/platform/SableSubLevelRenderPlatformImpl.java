package dev.ryanhcode.sable.neoforge.platform;

import dev.ryanhcode.sable.platform.SableSubLevelRenderPlatform;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class SableSubLevelRenderPlatformImpl implements SableSubLevelRenderPlatform {

    @Override
    public void tryAddFlywheelVisual(final BlockEntity blockEntity) {
        // Flywheel compat stripped on the mc26.1 port branch (no 26.1 build).
    }
}
