package dev.pipcam;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Camera placement: the PiP is shown large and the camera is flown with your normal movement keys and the mouse.
 * Supports both right-click-drag to look and left-click to lock the cursor. Your character does not move while open.
 */
public class PipPlaceScreen extends Screen {
    private final Screen parent;
    private final PipActions.PoseSnapshot before;
    private long lastNanos;
    private float speed = 8f;
    private boolean locked, rightDragging, haveLast, cancelled;
    private double lastX, lastY;
    private double savedCursorX, savedCursorY;
    private IntSlider fovSlider;
    private Component toast;
    private long toastUntilNanos;

    public PipPlaceScreen(Screen parent, PipActions.PoseSnapshot before) {
        super(Component.translatable("pipcam.place.title"));
        this.parent = parent;
        this.before = before;
    }

    @Override
    protected void init() {
        PipState.INSTANCE.editing = true;
        lastNanos = System.nanoTime();
        int y = height - 26;
        PipState s = PipState.INSTANCE;

        int gap = 4;
        int totalW = Math.min(width - 12, 460);
        int avail = Math.max(120, totalW - gap * 5);
        int wJump = avail * 15 / 100;
        int wTrack = avail * 18 / 100;
        int wFov = avail * 23 / 100;
        int wSave = avail * 16 / 100;
        int wCancel = avail * 14 / 100;
        int wDone = avail - (wJump + wTrack + wFov + wSave + wCancel);
        int x = (width - totalW) / 2;

        addRenderableWidget(Button.builder(Component.translatable("pipcam.place.jump"), b -> PipActions.pinAtPlayer())
                .bounds(x, y, wJump, 20)
                .tooltip(Tooltip.create(Component.translatable("pipcam.tip.pin_here")))
                .build());
        x += wJump + gap;

        addRenderableWidget(CycleButton.onOffBuilder(s.trackPlayer)
                .withTooltip(v -> Tooltip.create(Component.translatable("pipcam.tip.track_player")))
                .create(x, y, wTrack, 20, Component.translatable("pipcam.opt.track_short"),
                        (b, v) -> s.trackPlayer = v));
        x += wTrack + gap;

        fovSlider = addRenderableWidget(new IntSlider(x, y, wFov, 20, 20, 120, Math.round(s.fov),
                v -> Component.translatable("pipcam.opt.fov_short", v).getString(), v -> s.fov = v));
        x += wFov + gap;

        addRenderableWidget(Button.builder(Component.translatable("pipcam.place.save_view"), b -> {
                    PipActions.saveNewView("");
                    toast = Component.translatable("pipcam.place.saved_toast", PipActions.viewName(PipState.INSTANCE.selectedView));
                    toastUntilNanos = System.nanoTime() + 2_000_000_000L;
                })
                .bounds(x, y, wSave, 20)
                .tooltip(Tooltip.create(Component.translatable("pipcam.tip.save_new")))
                .build());
        x += wSave + gap;

        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> cancelAndClose())
                .bounds(x, y, wCancel, 20).build());
        x += wCancel + gap;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x, y, wDone, 20).build());
    }

    /** Area of the large preview respecting the configured aspect ratio, leaving room for help text and buttons. */
    int[] previewRect() {
        int maxW = Math.max(16, width - 40);
        int maxH = Math.max(16, height - 80);
        int[] dims = PipHud.fitRect(maxW, maxH, PipState.INSTANCE.aspectValue());
        int y = 42 + Math.max(0, (maxH - dims[1]) / 2);
        return new int[]{(width - dims[0]) / 2, y, dims[0], dims[1]};
    }

    private boolean down(KeyMapping k) {
        InputConstants.Key key = k.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() < 0) return false;
        return InputConstants.isKeyDown(minecraft.getWindow().getWindow(), key.getValue());
    }

    private void fly() {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        var o = minecraft.options;
        float fwd = (down(o.keyUp) ? 1 : 0) - (down(o.keyDown) ? 1 : 0);
        float strafe = (down(o.keyRight) ? 1 : 0) - (down(o.keyLeft) ? 1 : 0);
        float up = (down(o.keyJump) ? 1 : 0) - (down(o.keyShift) ? 1 : 0);
        if (fwd == 0 && strafe == 0 && up == 0) return;
        PipState s = PipState.INSTANCE;
        float v = speed * (down(o.keySprint) ? 4 : 1) * dt;
        float yr = s.yaw * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yr), fz = Mth.cos(yr);
        double rx = -Mth.cos(yr), rz = -Mth.sin(yr);
        s.x += (fx * fwd + rx * strafe) * v;
        s.z += (fz * fwd + rz * strafe) * v;
        s.y += up * v;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!minecraft.isWindowActive()) {
            haveLast = false;
        } else if (locked || rightDragging) {
            long w = minecraft.getWindow().getWindow();
            if (GLFW.glfwGetInputMode(w, GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_DISABLED) {
                GLFW.glfwSetInputMode(w, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                haveLast = false;
            }
        }
        fly();
        super.render(g, mouseX, mouseY, partialTick);
        int[] r = previewRect();
        g.renderOutline(r[0] - 2, r[1] - 2, r[2] + 4, r[3] + 4, 0xA0000000);
        PipHud.drawTexture(g, r[0], r[1], r[2], r[3]);
        g.renderOutline(r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, 0xE0FFFFFF);

        g.drawCenteredString(font, Component.translatable("pipcam.place.hint_move"), width / 2, 6, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable(locked
                ? "pipcam.place.hint_locked" : "pipcam.place.hint_look"), width / 2, 17, 0xE0E0E0);

        PipState s = PipState.INSTANCE;
        g.drawCenteredString(font, Component.literal(String.format("%.1f, %.1f, %.1f   yaw %.0f°  pitch %.0f°   speed %.0f b/s",
                s.x, s.y, s.z, s.yaw, s.pitch, speed)), width / 2, 29, 0xA0A0A0);

        if (toast != null && System.nanoTime() < toastUntilNanos) {
            int tw = font.width(toast);
            int tx = width / 2 - tw / 2;
            int ty = r[1] + 8;
            g.fill(tx - 5, ty - 3, tx + tw + 5, ty + font.lineHeight + 3, 0xB0000000);
            g.drawString(font, toast, tx, ty, 0xFF88FF88, false);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xC0101010);
    }

    private void grabCursor() {
        long w = minecraft.getWindow().getWindow();
        double[] cx = new double[1], cy = new double[1];
        GLFW.glfwGetCursorPos(w, cx, cy);
        savedCursorX = cx[0];
        savedCursorY = cy[0];
        GLFW.glfwSetInputMode(w, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        haveLast = false;
    }

    private void releaseCursor(boolean center) {
        long w = minecraft.getWindow().getWindow();
        GLFW.glfwSetInputMode(w, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        if (center) {
            GLFW.glfwSetCursorPos(w, minecraft.getWindow().getScreenWidth() / 2.0, minecraft.getWindow().getScreenHeight() / 2.0);
        } else {
            GLFW.glfwSetCursorPos(w, savedCursorX, savedCursorY);
        }
        haveLast = false;
    }

    private void lock() {
        grabCursor();
        locked = true;
    }

    private void unlock() {
        if (!locked && !rightDragging) return;
        boolean wasLocked = locked;
        locked = false;
        rightDragging = false;
        releaseCursor(wasLocked);
    }

    private void applyLookDelta(double dx, double dy) {
        double sens = minecraft.options.sensitivity().get() * 0.6 + 0.2;
        double f = sens * sens * sens * 8.0 * 0.15 * minecraft.getWindow().getGuiScale();
        PipState s = PipState.INSTANCE;
        s.yaw = Mth.wrapDegrees(s.yaw + (float) (dx * f));
        s.pitch = Mth.clamp(s.pitch + (float) (dy * f), -90f, 90f);
    }

    private boolean insidePreview(double mx, double my) {
        int[] r = previewRect();
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (locked) {
            unlock();
            return true;
        }
        if (button == 1 && insidePreview(mouseX, mouseY)) {
            grabCursor();
            rightDragging = true;
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && insidePreview(mouseX, mouseY)) {
            lock();
            return true;
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!locked && !rightDragging) return;
        if (haveLast) {
            applyLookDelta(mouseX - lastX, mouseY - lastY);
        }
        lastX = mouseX;
        lastY = mouseY;
        haveLast = true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1 && rightDragging) {
            rightDragging = false;
            releaseCursor(false);
        }
        boolean r = super.mouseReleased(mouseX, mouseY, button);
        setFocused(null); // never leave a button focused: Space (= fly up) would press it
        return r;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (locked && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            unlock();
            return true;
        }
        var o = minecraft.options;
        for (KeyMapping k : new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint}) {
            if (k.matches(keyCode, scanCode)) return true; // flying keys never activate buttons
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (Screen.hasControlDown()) {
            PipState s = PipState.INSTANCE;
            s.fov = Mth.clamp(s.fov + (scrollY > 0 ? -5f : 5f), 20f, 120f);
            if (fovSlider != null) fovSlider.setIntValue(Math.round(s.fov));
            return true;
        }
        speed = Mth.clamp(speed * (scrollY > 0 ? 1.25f : 0.8f), 1f, 128f);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void cancelAndClose() {
        cancelled = true;
        if (before != null) before.restore(PipState.INSTANCE);
        PipState.INSTANCE.editing = false;
        minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        unlock();
        rightDragging = false;
        PipState.INSTANCE.editing = false;
        if (!cancelled) PipConfig.save();
    }

    @Override
    public void onClose() {
        PipState.INSTANCE.editing = false;
        PipConfig.save();
        minecraft.setScreen(parent);
    }
}
