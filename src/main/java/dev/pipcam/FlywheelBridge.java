package dev.pipcam;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Optional Flywheel + Colorwheel compatibility bridge (zero compile/runtime dependencies).
 *
 * <p>When an Iris shader pack is active alongside Colorwheel (e.g. Create), Flywheel uses Colorwheel's Iris backend
 * and suppresses Minecraft's vanilla {@code BlockEntityRenderer}s for kinetic parts (cogwheels, shafts, belts, fans...).
 * Because the PiP renders through Iris's plain vanilla pipeline, Colorwheel skips drawing its Iris instanced geometry.
 * While the PiP renders and Colorwheel is active, we report {@code supportsVisualization = false} so Create's own
 * built-in vanilla renderers draw entities and kinetic block entities cleanly into the PiP without touching the main
 * view's Colorwheel/Flywheel state.
 */
@EventBusSubscriber(modid = PipCamMod.MODID, value = Dist.CLIENT)
public final class FlywheelBridge {
    private static final boolean PRESENT =
            FMLLoader.getLoadingModList().getModFileById("flywheel") != null
            && FMLLoader.getLoadingModList().getModFileById("colorwheel") != null;

    private static boolean initialized, failed;
    private static Method getSafeFlw, isColorwheelCurrentBackend;
    private static Field managersField, blockEntitiesField, visualsField;
    private static Method levelAttachedGet, getStorage, skipVanillaRenderBe;

    private FlywheelBridge() {}

    /** True while the PiP is rendering and Colorwheel is the active Flywheel backend. */
    public static boolean useVanillaFallback() {
        if (!PRESENT || failed || !PipLevelRenderer.rendering || !RenderSystem.isOnRenderThread()) return false;
        try {
            if (!initialized) init();
            Object safeFlw = getSafeFlw.invoke(null);
            return safeFlw != null && (boolean) isColorwheelCurrentBackend.invoke(safeFlw);
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    @SubscribeEvent
    static void afterBlockEntities(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        if (!useVanillaFallback()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        try {
            Object managers = managersField.get(null);
            Object manager = managers == null ? null : levelAttachedGet.invoke(managers, level);
            if (manager == null) return;

            Object beVisualManager = blockEntitiesField.get(manager);
            if (beVisualManager == null) return;
            Object storage = getStorage.invoke(beVisualManager);
            if (storage == null) return;

            @SuppressWarnings("unchecked")
            Map<BlockEntity, ?> visuals = (Map<BlockEntity, ?>) visualsField.get(storage);
            if (visuals == null || visuals.isEmpty()) return;

            BlockEntityRenderDispatcher dispatcher = mc.getBlockEntityRenderDispatcher();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            PoseStack poseStack = e.getPoseStack();
            Camera camera = e.getCamera();
            Vec3 camPos = camera.getPosition();
            Frustum frustum = e.getFrustum();
            float partialTick = e.getPartialTick().getGameTimeDeltaPartialTick(false);
            double maxDistSq = Math.pow(PipState.INSTANCE.renderDistanceChunks * 16.0, 2);

            for (BlockEntity be : visuals.keySet()) {
                if (be == null || be.isRemoved() || be.getLevel() != level) continue;

                BlockPos pos = be.getBlockPos();
                double dx = pos.getX() + 0.5 - camPos.x;
                double dy = pos.getY() + 0.5 - camPos.y;
                double dz = pos.getZ() + 0.5 - camPos.z;
                if (dx * dx + dy * dy + dz * dz > maxDistSq) continue;
                // Only draw BEs whose vanilla BER was skipped at section-compile time; others already drew during renderLevel.
                if (!(boolean) skipVanillaRenderBe.invoke(null, be)) continue;
                if (!ClientHooks.isBlockEntityRendererVisible(dispatcher, be, frustum)) continue;

                poseStack.pushPose();
                poseStack.translate(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);
                dispatcher.render(be, partialTick, poseStack, buffers);
                poseStack.popPose();
            }
            buffers.endBatch();
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static void init() throws ReflectiveOperationException {
        initialized = true;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        Class<?> cw = Class.forName("dev.djefrey.colorwheel.Colorwheel", true, cl);
        getSafeFlw = cw.getMethod("getSafeFlw");
        Class<?> safeFlw = Class.forName("dev.djefrey.colorwheel.ClrwlSafeFlw", true, cl);
        isColorwheelCurrentBackend = safeFlw.getMethod("isColorwheelCurrentBackend");

        Class<?> vmi = Class.forName("dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl", true, cl);
        managersField = vmi.getDeclaredField("MANAGERS");
        managersField.setAccessible(true);
        blockEntitiesField = vmi.getDeclaredField("blockEntities");
        blockEntitiesField.setAccessible(true);

        Class<?> levelAttached = managersField.getType();
        levelAttachedGet = levelAttached.getMethod("get", LevelAccessor.class);

        Class<?> visualManagerImpl = Class.forName("dev.engine_room.flywheel.impl.visualization.VisualManagerImpl", true, cl);
        getStorage = visualManagerImpl.getMethod("getStorage");

        Class<?> storageCls = Class.forName("dev.engine_room.flywheel.impl.visualization.storage.Storage", true, cl);
        visualsField = storageCls.getDeclaredField("visuals");
        visualsField.setAccessible(true);

        Class<?> helper = Class.forName("dev.engine_room.flywheel.lib.visualization.VisualizationHelper", true, cl);
        skipVanillaRenderBe = helper.getMethod("skipVanillaRender", BlockEntity.class);
    }

    private static void fail(Throwable t) {
        failed = true;
        PipCamMod.LOGGER.warn("Flywheel/Colorwheel PiP fallback disabled due to unexpected internals", t);
    }
}
