package dev.pipcam;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Interactive HUD layout screen: drag the live PiP box anywhere on screen, scroll to resize,
 * snap to any corner, or toggle the frame and label badge.
 */
public class PipHudDragScreen extends Screen {
    private static final int MARGIN = 6;
    private static final int SNAP_PX = 10;

    private final Screen parent;
    private boolean dragging;
    private double grabOffsetX, grabOffsetY;

    public PipHudDragScreen(Screen parent) {
        super(Component.translatable("pipcam.hud_drag.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        PipState s = PipState.INSTANCE;
        int y = height - 28;
        int gap = 4;
        int totalW = Math.min(width - 12, 416);
        int avail = Math.max(180, totalW - gap * 6);
        int wCorner = Math.max(22, avail * 75 / 1000);
        int wToggle = Math.max(54, avail * 22 / 100);
        int wDone = Math.max(44, avail - (wCorner * 4 + wToggle * 2));
        int x = (width - (wCorner * 4 + wToggle * 2 + wDone + gap * 6)) / 2;

        addRenderableWidget(Button.builder(Component.translatable("pipcam.corner.short.tl"), b -> snap(0))
                .bounds(x, y, wCorner, 20).build());
        x += wCorner + gap;
        addRenderableWidget(Button.builder(Component.translatable("pipcam.corner.short.tr"), b -> snap(1))
                .bounds(x, y, wCorner, 20).build());
        x += wCorner + gap;
        addRenderableWidget(Button.builder(Component.translatable("pipcam.corner.short.bl"), b -> snap(2))
                .bounds(x, y, wCorner, 20).build());
        x += wCorner + gap;
        addRenderableWidget(Button.builder(Component.translatable("pipcam.corner.short.br"), b -> snap(3))
                .bounds(x, y, wCorner, 20).build());
        x += wCorner + gap;

        addRenderableWidget(CycleButton.onOffBuilder(s.showFrame)
                .create(x, y, wToggle, 20, Component.translatable("pipcam.opt.frame"), (b, v) -> s.showFrame = v));
        x += wToggle + gap;
        addRenderableWidget(CycleButton.onOffBuilder(s.showLabel)
                .create(x, y, wToggle, 20, Component.translatable("pipcam.opt.label"), (b, v) -> s.showLabel = v));
        x += wToggle + gap;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x, y, wDone, 20).build());
    }

    private void snap(int corner) {
        PipState s = PipState.INSTANCE;
        s.corner = corner;
        s.customPos = false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        PipState s = PipState.INSTANCE;
        int[] r = PipHud.hudRect(width, height);
        PipHud.drawWindow(g, minecraft, r[0], r[1], r[2], r[3], s.showFrame, s.showLabel);

        // Highlight outline while hovering or dragging so the interactive area is unmistakable
        boolean over = inside(mouseX, mouseY, r);
        if (dragging || over) {
            g.renderOutline(r[0] - 2, r[1] - 2, r[2] + 4, r[3] + 4, dragging ? 0xFF55FFFF : 0xCCFFFF55);
        }

        g.drawCenteredString(font, Component.translatable("pipcam.hud_drag.hint", s.sizePercent),
                width / 2, 10, 0xFFFFFF);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x44000000);
    }

    private static boolean inside(double mx, double my, int[] r) {
        return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            int[] r = PipHud.hudRect(width, height);
            if (inside(mouseX, mouseY, r)) {
                dragging = true;
                grabOffsetX = mouseX - r[0];
                grabOffsetY = mouseY - r[1];
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging && button == 0) {
            PipState s = PipState.INSTANCE;
            int[] r = PipHud.hudRect(width, height);
            int maxX = Math.max(1, width - r[2] - MARGIN * 2);
            int maxY = Math.max(1, height - r[3] - MARGIN * 2);
            double nx = mouseX - grabOffsetX - MARGIN;
            double ny = mouseY - grabOffsetY - MARGIN;
            // Gentle edge snapping so lining up with screen edges feels crisp
            if (Math.abs(nx) < SNAP_PX) nx = 0;
            else if (Math.abs(nx - maxX) < SNAP_PX) nx = maxX;
            if (Math.abs(ny) < SNAP_PX) ny = 0;
            else if (Math.abs(ny - maxY) < SNAP_PX) ny = maxY;
            s.customPos = true;
            s.posX = Mth.clamp((float) (nx / maxX), 0f, 1f);
            s.posY = Mth.clamp((float) (ny / maxY), 0f, 1f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        PipState s = PipState.INSTANCE;
        s.sizePercent = Mth.clamp(s.sizePercent + (scrollY > 0 ? 2 : -2), 10, 60);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        PipConfig.save();
        minecraft.setScreen(parent);
    }
}
