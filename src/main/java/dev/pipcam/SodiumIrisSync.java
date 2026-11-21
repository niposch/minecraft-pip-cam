package dev.pipcam;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Sodium keeps ONE render list object per region, shared by every traversal (main view, Iris shadow pass, PiP).
 * The PiP traversal therefore overwrites lists that the main view / Iris shadow pass would otherwise reuse on the
 * next frames, which shows up as chunks randomly vanishing. After every PiP render we force both to rebuild.
 */
final class SodiumIrisSync {
    private static boolean failed;
    private static Field rsmField, shadowListFlag, sunAngle;
    private static Method markGraphDirty;

    private SodiumIrisSync() {}

    static void afterPipRender() {
        if (failed || !SodiumBridge.isPresent()) return;
        try {
            Object swr = SodiumBridge.worldRendererNullable();
            if (swr == null) return;
            if (rsmField == null) {
                rsmField = swr.getClass().getDeclaredField("renderSectionManager");
                rsmField.setAccessible(true);
                sunAngle = find(swr.getClass(), "lastSunAngle");
            }
            Object rsm = rsmField.get(swr);
            if (rsm == null) return;
            if (markGraphDirty == null) {
                markGraphDirty = rsm.getClass().getMethod("markGraphDirty");
            }
            markGraphDirty.invoke(rsm);
            if (shadowListFlag == null) shadowListFlag = find(rsm.getClass(), "shadowNeedsRenderListUpdate");
            if (shadowListFlag != null) shadowListFlag.setBoolean(rsm, true);
            if (sunAngle != null) sunAngle.setFloat(swr, Float.NaN); // forces Iris to re-traverse for shadows
        } catch (Throwable t) {
            failed = true;
            PipCamMod.LOGGER.warn("Could not resync Sodium/Iris after PiP render; main view may flicker", t);
        }
    }

    private static Field find(Class<?> c, String suffix) {
        for (Field f : c.getDeclaredFields()) {
            if (f.getName().equals(suffix) || f.getName().endsWith("$" + suffix) || f.getName().endsWith(suffix)) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }
}
