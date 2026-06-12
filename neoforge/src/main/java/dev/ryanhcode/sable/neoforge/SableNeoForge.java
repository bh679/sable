package dev.ryanhcode.sable.neoforge;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableCommonEvents;
import dev.ryanhcode.sable.SableConfig;
import dev.ryanhcode.sable.command.SableCommand;
import dev.ryanhcode.sable.command.argument.SubLevelSelectorModifiers;
import dev.ryanhcode.sable.index.SableAttributes;
import dev.ryanhcode.sable.index.SableTicketTypes;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertiesDefinitionLoader;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.CrashReportCallables;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Sable.MOD_ID)
public final class SableNeoForge {
    public SableNeoForge(final ModContainer modContainer, final IEventBus modBus) {
        Sable.init();

        final IEventBus neoBus = NeoForge.EVENT_BUS;
        neoBus.addListener(this::registerCommand);
        neoBus.addListener(this::registerReloadListeners);
        modBus.addListener(this::serverSetup);
        modBus.addListener(SableNeoForgePackets::register);
        neoBus.addListener(this::syncDataPack);

        SubLevelSelectorModifiers.registerModifiers();

        final DeferredRegister<Attribute> attributes = DeferredRegister.create(BuiltInRegistries.ATTRIBUTE, Sable.MOD_ID);
        SableAttributes.PUNCH_STRENGTH = attributes.register(SableAttributes.PUNCH_STRENGTH_NAME, () -> SableAttributes.PUNCH_STRENGTH_ATTRIBUTE);
        SableAttributes.PUNCH_COOLDOWN = attributes.register(SableAttributes.PUNCH_COOLDOWN_NAME, () -> SableAttributes.PUNCH_COOLDOWN_ATTRIBUTE);
        attributes.register(modBus);

        // mc26.1: TicketType is a registered record now — register Sable's
        // force-load ticket type and publish it to the common holder.
        final DeferredRegister<TicketType> ticketTypes = DeferredRegister.create(Registries.TICKET_TYPE, Sable.MOD_ID);
        final var subLevelLoaded = ticketTypes.register("sub_level_loaded",
                () -> new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION));
        ticketTypes.register(modBus);
        modBus.<FMLCommonSetupEvent>addListener(event -> SableTicketTypes.SUB_LEVEL_LOADED = subLevelLoaded.get());

        modContainer.registerConfig(ModConfig.Type.COMMON, SableConfig.SPEC);

        CrashReportCallables.registerHeader(Sable::getCrashHeader);
    }

    public void registerReloadListeners(final AddServerReloadListenersEvent event) {
        // mc26.1: server reload listeners are registered with an Identifier key.
        event.addListener(Sable.sablePath("physics_block_properties"), PhysicsBlockPropertiesDefinitionLoader.INSTANCE);
        event.addListener(Sable.sablePath("dimension_physics"), DimensionPhysicsData.ReloadListener.INSTANCE);
        event.addListener(Sable.sablePath("floating_block_materials"), FloatingBlockMaterialDataHandler.ReloadListener.INSTANCE);
    }

    private void serverSetup(final FMLCommonSetupEvent event) {
        SableAttributes.register();
    }

    private void registerCommand(final RegisterCommandsEvent event) {
        SableCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    private void syncDataPack(final OnDatapackSyncEvent event) {
        SableCommonEvents.syncDataPacket(payloads -> event.getRelevantPlayers().forEach(player -> {
            for (final var payload : payloads) {
                player.connection.send(payload);
            }
        }));
    }
}
