package dev.ryanhcode.sable.neoforge;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.SableClientConfig;
import dev.ryanhcode.sable.physics.config.FloatingBlockMaterialDataHandler;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = Sable.MOD_ID, dist = Dist.CLIENT)
public final class SableNeoForgeClient {

    public SableNeoForgeClient(final ModContainer modContainer, final IEventBus modBus) {
        final IEventBus neoBus = NeoForge.EVENT_BUS;

        SableClient.init();

        modContainer.registerConfig(ModConfig.Type.CLIENT, SableClientConfig.SPEC);
        modBus.<ModConfigEvent.Loading>addListener(event -> SableClientConfig.onUpdate(false));
        modBus.<ModConfigEvent.Reloading>addListener(event -> SableClientConfig.onUpdate(true));
        neoBus.<ClientPlayerNetworkEvent.LoggingOut>addListener(event -> {
            if (event.getPlayer() != null) { // LoggingOut may fire when logging in
                FloatingBlockMaterialDataHandler.clearMaterials();
            }
        });
        // mc26.1: client reload listeners are keyed by Identifier and the
        // PreparableReloadListener signature changed to (SharedState, Executor,
        // PreparationBarrier, Executor).
        modBus.<AddClientReloadListenersEvent>addListener(event -> event.addListener(Sable.sablePath("sub_level_renderer"),
                (currentReload, taskExecutor, preparationBarrier, reloadExecutor) -> SubLevelRenderDispatcher.get().reload(currentReload, taskExecutor, preparationBarrier, reloadExecutor)));
    }
}
