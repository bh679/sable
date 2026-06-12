package dev.ryanhcode.sable.neoforge.platform;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.platform.SablePlotPlatform;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.world.LevelChunkAuxiliaryLightManager;
import org.slf4j.Logger;

@SuppressWarnings("UnstableApiUsage")
public class SablePlotPlatformImpl implements SablePlotPlatform {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void readLightData(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        if (tag.contains(LevelChunkAuxiliaryLightManager.LIGHT_NBT_KEY)) {
            // mc26.1: deserializeNBT lost the RegistryAccess param
            chunk.getAuxLightManager(chunk.getPos()).deserializeNBT(tag.getListOrEmpty(LevelChunkAuxiliaryLightManager.LIGHT_NBT_KEY));
        }
    }

    @Override
    public void readChunkAttachments(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        if (tag.contains(AttachmentHolder.ATTACHMENTS_NBT_KEY)) {
            chunk.readAttachmentsFromNBT(registryAccess, tag.getCompoundOrEmpty(AttachmentHolder.ATTACHMENTS_NBT_KEY));
        }
    }

    @Override
    public void postLoad(final CompoundTag tag, final LevelChunk chunk) {
        // PORT-NOTE(mc26.1): ChunkDataEvent.Load now requires SerializableChunkData
        // rather than a raw tag; plot chunks no longer fire it. Niche mod-compat
        // only — re-evaluate if an attachment mod needs the event on plots.
    }

    @Override
    public void writeLightData(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        final Tag lightTag = chunk.getAuxLightManager(chunk.getPos()).serializeNBT();
        if (lightTag != null) {
            tag.put(LevelChunkAuxiliaryLightManager.LIGHT_NBT_KEY, lightTag);
        }
    }

    @Override
    public void writeChunkAttachments(final CompoundTag tag, final RegistryAccess registryAccess, final LevelChunk chunk) {
        try {
            final CompoundTag capTag = chunk.writeAttachmentsToNBT(registryAccess);
            if (capTag != null) {
                tag.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, capTag);
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to write chunk attachments. An attachment has likely thrown an exception trying to write state. It will not persist. Report this to the mod author", e);
        }
    }
}
