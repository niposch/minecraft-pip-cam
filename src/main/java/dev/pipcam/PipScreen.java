package dev.pipcam;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

/** Main PiP menu: two clean tabs so every control fits comfortably at any window size or GUI scale. */
public class PipScreen extends Screen {
    private static final int[] RESOLUTIONS = {320, 480, 640, 960, 1280, 1920};
    private static int activeTab = 0; // 0 = Camera, 1 = Settings

    private final Screen parent;
    private EditBox nameBox;
    private int viewsLabelY = -1;

    public PipScreen(Screen parent) {
        super(Component.translatable("pipcam.menu.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        PipState s = PipState.INSTANCE;
        boolean inWorld = minecraft != null && minecraft.level != null;
        int colW = 152, gap = 8, h = 20;
        int full = colW * 2 + gap;
        int left = width / 2 - colW - gap / 2, right = width / 2 + gap / 2;
        int rowH = Math.max(21, Math.min(24, (height - 84) / 7));
        int y = 34;

        // --- tab bar ---
        Button tabCam = addRenderableWidget(Button.builder(Component.translatable("pipcam.tab.camera"), b -> {
            activeTab = 0;
            rebuildWidgets();
        }).bounds(left, y, colW, h).build());
        Button tabSet = addRenderableWidget(Button.builder(Component.translatable("pipcam.tab.settings"), b -> {
            activeTab = 1;
            rebuildWidgets();
        }).bounds(right, y, colW, h).build());
        tabCam.active = activeTab != 0;
        tabSet.active = activeTab != 1;
        y += rowH + 4;

        nameBox = null;
        viewsLabelY = -1;

        if (activeTab == 0) {
            y = buildCameraTab(s, inWorld, left, right, colW, full, gap, h, rowH, y);
        } else {
            y = buildSettingsTab(s, left, right, colW, h, rowH, y);
        }

        int doneY = Math.min(height - h - 6, Math.max(y + 4, height - h - 10));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, doneY, 200, h).build());
    }

    private int buildCameraTab(PipState s, boolean inWorld, int left, int right, int colW, int full, int gap, int h, int rowH, int y) {
        // --- camera actions ---
        int bw3 = (full - 2 * gap) / 3;
        Button place = addRenderableWidget(Button.builder(
                        Component.translatable(s.pinned ? "pipcam.btn.move" : "pipcam.btn.place"),
                        b -> PipActions.openPlacement(this))
                .bounds(left, y, bw3, h)
                .tooltip(Tooltip.create(Component.translatable("pipcam.tip.place")))
                .build());
        Button pinHere = addRenderableWidget(Button.builder(Component.translatable("pipcam.btn.pin_here"), b -> {
                    PipActions.pinAtPlayer();
                    rebuildWidgets();
                }).bounds(left + bw3 + gap, y, bw3, h)
                .tooltip(Tooltip.create(Component.translatable("pipcam.tip.pin_here")))
                .build());
        Button remove = addRenderableWidget(Button.builder(Component.translatable("pipcam.btn.remove"), b -> {
            PipActions.unpin();
            rebuildWidgets();
        }).bounds(left + 2 * (bw3 + gap), y, full - 2 * (bw3 + gap), h).build());
        place.active = inWorld;
        pinHere.active = inWorld;
        remove.active = inWorld && s.pinned;
        y += rowH + 4;

        // --- saved views ---
        int count = inWorld ? PipActions.viewCount() : 0;
        int sel = (inWorld && s.selectedView >= 0 && s.selectedView < count) ? s.selectedView : -1;
        viewsLabelY = y;
        y += 10;
        int arrow = 20;
        Button prev = addRenderableWidget(Button.builder(Component.literal("◀"), b -> {
            PipActions.cycleView(-1);
            rebuildWidgets();
        }).bounds(left, y, arrow, h).tooltip(Tooltip.create(Component.translatable("pipcam.tip.prev_view"))).build());
        nameBox = new EditBox(font, left + arrow + 4, y, full - 2 * arrow - 8, h,
                Component.translatable("pipcam.views.name"));
        nameBox.setMaxLength(40);
        nameBox.setHint(Component.translatable("pipcam.views.name_hint").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setValue(sel >= 0 ? PipActions.viewName(sel) : "");
        nameBox.setEditable(inWorld);
        addRenderableWidget(nameBox);
        Button next = addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
            PipActions.cycleView(1);
            rebuildWidgets();
        }).bounds(left + full - arrow, y, arrow, h).tooltip(Tooltip.create(Component.translatable("pipcam.tip.next_view"))).build());
        prev.active = next.active = inWorld && count > 0;
        y += rowH;

        int vGap = 4;
        int bw4 = (full - 3 * vGap) / 4;
        Button saveNew = addRenderableWidget(Button.builder(Component.translatable("pipcam.btn.save_new"), b -> {
            PipActions.saveNewView(nameBox.getValue());
            rebuildWidgets();
        }).bounds(left, y, bw4, h).tooltip(Tooltip.create(Component.translatable("pipcam.tip.save_new"))).build());
        Button rename = addRenderableWidget(Button.builder(Component.translatable("pipcam.btn.rename"), b -> {
            PipActions.renameView(s.selectedView, nameBox.getValue());
            rebuildWidgets();
        }).bounds(left + bw4 + vGap, y, bw4, h).tooltip(Tooltip.create(Component.translatable("pipcam.tip.rename"))).build());
        Button update = addRenderableWidget(Button.builder(Component.translatable("pipcam.btn.update"), b -> {
            PipActions.overwriteView(s.selectedView, nameBox.getValue());
            rebuildWidgets();
        }).bounds(left + 2 * (bw4 + vGap), y, bw4, h).tooltip(Tooltip.create(Component.translatable("pipcam.tip.update"))).build());
        Button delete = addRenderableWidget(Button.builder(Component.translatable("pipcam.btn.delete"), b -> {
            PipActions.deleteView(s.selectedView);
            rebuildWidgets();
        }).bounds(left + 3 * (bw4 + vGap), y, full - 3 * (bw4 + vGap), h)
                .tooltip(Tooltip.create(Component.translatable("pipcam.tip.delete"))).build());
        saveNew.active = inWorld && s.pinned;
        rename.active = inWorld && sel >= 0;
        update.active = inWorld && s.pinned && sel >= 0;
        delete.active = inWorld && sel >= 0;
        y += rowH + 4;

        // --- display & quick controls ---
        addRenderableWidget(CycleButton.onOffBuilder(s.visible)
                .create(left, y, colW, h, Component.translatable("pipcam.opt.show"), (b, v) -> s.visible = v));
        addRenderableWidget(CycleButton.onOffBuilder(s.paused)
                .withTooltip(v -> Tooltip.create(Component.translatable("pipcam.tip.pause")))
                .create(right, y, colW, h, Component.translatable("pipcam.opt.pause"), (b, v) -> s.paused = v));
        y += rowH;

        addRenderableWidget(CycleButton.onOffBuilder(s.trackPlayer)
                .withTooltip(v -> Tooltip.create(Component.translatable("pipcam.tip.track_player")))
                .create(left, y, colW, h, Component.translatable("pipcam.opt.track"), (b, v) -> s.trackPlayer = v));
        Button moveHud = addRenderableWidget(Button.builder(Component.translatable("pipcam.btn.move_hud"),
                        b -> minecraft.setScreen(new PipHudDragScreen(this)))
                .bounds(right, y, colW, h)
                .tooltip(Tooltip.create(Component.translatable("pipcam.tip.move_hud")))
                .build());
        moveHud.active = inWorld;
        y += rowH;

        return y;
    }

    private int buildSettingsTab(PipState s, int left, int right, int colW, int h, int rowH, int y) {
        addRenderableWidget(CycleButton.<Integer>builder(this::aspectLabel)
                .withValues(PipState.ASPECT_16_9, PipState.ASPECT_4_3, PipState.ASPECT_1_1)
                .withInitialValue(s.aspectRatio)
                .create(left, y, colW, h, Component.translatable("pipcam.opt.aspect"), (b, v) -> {
                    s.setAspectRatio(v);
                    rebuildWidgets();
                }));
        addRenderableWidget(new IntSlider(right, y, colW, h, 10, 60, s.sizePercent,
                v -> Component.translatable("pipcam.opt.size", v).getString(), v -> s.sizePercent = v));
        y += rowH;

        addRenderableWidget(new IntSlider(left, y, colW, h, 1, 60, s.targetFps,
                v -> Component.translatable("pipcam.opt.fps", v).getString(), v -> s.targetFps = v));
        int res = closestResolution(s.renderWidth);
        addRenderableWidget(CycleButton.<Integer>builder(w -> Component.literal(w + " × " + s.heightForWidth(w)))
                .withValues(320, 480, 640, 960, 1280, 1920).withInitialValue(res)
                .withTooltip(v -> Tooltip.create(Component.translatable("pipcam.tip.resolution")))
                .create(right, y, colW, h, Component.translatable("pipcam.opt.resolution"),
                        (b, v) -> s.setRenderWidth(v)));
        y += rowH;

        addRenderableWidget(new IntSlider(left, y, colW, h, 2, 32, s.renderDistanceChunks,
                v -> Component.translatable("pipcam.opt.view_dist", v).getString(), v -> s.renderDistanceChunks = v));
        addRenderableWidget(new IntSlider(right, y, colW, h, 20, 120, Math.round(s.fov),
                v -> Component.translatable("pipcam.opt.fov", v).getString(), v -> s.fov = v));
        y += rowH;

        addRenderableWidget(CycleButton.<Integer>builder(this::lightingLabel)
                .withValues(PipState.LIGHT_AUTO, PipState.LIGHT_DAY, PipState.LIGHT_NIGHT)
                .withInitialValue(s.lighting)
                .withTooltip(v -> Tooltip.create(Component.translatable("pipcam.tip.lighting")))
                .create(left, y, colW, h, Component.translatable("pipcam.opt.lighting"), (b, v) -> s.lighting = v));
        addRenderableWidget(CycleButton.onOffBuilder(s.renderWeather)
                .withTooltip(v -> Tooltip.create(Component.translatable("pipcam.tip.weather")))
                .create(right, y, colW, h, Component.translatable("pipcam.opt.weather"), (b, v) -> s.renderWeather = v));
        y += rowH;

        addRenderableWidget(CycleButton.onOffBuilder(s.renderClouds)
                .create(left, y, colW, h, Component.translatable("pipcam.opt.clouds"), (b, v) -> s.renderClouds = v));
        boolean dh = ModList.get().isLoaded("distanthorizons");
        CycleButton<Boolean> dhButton = addRenderableWidget(CycleButton.onOffBuilder(dh && s.distantHorizons)
                .withTooltip(v -> Tooltip.create(Component.translatable(dh
                        ? "pipcam.tip.dh_on" : "pipcam.tip.dh_missing")))
                .create(right, y, colW, h, Component.translatable("pipcam.opt.dh"), (b, v) -> s.distantHorizons = v));
        dhButton.active = dh;
        y += rowH;

        addRenderableWidget(CycleButton.onOffBuilder(s.retainChunks)
                .withTooltip(v -> Tooltip.create(Component.translatable("pipcam.tip.retain")))
                .create(left, y, colW, h, Component.translatable("pipcam.opt.retain"), (b, v) -> s.retainChunks = v));
        addRenderableWidget(new IntSlider(right, y, colW, h, 1, 16, s.retainRadiusChunks,
                v -> Component.translatable("pipcam.opt.retain_radius", v).getString(), v -> s.retainRadiusChunks = v));
        y += rowH;

        return y;
    }

    private Component aspectLabel(int mode) {
        return switch (mode) {
            case PipState.ASPECT_4_3 -> Component.literal("4:3");
            case PipState.ASPECT_1_1 -> Component.literal("1:1");
            default -> Component.literal("16:9");
        };
    }

    private Component lightingLabel(int mode) {
        return switch (mode) {
            case PipState.LIGHT_DAY -> Component.translatable("pipcam.light.day");
            case PipState.LIGHT_NIGHT -> Component.translatable("pipcam.light.night");
            default -> Component.translatable("pipcam.light.auto");
        };
    }

    private static int closestResolution(int w) {
        int best = RESOLUTIONS[0];
        for (int r : RESOLUTIONS) {
            if (Math.abs(r - w) < Math.abs(best - w)) best = r;
        }
        return best;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft != null && minecraft.level != null) {
            // Subtle dim so the live corner PiP stays visible while tweaking settings
            g.fill(0, 0, width, height, 0x70000000);
        } else {
            super.renderBackground(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        PipState s = PipState.INSTANCE;
        boolean inWorld = minecraft != null && minecraft.level != null;
        Component status;
        if (!inWorld) {
            status = Component.translatable("pipcam.status.no_world");
        } else if (s.pinned) {
            int retained = ChunkRetention.retainedCount();
            status = retained > 0
                    ? Component.translatable("pipcam.status.pinned_retained",
                            Math.round(s.x), Math.round(s.y), Math.round(s.z), retained)
                    : Component.translatable("pipcam.status.pinned",
                            Math.round(s.x), Math.round(s.y), Math.round(s.z));
        } else {
            status = Component.translatable("pipcam.status.unpinned");
        }
        g.drawCenteredString(font, status, width / 2, 20, 0xB0B0B0);

        if (viewsLabelY >= 0) {
            int count = inWorld ? PipActions.viewCount() : 0;
            int sel = s.selectedView;
            Component views;
            if (!inWorld || count == 0) {
                views = Component.translatable("pipcam.views.none");
            } else if (sel >= 0 && sel < count) {
                String dim = PipActions.viewDimension(sel);
                boolean otherDim = dim != null && minecraft.level != null
                        && !dim.equals(minecraft.level.dimension().location().toString());
                views = otherDim
                        ? Component.translatable("pipcam.views.selected_dim", sel + 1, count, shortDim(dim))
                        : Component.translatable("pipcam.views.selected", sel + 1, count);
            } else {
                views = Component.translatable("pipcam.views.count", count);
            }
            g.drawString(font, views, width / 2 - 156, viewsLabelY, 0xC8C8C8);
        }
    }

    private static String shortDim(String dim) {
        int colon = dim.indexOf(':');
        return colon >= 0 ? dim.substring(colon + 1) : dim;
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
