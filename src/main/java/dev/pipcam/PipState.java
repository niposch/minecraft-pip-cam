package dev.pipcam;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Mutable runtime state of the single pinned camera plus user-facing settings (persisted by {@link PipConfig}). */
public final class PipState {
    public static final PipState INSTANCE = new PipState();

    public static final int LIGHT_AUTO = 0, LIGHT_DAY = 1, LIGHT_NIGHT = 2;
    public static final int ASPECT_16_9 = 0, ASPECT_4_3 = 1, ASPECT_1_1 = 2;

    // --- pinned pose (persisted per world/server) ---
    public boolean pinned;
    public ResourceKey<Level> dimension;
    public double x, y, z;
    public float yaw, pitch;
    public float fov = 70f;
    /** When true, the pinned camera automatically turns to keep the player centered. */
    public boolean trackPlayer;

    // --- display settings ---
    public boolean visible = true;
    public boolean paused;
    /** Internal render width; height follows {@link #aspectRatio}. */
    public int renderWidth = 480;
    public int renderHeight = 270;
    public int aspectRatio = ASPECT_16_9;
    public int targetFps = 15;
    public int renderDistanceChunks = 8;
    /** PiP lighting: follow the world time, always day, or always night. */
    public int lighting = LIGHT_AUTO;
    /** Keep chunks around the pinned camera after the server unloads them (stale but visible). */
    public boolean retainChunks = true;
    public int retainRadiusChunks = 8;
    /** Let Distant Horizons draw its LODs into the PiP too. */
    public boolean distantHorizons = true;
    /** Render rain/snow inside the PiP (off by default for a clearer picture). */
    public boolean renderWeather = false;
    /** Render clouds inside the PiP. */
    public boolean renderClouds = true;
    /** 0 = top-left, 1 = top-right, 2 = bottom-left, 3 = bottom-right. */
    public int corner = 1;
    /** When true, HUD placement uses normalized {@link #posX}/{@link #posY} instead of {@link #corner}. */
    public boolean customPos = false;
    /** Normalized top-left position (0..1 of available travel range) when {@link #customPos} is true. */
    public float posX = 1.0f;
    public float posY = 0.0f;
    /** PiP width as a percentage of the screen width. */
    public int sizePercent = 25;
    /** Draw the HUD border frame around the PiP. */
    public boolean showFrame = true;
    /** Draw the subtle status/view-name badge inside the PiP. */
    public boolean showLabel = true;

    // --- transient ---
    /** The placement screen is open: render every frame, larger. */
    public transient boolean editing;
    /** Hold-to-peek key is held: show the PiP large and centered. */
    public transient boolean peeking;
    /** Index of the saved view currently shown, or -1. */
    public transient int selectedView = -1;

    private PipState() {}

    public void setRenderWidth(int w) {
        renderWidth = w;
        renderHeight = heightForWidth(w);
    }

    public void setAspectRatio(int mode) {
        aspectRatio = Math.max(0, Math.min(2, mode));
        renderHeight = heightForWidth(renderWidth);
    }

    public int heightForWidth(int w) {
        return switch (aspectRatio) {
            case ASPECT_4_3 -> Math.max(9, w * 3 / 4);
            case ASPECT_1_1 -> Math.max(9, w);
            default -> Math.max(9, w * 9 / 16);
        };
    }

    public float aspectValue() {
        return switch (aspectRatio) {
            case ASPECT_4_3 -> 4f / 3f;
            case ASPECT_1_1 -> 1f;
            default -> 16f / 9f;
        };
    }
}
