package dev.pipcam;

import com.mojang.blaze3d.systems.RenderSystem;
import net.neoforged.fml.loading.FMLLoader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Optional Iris compatibility bridge using cached reflection so Iris is neither a compile-time
 * nor runtime dependency.
 */
public final class IrisBridge {
    private static final boolean PRESENT = FMLLoader.getLoadingModList().getModFileById("iris") != null;

    private static boolean initialized, failed;
    private static Object settingsInstance;
    private static Class<?> settingsClass;
    private static Constructor<?> vanillaCtor;
    private static Method isPackInUseQuick, shouldDisableDirectionalShading, shouldUseSeparateAo, getVertexFormat;
    private static Object compactFormat;
    private static Object vanilla;

    private IrisBridge() {}

    public static boolean isPresent() {
        return PRESENT && !failed;
    }

    /**
     * Iris's VanillaRenderingPipeline constructor overwrites the GLOBAL WorldRenderingSettings (chunk vertex format,
     * block ids, AO...). Doing that while a shader pack is active makes Iris rebuild/reload chunks for the main view
     * (chunks flicker in and out). So snapshot every field and put it back after construction.
     */
    public static Object vanillaPipeline() {
        if (!PRESENT || failed) return null;
        if (vanilla == null) {
            try {
                if (!initialized) init();
                Field[] fields = settingsClass.getDeclaredFields();
                Object[] saved = new Object[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    if (Modifier.isStatic(fields[i].getModifiers())) continue;
                    fields[i].setAccessible(true);
                    saved[i] = fields[i].get(settingsInstance);
                }
                vanilla = vanillaCtor.newInstance();
                for (int i = 0; i < fields.length; i++) {
                    if (Modifier.isStatic(fields[i].getModifiers())) continue;
                    fields[i].set(settingsInstance, saved[i]);
                }
            } catch (Throwable t) {
                fail(t);
                return null;
            }
        }
        return vanilla;
    }

    public static boolean isPackInUse() {
        if (!PRESENT || failed) return false;
        try {
            if (!initialized) init();
            return (boolean) isPackInUseQuick.invoke(null);
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    /** True when Iris built the chunk meshes without vanilla face shading (shader pack active). */
    public static boolean meshesUnshaded() {
        if (!PRESENT || failed) return false;
        try {
            if (!initialized) init();
            return (boolean) isPackInUseQuick.invoke(null)
                    || (boolean) shouldDisableDirectionalShading.invoke(settingsInstance);
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    /** Iris is building chunk meshes in its extended (shader-pack) vertex format. */
    public static boolean extendedChunkFormat() {
        if (!PRESENT || failed) return false;
        try {
            if (!initialized) init();
            Object f = getVertexFormat.invoke(settingsInstance);
            return f != null && f != compactFormat;
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    public static boolean usesSeparateAo() {
        if (!PRESENT || failed) return false;
        try {
            if (!initialized) init();
            return (boolean) shouldUseSeparateAo.invoke(settingsInstance);
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    public static boolean forceVanilla() {
        return PRESENT && !failed && PipLevelRenderer.rendering && RenderSystem.isOnRenderThread();
    }

    private static void init() throws ReflectiveOperationException {
        initialized = true;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class<?> iris = Class.forName("net.irisshaders.iris.Iris", true, cl);
        isPackInUseQuick = iris.getMethod("isPackInUseQuick");
        settingsClass = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings", true, cl);
        settingsInstance = settingsClass.getField("INSTANCE").get(null);
        shouldDisableDirectionalShading = settingsClass.getMethod("shouldDisableDirectionalShading");
        shouldUseSeparateAo = settingsClass.getMethod("shouldUseSeparateAo");
        getVertexFormat = settingsClass.getMethod("getVertexFormat");
        Class<?> vanillaCls = Class.forName("net.irisshaders.iris.pipeline.VanillaRenderingPipeline", true, cl);
        vanillaCtor = vanillaCls.getDeclaredConstructor();
        vanillaCtor.setAccessible(true);
        try {
            Class<?> cmf = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats", true, cl);
            compactFormat = cmf.getField("COMPACT").get(null);
        } catch (ReflectiveOperationException ignored) {
            compactFormat = null;
        }
    }

    private static void fail(Throwable t) {
        failed = true;
        PipCamMod.LOGGER.warn("Iris compatibility bridge disabled due to unexpected internals", t);
    }
}
