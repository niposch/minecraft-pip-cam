package dev.pipcam;

import net.neoforged.fml.loading.FMLLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Iris records the camera/projection matrices, fog and tick delta of every level render in CapturedRenderingState.
 * Shader packs use these (and "previous frame" values derived from them) for TAA, reflections etc. The PiP render
 * would leave the PiP camera's values there; restore the main view's values afterwards.
 */
final class IrisSnapshot {
    private static final boolean IRIS = FMLLoader.getLoadingModList().getModFileById("iris") != null;
    private static Field[] fields;
    private static Object target;
    private static boolean failed;

    private IrisSnapshot() {}

    static Object capture() {
        if (!IRIS || failed) return null;
        try {
            if (fields == null) {
                Class<?> c = Class.forName("net.irisshaders.iris.uniforms.CapturedRenderingState", true,
                        Thread.currentThread().getContextClassLoader());
                Field inst = c.getDeclaredField("INSTANCE");
                target = inst.get(null);
                java.util.List<Field> fs = new java.util.ArrayList<>();
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    fs.add(f);
                }
                fields = fs.toArray(new Field[0]);
            }
            Object[] v = new Object[fields.length];
            for (int i = 0; i < fields.length; i++) v[i] = fields[i].get(target);
            return v;
        } catch (Throwable t) {
            failed = true;
            PipCamMod.LOGGER.warn("Could not snapshot Iris rendering state", t);
            return null;
        }
    }

    static void restore(Object snapshot) {
        if (!(snapshot instanceof Object[] v)) return;
        try {
            for (int i = 0; i < fields.length; i++) fields[i].set(target, v[i]);
        } catch (Throwable t) {
            failed = true;
            PipCamMod.LOGGER.warn("Could not restore Iris rendering state", t);
        }
    }
}
