package dev.ryanhcode.sable.index;

import net.minecraft.server.level.TicketType;

/**
 * Sable's chunk ticket types. On MC 26.1 {@link TicketType} is a registered
 * record (timeout + flags) instead of a comparator-keyed generic, so the
 * loader module registers the instance during its registration phase and
 * assigns it here before any level exists.
 */
public final class SableTicketTypes {

    /**
     * Keeps chunks inhabited by force-loaded sub-levels at entity-ticking
     * level. Assigned by the loader module (NeoForge: registry event) during
     * common setup — never null by the time a server level ticks.
     */
    public static TicketType SUB_LEVEL_LOADED;

    private SableTicketTypes() {
    }
}
