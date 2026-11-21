package dev.pipcam;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** Shared user actions used by keys and screens. */
public final class PipActions {
    private PipActions() {}

    /** Snapshot of the camera pose for Cancel/Undo in {@link PipPlaceScreen}. */
    static final class PoseSnapshot {
        final boolean pinned, paused, visible, trackPlayer;
        final ResourceKey<Level> dimension;
        final double x, y, z;
        final float yaw, pitch, fov;
        final int selectedView;

        PoseSnapshot(PipState s) {
            this.pinned = s.pinned;
            this.paused = s.paused;
            this.visible = s.visible;
            this.trackPlayer = s.trackPlayer;
            this.dimension = s.dimension;
            this.x = s.x; this.y = s.y; this.z = s.z;
            this.yaw = s.yaw; this.pitch = s.pitch; this.fov = s.fov;
            this.selectedView = s.selectedView;
        }

        void restore(PipState s) {
            s.pinned = pinned;
            s.paused = paused;
            s.visible = visible;
            s.trackPlayer = trackPlayer;
            s.dimension = dimension;
            s.x = x; s.y = y; s.z = z;
            s.yaw = yaw; s.pitch = pitch; s.fov = fov;
            s.selectedView = selectedView;
        }
    }

    /** Pins the camera exactly where the player is looking from right now. */
    public static void pinAtPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 pos = cam.getPosition();
        PipState s = PipState.INSTANCE;
        s.dimension = mc.level.dimension();
        s.x = pos.x; s.y = pos.y; s.z = pos.z;
        s.yaw = cam.getYRot();
        s.pitch = cam.getXRot();
        s.pinned = true;
        s.paused = false;
        s.visible = true;
        PipConfig.save();
    }

    /** Opens the fly-to-place screen; pins at the player first if nothing is pinned in this dimension. */
    public static void openPlacement(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        PipState s = PipState.INSTANCE;
        PoseSnapshot before = new PoseSnapshot(s);
        if (!s.pinned || (s.dimension != null && !s.dimension.equals(mc.level.dimension()))) {
            pinAtPlayer();
        }
        mc.setScreen(new PipPlaceScreen(parent, before));
    }

    // ---- saved views ----

    public static int viewCount() {
        return PipConfig.views().size();
    }

    public static String viewName(int i) {
        var v = PipConfig.views();
        return i >= 0 && i < v.size() ? v.get(i).name : "";
    }

    public static String viewDimension(int i) {
        var v = PipConfig.views();
        return i >= 0 && i < v.size() ? v.get(i).dimension : null;
    }

    /** Saves the current camera as a new view and selects it. */
    public static void saveNewView(String name) {
        if (!PipState.INSTANCE.pinned) return;
        var v = PipConfig.views();
        v.add(PipConfig.fromState(uniqueName(name, -1)));
        PipState.INSTANCE.selectedView = v.size() - 1;
        PipConfig.save();
    }

    /** Renames view i without altering its saved position or orientation. */
    public static void renameView(int i, String name) {
        var v = PipConfig.views();
        if (i < 0 || i >= v.size()) return;
        v.get(i).name = uniqueName(name, i);
        PipConfig.save();
    }

    /** Replaces view i with the current camera (and the given name). */
    public static void overwriteView(int i, String name) {
        var v = PipConfig.views();
        if (!PipState.INSTANCE.pinned || i < 0 || i >= v.size()) return;
        v.set(i, PipConfig.fromState(uniqueName(name, i)));
        PipConfig.save();
    }

    public static void deleteView(int i) {
        var v = PipConfig.views();
        if (i < 0 || i >= v.size()) return;
        v.remove(i);
        PipState s = PipState.INSTANCE;
        s.selectedView = v.isEmpty() ? -1 : Math.min(i, v.size() - 1);
        PipConfig.save();
    }

    /** Shows the next (+1) or previous (-1) saved view. */
    public static boolean cycleView(int dir) {
        var v = PipConfig.views();
        if (v.isEmpty()) return false;
        PipState s = PipState.INSTANCE;
        int i = s.selectedView < 0 ? (dir > 0 ? 0 : v.size() - 1) : Math.floorMod(s.selectedView + dir, v.size());
        s.selectedView = i;
        PipConfig.applyToState(v.get(i));
        s.visible = true;
        PipConfig.save();
        return true;
    }

    private static String uniqueName(String wanted, int except) {
        if (wanted == null || wanted.isBlank()) {
            for (int n = PipConfig.views().size() + 1; ; n++) {
                if (!taken("View " + n, except)) return "View " + n;
            }
        }
        String c = wanted.trim();
        for (int n = 2; taken(c, except); n++) c = wanted.trim() + " (" + n + ")";
        return c;
    }

    private static boolean taken(String name, int except) {
        var v = PipConfig.views();
        for (int j = 0; j < v.size(); j++) if (j != except && Objects.equals(v.get(j).name, name)) return true;
        return false;
    }

    public static void unpin() {
        PipState.INSTANCE.pinned = false;
        PipState.INSTANCE.selectedView = -1;
        PipConfig.save();
    }
}
