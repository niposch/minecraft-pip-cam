package dev.pipcam;

import net.neoforged.fml.loading.FMLLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Distant Horizons keeps screen-sized framebuffers and temporal (TAA) history in singleton renderers. Rendering the
 * PiP through them made DH resize/recreate those buffers twice per PiP frame and poisoned the TAA history, which shows
 * up in the main view as LODs flickering and jittering while moving. This gives the PiP its own private copy of that
 * state: every instance field of those renderers is swapped to a PiP set while the PiP renders and swapped back after.
 * The PiP set starts zeroed, which is exactly DH's "not initialised yet" state, so DH lazily creates separate buffers.
 */
final class DhStateSwap {
    private static final String[] STATIC_SINGLETONS = {
            "com.seibel.distanthorizons.common.render.openGl.postProcessing.antialiasing.GlDhTaaRenderer_neoforge",
            "com.seibel.distanthorizons.common.render.openGl.postProcessing.ssao.GlDhSSAORenderer_neoforge",
            "com.seibel.distanthorizons.common.render.openGl.postProcessing.fog.GlDhFogRenderer_neoforge",
            "com.seibel.distanthorizons.common.render.openGl.postProcessing.fade.GlDhFarFadeRenderer_neoforge",
            "com.seibel.distanthorizons.common.render.openGl.postProcessing.fade.GlVanillaFadeRenderer_neoforge",
    };

    private static final class Slot {
        final Object target;
        final Field[] fields;
        final Object[] other; // values of the inactive view

        Slot(Object target, Field[] fields) {
            this.target = target;
            this.fields = fields;
            this.other = new Object[fields.length];
            for (int i = 0; i < fields.length; i++) other[i] = zero(fields[i].getType());
        }

        void swap() throws IllegalAccessException {
            for (int i = 0; i < fields.length; i++) {
                Object cur = fields[i].get(target);
                fields[i].set(target, other[i]);
                other[i] = cur;
            }
        }
    }

    private static final boolean PRESENT = FMLLoader.getLoadingModList().getModFileById("distanthorizons") != null;
    private static Field metaRendererField;
    private static Object lodRendererInstance;
    private static Object trackedMetaRenderer;
    private static List<Slot> slots;
    private static boolean failed, swapped;

    private DhStateSwap() {}

    static boolean isAvailable() {
        return PRESENT && !failed;
    }

    static boolean enterPip() {
        if (!PRESENT || failed || !PipState.INSTANCE.distantHorizons) return false;
        int done = 0;
        try {
            if (!ensureSlots()) return false;
            for (; done < slots.size(); done++) slots.get(done).swap();
            swapped = true;
            return true;
        } catch (Throwable t) {
            // Roll back any slots already swapped so DH's main-view state is never left half-zeroed.
            for (int i = done - 1; i >= 0; i--) {
                try { slots.get(i).swap(); } catch (Throwable ignored) {}
            }
            fail(t);
            return false;
        }
    }

    static void exitPip() {
        if (!swapped) return;
        swapped = false;
        try {
            for (int i = slots.size() - 1; i >= 0; i--) slots.get(i).swap();
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static void fail(Throwable t) {
        failed = true;
        PipCamMod.LOGGER.error("Could not isolate Distant Horizons state for the PiP; DH disabled for this session", t);
    }

    private static boolean ensureSlots() throws ReflectiveOperationException {
        if (metaRendererField == null) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> lr = Class.forName("com.seibel.distanthorizons.core.render.renderer.LodRenderer", true, cl);
            metaRendererField = lr.getDeclaredField("metaRenderer");
            metaRendererField.setAccessible(true);
            lodRendererInstance = lr.getField("INSTANCE").get(null);
        }
        Object metaRenderer = metaRendererField.get(lodRendererInstance);
        // A saved camera can render before DH binds its main framebuffer owner. Retry after the main view runs.
        if (metaRenderer == null) return false;
        if (slots != null && trackedMetaRenderer == metaRenderer) return true;

        if (slots == null) {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            List<Slot> out = new ArrayList<>();
            for (String name : STATIC_SINGLETONS) {
                Class<?> c = Class.forName(name, true, cl);
                Field inst = c.getDeclaredField("INSTANCE");
                inst.setAccessible(true);
                Object o = inst.get(null);
                if (o != null) out.add(slot(o));
            }
            out.add(slot(metaRenderer));
            slots = out;
            PipCamMod.LOGGER.info("Isolating {} Distant Horizons renderers for the PiP", out.size());
        } else {
            // DH recreated its metaRenderer (e.g. renderer reload): replace only the metaRenderer slot.
            slots.set(slots.size() - 1, slot(metaRenderer));
        }
        trackedMetaRenderer = metaRenderer;
        return true;
    }

    private static Slot slot(Object o) {
        List<Field> fs = new ArrayList<>();
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                fs.add(f);
            }
        }
        return new Slot(o, fs.toArray(new Field[0]));
    }

    private static Object zero(Class<?> t) {
        if (!t.isPrimitive()) return null;
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        if (t == short.class) return (short) 0;
        if (t == byte.class) return (byte) 0;
        return (char) 0;
    }
}
