package dev.pipcam;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** One key opens the menu (default O); the rest are optional shortcuts, unbound by default. */
public final class PipKeys {
    static final String CATEGORY = "key.categories.pipcam";
    static final KeyMapping MENU = new KeyMapping("key.pipcam.menu", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY);
    static final KeyMapping TOGGLE = new KeyMapping("key.pipcam.toggle", KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN, CATEGORY);
    static final KeyMapping PAUSE = new KeyMapping("key.pipcam.pause", KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN, CATEGORY);
    static final KeyMapping PEEK = new KeyMapping("key.pipcam.peek", KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN, CATEGORY);
    static final KeyMapping PIN_HERE = new KeyMapping("key.pipcam.pin_here", KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN, CATEGORY);
    static final KeyMapping PLACE = new KeyMapping("key.pipcam.place", KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN, CATEGORY);
    static final KeyMapping TRACK = new KeyMapping("key.pipcam.track", KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN, CATEGORY);
    static final KeyMapping NEXT_VIEW = new KeyMapping("key.pipcam.next_view", KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN, CATEGORY);
    static final KeyMapping PREV_VIEW = new KeyMapping("key.pipcam.prev_view", KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN, CATEGORY);

    private PipKeys() {}

    static void register(RegisterKeyMappingsEvent e) {
        e.register(MENU);
        e.register(TOGGLE);
        e.register(PAUSE);
        e.register(PEEK);
        e.register(PIN_HERE);
        e.register(PLACE);
        e.register(TRACK);
        e.register(NEXT_VIEW);
        e.register(PREV_VIEW);
    }

    @EventBusSubscriber(modid = PipCamMod.MODID, value = Dist.CLIENT)
    static final class Handler {
        @SubscribeEvent
        static void tick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            PipState s = PipState.INSTANCE;
            if (mc.player == null) {
                s.peeking = false;
                return;
            }
            s.peeking = mc.screen == null && s.pinned && !PEEK.isUnbound() && PEEK.isDown();

            while (MENU.consumeClick()) mc.setScreen(new PipScreen(null));
            while (TOGGLE.consumeClick()) {
                s.visible = !s.visible;
                PipConfig.save();
            }
            while (PAUSE.consumeClick()) {
                s.paused = !s.paused;
                mc.player.displayClientMessage(Component.translatable(
                        s.paused ? "pipcam.msg.paused" : "pipcam.msg.resumed"), true);
            }
            while (PIN_HERE.consumeClick()) {
                PipActions.pinAtPlayer();
                mc.player.displayClientMessage(Component.translatable("pipcam.msg.pinned"), true);
            }
            while (PLACE.consumeClick()) PipActions.openPlacement(null);
            while (TRACK.consumeClick()) {
                s.trackPlayer = !s.trackPlayer;
                PipConfig.save();
                mc.player.displayClientMessage(Component.translatable(
                        s.trackPlayer ? "pipcam.msg.track_on" : "pipcam.msg.track_off"), true);
            }
            while (NEXT_VIEW.consumeClick()) announceView(mc, PipActions.cycleView(1));
            while (PREV_VIEW.consumeClick()) announceView(mc, PipActions.cycleView(-1));
        }

        private static void announceView(Minecraft mc, boolean ok) {
            int i = PipState.INSTANCE.selectedView;
            mc.player.displayClientMessage(ok
                    ? Component.translatable("pipcam.msg.view", PipActions.viewName(i), i + 1, PipActions.viewCount())
                    : Component.translatable("pipcam.msg.no_views"), true);
        }
    }
}
