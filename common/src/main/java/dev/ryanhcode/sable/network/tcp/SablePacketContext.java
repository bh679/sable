package dev.ryanhcode.sable.network.tcp;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Loader-agnostic packet handling context.
 *
 * <p>Replaces Veil's {@code foundry.veil.api.network.handler.PacketContext} on the
 * mc26.1 port branch (Veil has no 26.1 build). Only the two accessors Sable's
 * handlers actually use are exposed; the NeoForge module adapts its payload
 * context to this interface at registration time.
 */
public interface SablePacketContext {

    /**
     * @return the level of the handling player (client level for clientbound
     * packets, the sending player's server level for serverbound packets)
     */
    Level level();

    /**
     * @return the handling player (the local client player for clientbound
     * packets, the sending server player for serverbound packets)
     */
    Player player();
}
