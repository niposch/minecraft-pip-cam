package dev.pipcam;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;

/** Draws the PiP window in the HUD. The world view itself is rendered at the start of each frame. */
@EventBusSubscriber(modid = PipCamMod.MODID, value = Dist.CLIENT)
public final class PipHud {
    private static final int MARGIN = 6;
    private static final int HOTBAR_CLEARANCE = 40;

    private PipHud() {}

    @SubscribeEvent
    static void pre(RenderFrameEvent.Pre e) {
        PipRenderer.tick(Minecraft.getInstance());
    }

    @SubscribeEvent
    static void post(RenderGuiEvent.Post e) {
        PipState s = PipState.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        if (!s.pinned || s.editing || mc.options.hideGui) return;
        if (mc.screen instanceof PipHudDragScreen) return;
        boolean peeking = s.peeking && mc.screen == null;
        if (!peeking && (!s.visible || mc.getDebugOverlay().showDebugScreen())) return;

        GuiGraphics g = e.getGuiGraphics();
        int sw = g.guiWidth(), sh = g.guiHeight();
        if (peeking) {
            int maxW = (int) (sw * 0.72f);
            int maxH = (int) (sh * 0.72f);
            int[] r = fitRect(maxW, maxH, s.aspectValue());
            int x = (sw - r[0]) / 2, y = (sh - r[1]) / 2;
            g.fill(0, 0, sw, sh, 0x55000000);
            drawWindow(g, mc, x, y, r[0], r[1], true, true);
            return;
        }

        int[] r = hudRect(sw, sh);
        drawWindow(g, mc, r[0], r[1], r[2], r[3], s.showFrame, s.showLabel);
    }

    /** Computes {x, y, w, h} for the HUD PiP window at the given GUI dimensions. */
    static int[] hudRect(int sw, int sh) {
        PipState s = PipState.INSTANCE;
        int targetW = Math.max(64, sw * s.sizePercent / 100);
        int maxH = Math.max(48, (int) (sh * 0.65f));
        int[] dims = fitRect(Math.min(targetW, sw - MARGIN * 2), maxH, s.aspectValue());
        int w = dims[0], h = dims[1];
        int x, y;
        if (s.customPos) {
            int maxX = Math.max(0, sw - w - MARGIN * 2);
            int maxY = Math.max(0, sh - h - MARGIN * 2);
            x = MARGIN + Math.round(Mth.clamp(s.posX, 0f, 1f) * maxX);
            y = MARGIN + Math.round(Mth.clamp(s.posY, 0f, 1f) * maxY);
        } else {
            x = (s.corner == 0 || s.corner == 2) ? MARGIN : sw - w - MARGIN;
            y = (s.corner == 0 || s.corner == 1) ? MARGIN : Math.max(MARGIN, sh - h - MARGIN - HOTBAR_CLEARANCE);
        }
        return new int[]{x, y, w, h};
    }

    static int[] fitRect(int maxW, int maxH, float aspect) {
        int safeW = Math.max(16, maxW);
        int safeH = Math.max(16, maxH);
        int w = Math.min(safeW, Math.round(safeH * aspect));
        int h = Math.max(16, Math.round(w / aspect));
        if (h > safeH) {
            h = safeH;
            w = Math.max(16, Math.round(h * aspect));
        }
        return new int[]{w, h};
    }

    static void drawWindow(GuiGraphics g, Minecraft mc, int x, int y, int w, int h, boolean frame, boolean showLabel) {
        PipState s = PipState.INSTANCE;
        boolean otherDim = !PipRenderer.inCameraDimension(mc);
        if (frame) {
            // Subtle outer dark shadow + crisp inner border so the frame reads clearly on both bright sky and dark caves
            g.renderOutline(x - 2, y - 2, w + 4, h + 4, 0xA0000000);
        }
        if (otherDim || PipRenderer.textureId() < 0) {
            g.fill(x, y, x + w, y + h, 0xC8101014);
        } else {
            drawTexture(g, x, y, w, h);
        }
        if (frame) {
            int borderColor = (s.paused || otherDim) ? 0xB8AAAAAA : 0xD8FFFFFF;
            g.renderOutline(x - 1, y - 1, w + 2, h + 2, borderColor);
        }

        Component badge = badgeText(s, otherDim, showLabel);
        if (badge != null) {
            int tw = mc.font.width(badge);
            int padX = 4, padY = 2;
            int bx = x + 3;
            int by = y + h - mc.font.lineHeight - padY * 2 - 2;
            if (by >= y + 2 && bx + tw + padX * 2 <= x + w - 2) {
                g.fill(bx, by, bx + tw + padX * 2, by + mc.font.lineHeight + padY * 2 - 1, 0x98000000);
                int textColor = otherDim ? 0xFFBBBBBB : s.paused ? 0xFFFFD27F : 0xFFFFFFFF;
                g.drawString(mc.font, badge, bx + padX, by + padY, textColor, false);
            }
        }
    }

    private static Component badgeText(PipState s, boolean otherDim, boolean showLabel) {
        if (otherDim) return Component.translatable("pipcam.hud.other_dimension");
        if (s.paused) return Component.translatable("pipcam.hud.paused");
        if (!showLabel) return null;
        String name = s.selectedView >= 0 ? PipActions.viewName(s.selectedView) : "";
        if (!name.isEmpty()) {
            return s.trackPlayer
                    ? Component.translatable("pipcam.hud.view_tracking", name)
                    : Component.literal(name);
        }
        return s.trackPlayer ? Component.translatable("pipcam.hud.tracking") : null;
    }

    /** Draws the latest PiP picture (flipped: GL textures are bottom-up). */
    static void drawTexture(GuiGraphics g, int x, int y, int w, int h) {
        int tex = PipRenderer.textureId();
        if (tex < 0) return;
        Matrix4f m = g.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, x, y + h, 0).setUv(0, 0);
        b.addVertex(m, x + w, y + h, 0).setUv(1, 0);
        b.addVertex(m, x + w, y, 0).setUv(1, 1);
        b.addVertex(m, x, y, 0).setUv(0, 1);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }
}
