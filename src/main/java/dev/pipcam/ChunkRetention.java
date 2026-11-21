package dev.pipcam;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Field;

/**
 * Keeps chunks around the pinned camera visible after the server tells the client to forget them (because the
 * player walked away). Purely client side: the server is never asked for anything. Retained chunks are frozen at
 * their last known state until the player comes back into range, when the server sends fresh data again.
 *
 * <p>Mechanism: the forget packet is swallowed for wanted chunks; if Minecraft later reuses the chunk's storage slot,
 * the chunk is kept in {@link #ghosts} and served from {@code ClientChunkCache.getChunk}. Sodium is never told the
 * chunk was unloaded, so its already-built meshes stay. When a chunk is no longer wanted it is unloaded the normal way.
 */
@EventBusSubscriber(modid = PipCamMod.MODID, value = Dist.CLIENT)
public final class ChunkRetention {
    /** Positions whose forget packet we swallowed (server no longer tracks them for us). */
    private static final LongSet retained = new LongOpenHashSet();
    /** Retained chunk objects, so they can still be served after leaving Minecraft's ring buffer. */
    private static final Long2ObjectMap<LevelChunk> ghosts = new Long2ObjectOpenHashMap<>();
    private static ClientLevel trackedLevel;
    private static boolean bypass;
    private static int tickCounter;
    private static Field storageField;
    private static boolean storageFailed;

    private ChunkRetention() {}

    public static int retainedCount() {
        return retained.size();
    }

    public static void clear() {
        retained.clear();
        ghosts.clear();
        trackedLevel = null;
    }

    public static boolean wanted(int x, int z) {
        PipState s = PipState.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        // Keep retained chunks while pinned & enabled (even if temporarily hidden) so tapping Hide doesn't lose them.
        if (!s.pinned || !s.retainChunks || mc.level == null) return false;
        if (s.dimension != null && !s.dimension.equals(mc.level.dimension())) return false;
        int cx = Mth.floor(s.x) >> 4, cz = Mth.floor(s.z) >> 4;
        return Math.max(Math.abs(x - cx), Math.abs(z - cz)) <= s.retainRadiusChunks;
    }

    /** @return true to swallow the server's forget packet. */
    public static boolean onForget(ChunkPos pos) {
        if (bypass) return false;
        long k = pos.toLong();
        if (retained.contains(k)) return true;
        if (!wanted(pos.x, pos.z)) return false;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return false;
        // the client may already have re-centred its ring buffer, so look at the raw slot, not getChunk()
        LevelChunk chunk = rawChunk(level, pos.x, pos.z);
        if (chunk == null) return false;
        retained.add(k);
        ghosts.put(k, chunk);
        return true;
    }

    static LevelChunk rawChunk(ClientLevel level, int x, int z) {
        if (storageFailed || level == null) return null;
        try {
            if (storageField == null) {
                storageField = ClientChunkCache.class.getDeclaredField("storage");
                storageField.setAccessible(true);
            }
            return ((PipChunkStorage) storageField.get(level.getChunkSource())).pipcam$raw(x, z);
        } catch (ReflectiveOperationException e) {
            storageFailed = true;
            PipCamMod.LOGGER.error("PiP chunk retention unavailable", e);
            return null;
        }
    }

    /** Called when Minecraft evicts a chunk from its ring buffer. @return true if we keep it (skip unload). */
    public static boolean keepOnEvict(LevelChunk chunk) {
        long k = chunk.getPos().toLong();
        if (!retained.contains(k)) {
            if (bypass || !wanted(chunk.getPos().x, chunk.getPos().z)) return false;
            retained.add(k); // evicted before its forget packet arrived
        }
        ghosts.put(k, chunk);
        return true;
    }

    public static LevelChunk ghost(int x, int z) {
        return ghosts.isEmpty() ? null : ghosts.get(ChunkPos.asLong(x, z));
    }

    /** The server sent this chunk again: it is live, stop retaining. */
    public static void onFresh(int x, int z) {
        long k = ChunkPos.asLong(x, z);
        if (retained.remove(k)) ghosts.remove(k);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != trackedLevel) {
            // world/dimension change or disconnect: old level is discarded wholesale
            retained.clear();
            ghosts.clear();
            trackedLevel = mc.level;
        }
        if (mc.level == null || retained.isEmpty() || ++tickCounter % 10 != 0) return;
        LongArrayList release = new LongArrayList();
        for (long k : retained) {
            if (!wanted(ChunkPos.getX(k), ChunkPos.getZ(k))) release.add(k);
        }
        for (long k : release) release(mc, k);
    }

    private static void release(Minecraft mc, long k) {
        ChunkPos pos = new ChunkPos(k);
        retained.remove(k);
        LevelChunk ghost = ghosts.remove(k);
        ClientLevel level = mc.level;
        if (level == null) return;
        // Check before forgetting so we don't fire duplicate level.unload(ghost) events.
        boolean inStorage = ghost != null && rawChunk(level, pos.x, pos.z) == ghost;
        if (mc.getConnection() != null) {
            bypass = true;
            try {
                // vanilla path: drops it from storage if still there, fires unload events, removes light data
                mc.getConnection().handleForgetLevelChunk(new ClientboundForgetLevelChunkPacket(pos));
            } finally {
                bypass = false;
            }
        }
        if (ghost != null && !inStorage) {
            level.unload(ghost);
            removeLight(level, pos);
        }
        SodiumBridge.chunkRemoved(level, pos.x, pos.z);
    }

    private static void removeLight(ClientLevel level, ChunkPos pos) {
        level.queueLightUpdate(() -> {
            LevelLightEngine light = level.getLightEngine();
            light.setLightEnabled(pos, false);
            for (int i = light.getMinLightSection(); i < light.getMaxLightSection(); i++) {
                SectionPos sp = SectionPos.of(pos, i);
                light.queueSectionData(LightLayer.BLOCK, sp, null);
                light.queueSectionData(LightLayer.SKY, sp, null);
            }
            for (int j = level.getMinSection(); j < level.getMaxSection(); j++) {
                light.updateSectionStatus(SectionPos.of(pos, j), true);
            }
        });
    }
}
