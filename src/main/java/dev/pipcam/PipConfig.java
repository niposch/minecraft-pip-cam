package dev.pipcam;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.pipcam.mixin.MinecraftServerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Saves settings and the pinned camera (one per world/server) to config/pipcam.json. */
@EventBusSubscriber(modid = PipCamMod.MODID, value = Dist.CLIENT)
public final class PipConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("pipcam.json");

    static final class Pin {
        String name;
        String dimension;
        double x, y, z;
        float yaw, pitch, fov;
        boolean trackPlayer;
    }

    static final class Data {
        boolean visible = true;
        int renderWidth = 480;
        int aspectRatio = PipState.ASPECT_16_9;
        int targetFps = 15;
        int renderDistanceChunks = 8;
        boolean retainChunks = true;
        int retainRadiusChunks = 8;
        boolean distantHorizons = true;
        boolean renderWeather = false;
        boolean renderClouds = true;
        int corner = 1;
        boolean customPos = false;
        float posX = 1.0f;
        float posY = 0.0f;
        int sizePercent = 25;
        boolean showFrame = true;
        boolean showLabel = true;
        int lighting = 0;
        Map<String, Pin> pins = new HashMap<>();
        /** Saved camera views per world/server. */
        Map<String, List<Pin>> views = new HashMap<>();
    }

    private static Data data = new Data();

    private PipConfig() {}

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    Data d = GSON.fromJson(r, Data.class);
                    if (d != null) data = d;
                    if (data.pins == null) data.pins = new HashMap<>();
                    if (data.views == null) data.views = new HashMap<>();
                }
            }
        } catch (Exception e) {
            PipCamMod.LOGGER.warn("Could not read {}, using defaults", FILE, e);
            data = new Data();
        }
        PipState s = PipState.INSTANCE;
        s.visible = data.visible;
        s.setAspectRatio(Mth.clamp(data.aspectRatio, 0, 2));
        s.setRenderWidth(Mth.clamp(data.renderWidth, 160, 1920));
        s.targetFps = Mth.clamp(data.targetFps, 1, 60);
        s.renderDistanceChunks = Mth.clamp(data.renderDistanceChunks, 2, 32);
        s.retainChunks = data.retainChunks;
        s.retainRadiusChunks = Mth.clamp(data.retainRadiusChunks, 1, 16);
        s.distantHorizons = data.distantHorizons;
        s.renderWeather = data.renderWeather;
        s.renderClouds = data.renderClouds;
        s.corner = Mth.clamp(data.corner, 0, 3);
        s.customPos = data.customPos;
        s.posX = Float.isFinite(data.posX) ? Mth.clamp(data.posX, 0f, 1f) : 1f;
        s.posY = Float.isFinite(data.posY) ? Mth.clamp(data.posY, 0f, 1f) : 0f;
        s.sizePercent = Mth.clamp(data.sizePercent, 10, 60);
        s.showFrame = data.showFrame;
        s.showLabel = data.showLabel;
        s.lighting = Mth.clamp(data.lighting, 0, 2);
    }

    public static void save() {
        PipState s = PipState.INSTANCE;
        data.visible = s.visible;
        data.renderWidth = s.renderWidth;
        data.aspectRatio = s.aspectRatio;
        data.targetFps = s.targetFps;
        data.renderDistanceChunks = s.renderDistanceChunks;
        data.retainChunks = s.retainChunks;
        data.retainRadiusChunks = s.retainRadiusChunks;
        data.distantHorizons = s.distantHorizons;
        data.renderWeather = s.renderWeather;
        data.renderClouds = s.renderClouds;
        data.corner = s.corner;
        data.customPos = s.customPos;
        data.posX = s.posX;
        data.posY = s.posY;
        data.sizePercent = s.sizePercent;
        data.showFrame = s.showFrame;
        data.showLabel = s.showLabel;
        data.lighting = s.lighting;
        String key = worldKey();
        if (key != null) {
            if (s.pinned) {
                data.pins.put(key, fromState(null));
            } else {
                data.pins.remove(key);
            }
        }
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            PipCamMod.LOGGER.warn("Could not write {}", FILE, e);
        }
    }

    /** Saved views for the current world (mutable; call save() after changing). */
    static List<Pin> views() {
        String key = worldKey();
        if (key == null) return new ArrayList<>();
        List<Pin> existing = data.views.get(key);
        if (existing != null) return existing;
        // Migrate legacy singleplayer key (display name) if present
        String legacy = legacyWorldKey();
        if (legacy != null && !legacy.equals(key) && data.views.containsKey(legacy)) {
            List<Pin> migrated = data.views.remove(legacy);
            data.views.put(key, migrated);
            return migrated;
        }
        return data.views.computeIfAbsent(key, k -> new ArrayList<>());
    }

    static Pin fromState(String name) {
        PipState s = PipState.INSTANCE;
        Pin p = new Pin();
        p.name = name;
        p.dimension = s.dimension == null ? null : s.dimension.location().toString();
        p.x = s.x; p.y = s.y; p.z = s.z;
        p.yaw = s.yaw; p.pitch = s.pitch; p.fov = s.fov;
        p.trackPlayer = s.trackPlayer;
        return p;
    }

    static void applyToState(Pin p) {
        PipState s = PipState.INSTANCE;
        s.dimension = parseDimension(p.dimension);
        s.x = Double.isFinite(p.x) ? p.x : 0.0;
        s.y = Double.isFinite(p.y) ? p.y : 64.0;
        s.z = Double.isFinite(p.z) ? p.z : 0.0;
        s.yaw = Float.isFinite(p.yaw) ? Mth.wrapDegrees(p.yaw) : 0f;
        s.pitch = Float.isFinite(p.pitch) ? Mth.clamp(p.pitch, -90f, 90f) : 0f;
        s.fov = (Float.isFinite(p.fov) && p.fov >= 20f && p.fov <= 120f) ? p.fov : 70f;
        s.trackPlayer = p.trackPlayer;
        s.pinned = true;
        s.paused = false;
    }

    private static ResourceKey<Level> parseDimension(String dim) {
        if (dim == null || dim.isBlank()) return null;
        ResourceLocation id = ResourceLocation.tryParse(dim);
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    /** Identifies the current world: server address in multiplayer, save folder ID in singleplayer. */
    static String worldKey() {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (server != null) {
            try {
                String folder = ((MinecraftServerAccess) server).pipcam$getStorageSource().getLevelId();
                if (folder != null && !folder.isBlank()) return "sp:" + folder;
            } catch (Throwable ignored) {
            }
            return "sp:" + server.getWorldData().getLevelName();
        }
        if (mc.getCurrentServer() != null) return "mp:" + mc.getCurrentServer().ip;
        return null;
    }

    private static String legacyWorldKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        return null;
    }

    @SubscribeEvent
    static void onJoin(ClientPlayerNetworkEvent.LoggingIn e) {
        PipState s = PipState.INSTANCE;
        s.selectedView = -1;
        s.pinned = false;
        s.paused = false;
        s.peeking = false;
        s.trackPlayer = false;
        String key = worldKey();
        Pin p = key == null ? null : data.pins.get(key);
        if (p == null) {
            String legacy = legacyWorldKey();
            if (legacy != null && !legacy.equals(key)) {
                p = data.pins.remove(legacy);
                if (p != null && key != null) data.pins.put(key, p);
            }
        }
        if (p != null) {
            applyToState(p);
        }
    }

    @SubscribeEvent
    static void onLeave(ClientPlayerNetworkEvent.LoggingOut e) {
        if (worldKey() != null) save();
        PipState s = PipState.INSTANCE;
        s.pinned = false;
        s.editing = false;
        s.peeking = false;
        ChunkRetention.clear();
        PipRenderer.free();
    }
}
