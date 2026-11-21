package dev.pipcam;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Owns the offscreen framebuffer and renders the pinned camera into it at a capped rate. */
public final class PipRenderer {
    private static final Matrix4f PROJECTION = new Matrix4f();

    private static TextureTarget fbo;
    private static long lastRenderNanos;
    private static long errorBackoffUntilNanos;
    private static boolean rendering;
    public static int framesRendered;

    private PipRenderer() {}

    public static int width() { return fbo == null ? 0 : fbo.width; }

    public static int height() { return fbo == null ? 0 : fbo.height; }

    public static int textureId() {
        return fbo == null ? -1 : fbo.getColorTextureId();
    }

    /** Called once per main frame; renders only when the target interval has elapsed. */
    public static void tick(Minecraft mc) {
        PipState s = PipState.INSTANCE;
        if (rendering || !s.pinned || mc.level == null || mc.player == null) return;
        boolean livePreview = s.editing || s.peeking;
        if (!livePreview && (!s.visible || s.paused || mc.isPaused())) return;
        if (!inCameraDimension(mc)) return;
        if (GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_ICONIFIED) != 0) return;

        long now = System.nanoTime();
        if (now < errorBackoffUntilNanos) return;
        long interval = livePreview ? 0 : 1_000_000_000L / Math.max(1, s.targetFps);
        if (fbo != null && now - lastRenderNanos < interval) return;
        lastRenderNanos = now;

        // Track player: aim the camera smoothly toward the player's eyes while staying pinned in place
        if (s.trackPlayer && !s.editing) {
            aimAtPlayer(mc, s);
        }

        // Placement / peek shows the picture large: render it sharper while open
        int w = livePreview ? Math.max(s.renderWidth, 1280) : s.renderWidth;
        int h = s.heightForWidth(w);
        ensureFbo(w, h);

        rendering = true;
        try {
            float aspect = (float) fbo.width / fbo.height;
            PROJECTION.setPerspective((float) Math.toRadians(s.fov), aspect, 0.05f,
                    s.renderDistanceChunks * 16f * 4f);
            PipLevelRenderer.render(fbo, PROJECTION, new Vec3(s.x, s.y, s.z), s.yaw, s.pitch,
                    s.renderDistanceChunks, mc.getTimer());
            forceOpaqueAndFilter();
            framesRendered++;
        } catch (Throwable t) {
            PipCamMod.LOGGER.error("PiP render failed; pausing", t);
            s.paused = true;
            // Back off for 2 seconds even while editing/peeking so logs never flood every frame
            errorBackoffUntilNanos = System.nanoTime() + 2_000_000_000L;
        } finally {
            rendering = false;
            // Restore the main target binding in case anything leaked
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void aimAtPlayer(Minecraft mc, PipState s) {
        Vec3 target = mc.player.getEyePosition(mc.getTimer().getGameTimeDeltaPartialTick(false));
        double dx = target.x - s.x;
        double dy = target.y - s.y;
        double dz = target.z - s.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz > 1e-4 || Math.abs(dy) > 1e-4) {
            s.yaw = Mth.wrapDegrees((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
            s.pitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(dy, horiz)), -90f, 90f);
        }
    }

    public static boolean inCameraDimension(Minecraft mc) {
        PipState s = PipState.INSTANCE;
        return mc.level != null && (s.dimension == null || s.dimension.equals(mc.level.dimension()));
    }

    /**
     * Makes every pixel opaque so nothing behind the PiP bleeds through, then generates mipmaps
     * for smooth trilinear downscaling / bilinear upscaling on the HUD.
     */
    private static void forceOpaqueAndFilter() {
        fbo.bindWrite(true);
        RenderSystem.colorMask(false, false, false, true);
        RenderSystem.clearColor(0f, 0f, 0f, 1f);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.colorMask(true, true, true, true);
        fbo.unbindWrite();

        int tex = fbo.getColorTextureId();
        if (tex > 0) {
            RenderSystem.bindTexture(tex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }
    }

    private static void ensureFbo(int w, int h) {
        if (fbo != null && fbo.width == w && fbo.height == h) return;
        free();
        fbo = new TextureTarget(w, h, true, Minecraft.ON_OSX);
        fbo.setClearColor(0f, 0f, 0f, 1f);
        fbo.setFilterMode(GL11.GL_LINEAR);
    }

    public static void free() {
        if (fbo != null) {
            fbo.destroyBuffers();
            fbo = null;
        }
    }
}
