package dev.ryanhcode.sable.neoforge;

import dev.ryanhcode.sable.network.tcp.SablePacketContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record NeoForgeSablePacketContext(IPayloadContext context) implements SablePacketContext {

    @Override
    public Level level() {
        return this.context.player().level();
    }

    @Override
    public Player player() {
        return this.context.player();
    }
}
