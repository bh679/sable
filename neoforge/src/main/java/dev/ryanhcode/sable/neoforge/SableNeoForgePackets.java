package dev.ryanhcode.sable.neoforge;

import dev.ryanhcode.sable.network.tcp.SableTCPPacket;
import dev.ryanhcode.sable.network.tcp.SableTCPPackets;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers Sable's TCP payloads with NeoForge networking. Replaces Veil's
 * {@code VeilPacketManager} on the mc26.1 port branch.
 */
public final class SableNeoForgePackets {

    private SableNeoForgePackets() {
    }

    public static void register(final RegisterPayloadHandlersEvent event) {
        // "1" matches the protocol version previously passed to VeilPacketManager.create(MOD_ID, "1")
        final PayloadRegistrar registrar = event.registrar("1");
        for (final SableTCPPackets.Entry<?> entry : SableTCPPackets.entries()) {
            register(registrar, entry);
        }
    }

    private static <T extends SableTCPPacket> void register(final PayloadRegistrar registrar, final SableTCPPackets.Entry<T> entry) {
        if (entry.clientbound()) {
            registrar.playToClient(entry.type(), entry.codec(), (payload, context) -> payload.handle(new NeoForgeSablePacketContext(context)));
        } else {
            registrar.playToServer(entry.type(), entry.codec(), (payload, context) -> payload.handle(new NeoForgeSablePacketContext(context)));
        }
    }
}
