package dev.pipcam;

import net.neoforged.fml.loading.FMLLoader;

import java.lang.reflect.Method;

/**
 * Optional Veil compatibility bridge: preserves Veil's shared {@code CameraMatrices} across the PiP render
 * so Veil/Sable post-processing and rendering in the main view are unaffected. Uses reflection so Veil is
 * neither a compile-time nor runtime dependency.
 */
final class VeilBridge {
    private static final boolean PRESENT = FMLLoader.getLoadingModList().getModFileById("veil") != null;
    private static boolean initialized, failed;
    private static Method rendererMethod, getCameraMatricesMethod, backupMethod, restoreMethod;
    private static Object backupMatrices;

    private VeilBridge() {}

    static Object backup() {
        if (!PRESENT || failed) return null;
        try {
            if (!initialized) init();
            Object renderer = rendererMethod.invoke(null);
            if (renderer == null) return null;
            Object matrices = getCameraMatricesMethod.invoke(renderer);
            if (matrices == null) return null;
            backupMethod.invoke(matrices, backupMatrices);
            return matrices;
        } catch (Throwable t) {
            failed = true;
            PipCamMod.LOGGER.warn("Could not back up Veil camera matrices for the PiP", t);
            return null;
        }
    }

    static void restore(Object matrices) {
        if (matrices == null || failed) return;
        try {
            restoreMethod.invoke(matrices, backupMatrices);
        } catch (Throwable t) {
            failed = true;
            PipCamMod.LOGGER.warn("Could not restore Veil camera matrices after the PiP", t);
        }
    }

    private static void init() throws ReflectiveOperationException {
        initialized = true;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        Class<?> vrs = Class.forName("foundry.veil.api.client.render.VeilRenderSystem", true, cl);
        Class<?> vr = Class.forName("foundry.veil.api.client.render.VeilRenderer", true, cl);
        Class<?> cm = Class.forName("foundry.veil.api.client.render.CameraMatrices", true, cl);
        rendererMethod = vrs.getMethod("renderer");
        getCameraMatricesMethod = vr.getMethod("getCameraMatrices");
        backupMethod = cm.getMethod("backup", cm);
        restoreMethod = cm.getMethod("restore", cm);
        backupMatrices = cm.getDeclaredConstructor().newInstance();
    }
}
