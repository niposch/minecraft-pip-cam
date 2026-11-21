package dev.pipcam;

import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.fml.loading.FMLLoader;

import java.lang.reflect.Method;

/**
 * Optional Sodium compatibility bridge using cached reflection so Sodium is neither a compile-time
 * nor runtime dependency.
 */
public final class SodiumBridge {
    private static final boolean PRESENT = FMLLoader.getLoadingModList().getModFileById("sodium") != null;
    private static final int FLAG_ALL = 3; // ChunkStatus.FLAG_ALL

    private static boolean initialized, failed;
    private static Method instanceNullable, isSectionReady, trackerGet, onChunkStatusRemoved;

    private SodiumBridge() {}

    public static boolean isPresent() {
        return PRESENT && !failed;
    }

    public static void chunkRemoved(ClientLevel level, int x, int z) {
        if (!PRESENT || failed || level == null) return;
        try {
            if (!initialized) init();
            Object tracker = trackerGet.invoke(null, level);
            if (tracker != null) onChunkStatusRemoved.invoke(tracker, x, z, FLAG_ALL);
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** True if Sodium has a built mesh for the section (or Sodium is absent and the chunk is loaded). */
    public static boolean sectionRendered(ClientLevel level, int x, int y, int z) {
        if (!level.hasChunk(x, z)) return false;
        if (!PRESENT || failed) return true;
        try {
            if (!initialized) init();
            Object renderer = instanceNullable.invoke(null);
            return renderer != null && (boolean) isSectionReady.invoke(renderer, x, y, z);
        } catch (Throwable t) {
            fail(t);
            return true;
        }
    }

    static Object worldRendererNullable() {
        if (!PRESENT || failed) return null;
        try {
            if (!initialized) init();
            return instanceNullable.invoke(null);
        } catch (Throwable t) {
            fail(t);
            return null;
        }
    }

    private static void init() throws ReflectiveOperationException {
        initialized = true;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class<?> swr = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", true, cl);
        instanceNullable = swr.getMethod("instanceNullable");
        isSectionReady = swr.getMethod("isSectionReady", int.class, int.class, int.class);
        Class<?> holder = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder", true, cl);
        trackerGet = holder.getMethod("get", ClientLevel.class);
        Class<?> tracker = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker", true, cl);
        onChunkStatusRemoved = tracker.getMethod("onChunkStatusRemoved", int.class, int.class, int.class);
    }

    private static void fail(Throwable t) {
        failed = true;
        PipCamMod.LOGGER.warn("Sodium compatibility bridge disabled due to unexpected internals", t);
    }
}
