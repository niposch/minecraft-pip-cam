package dev.pipcam;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.pipcam.mixin.GameRendererAccess;
import dev.pipcam.mixin.LevelRendererAccess;
import dev.pipcam.mixin.LightTextureAccess;
import dev.pipcam.mixin.RenderSystemAccess;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders the level from the pinned pose into an offscreen {@link RenderTarget} using the ordinary
 * vanilla/Sodium level-render path. Works with zero required third-party dependencies while preserving
 * full optional compatibility with Sodium, Iris, Distant Horizons, Veil/Sable, and Create Deep Seas.
 *
 * <p>Rendering flow adapted from Veil's VeilLevelPerspectiveRenderer (MIT).
 */
public final class PipLevelRenderer {
    private static final PipCamera CAMERA = new PipCamera();
    private static final Matrix4f TMP_PROJ = new Matrix4f();
    private static final Matrix4f BACKUP_PROJECTION = new Matrix4f();
    private static final Quaternionf TMP_ROT = new Quaternionf();
    private static final PoseStack POSE = new PoseStack();
    private static final Vector3f BACKUP_L0 = new Vector3f(), BACKUP_L1 = new Vector3f();

    /** True only while PiP is being rendered; mixins use this to specialise behaviour. */
    public static volatile boolean rendering;
    /** The framebuffer currently being drawn into, valid only while {@link #rendering}. */
    public static volatile RenderTarget current;
    private static boolean renderDistantHorizons;

    /** Radius (chunks) of fully loaded vanilla chunks around the PiP camera, for Distant Horizons. */
    public static int vanillaRadiusChunks;
    private static int cachedRadCx = Integer.MIN_VALUE, cachedRadCz = Integer.MIN_VALUE, cachedRadMax = -1;
    private static long cachedRadNanos;

    private PipLevelRenderer() {}

    public static Camera camera() {
        return CAMERA;
    }

    public static boolean canRenderDistantHorizons() {
        return renderDistantHorizons;
    }

    private static void refreshLightmap(GameRenderer gameRenderer, float partialTick) {
        ((LightTextureAccess) gameRenderer.lightTexture()).pipcam$setUpdateLightTexture(true);
        gameRenderer.lightTexture().updateLightTexture(partialTick);
    }

    static boolean isSurfaceReady(ClientLevel level, int x, int z) {
        if (!level.hasChunk(x, z)) return false;
        int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, x * 16 + 8, z * 16 + 8) - 1;
        return SodiumBridge.sectionRendered(level, x, ground >> 4, z);
    }

    /** Walks concentric chunk rings in O(R^2) to find the largest contiguous loaded radius around (cx, cz). */
    static int contiguousLoadedRadius(ClientLevel level, int cx, int cz, int max) {
        if (!isSurfaceReady(level, cx, cz)) return 0;
        for (int r = 1; r <= max; r++) {
            for (int dx = -r; dx <= r; dx++) {
                if (!isSurfaceReady(level, cx + dx, cz - r) || !isSurfaceReady(level, cx + dx, cz + r)) {
                    return r - 1;
                }
            }
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                if (!isSurfaceReady(level, cx - r, cz + dz) || !isSurfaceReady(level, cx + r, cz + dz)) {
                    return r - 1;
                }
            }
        }
        return max;
    }

    static final class PipCamera extends Camera {
        private boolean detached = true;

        void pose(ClientLevel level, Entity entity, Vec3 pos, float yaw, float pitch, float partialTick) {
            super.setup(level, entity, true, false, partialTick);
            this.setPosition(pos);
            this.setRotation(yaw, pitch);
            if (entity == null) {
                this.detached = true;
            } else {
                // Hide the local player when the PiP lens is inside their head/body (e.g. right after "Pin at my view")
                Vec3 delta = entity.getPosition(partialTick).subtract(entity.position());
                boolean insideBody = entity.getBoundingBox().move(delta).inflate(0.35).contains(pos);
                boolean nearEyes = pos.distanceToSqr(entity.getEyePosition(partialTick)) <= 0.64;
                this.detached = !insideBody && !nearEyes;
            }
        }

        @Override
        public boolean isDetached() {
            return this.detached;
        }
    }

    public static void render(RenderTarget fbo, Matrix4f projection, Vec3 pos, float yaw, float pitch,
                              float renderDistanceChunks, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        buffers.endBatch();

        GameRenderer gameRenderer = mc.gameRenderer;
        LevelRenderer levelRenderer = mc.levelRenderer;
        LevelRendererAccess lra = (LevelRendererAccess) levelRenderer;
        GameRendererAccess gra = (GameRendererAccess) gameRenderer;
        PipRenderTarget mainTarget = (PipRenderTarget) mc.getMainRenderTarget();

        if (DhStateSwap.isAvailable() && PipState.INSTANCE.distantHorizons) {
            int cx = Mth.floor(pos.x) >> 4, cz = Mth.floor(pos.z) >> 4;
            int maxRd = mc.options.getEffectiveRenderDistance();
            long now = System.nanoTime();
            if (cx != cachedRadCx || cz != cachedRadCz || maxRd != cachedRadMax || now - cachedRadNanos >= 250_000_000L) {
                vanillaRadiusChunks = contiguousLoadedRadius(mc.level, cx, cz, maxRd);
                cachedRadCx = cx; cachedRadCz = cz; cachedRadMax = maxRd; cachedRadNanos = now;
            }
        } else {
            vanillaRadiusChunks = 0;
        }

        float pt = dt.getGameTimeDeltaPartialTick(false);
        CAMERA.pose(mc.level, mc.cameraEntity != null ? mc.cameraEntity : mc.player, pos, yaw, pitch, pt);
        POSE.setIdentity();
        POSE.mulPose(CAMERA.rotation().conjugate(TMP_ROT));

        float backupRD = gameRenderer.getRenderDistance();
        gra.pipcam$setRenderDistance(renderDistanceChunks * 16.0F);
        float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        FogShape fogShape = RenderSystem.getShaderFogShape();

        BACKUP_PROJECTION.set(RenderSystem.getProjectionMatrix());
        gameRenderer.resetProjectionMatrix(TMP_PROJ.set(projection));
        Vector3f[] lightDirs = RenderSystemAccess.pipcam$getShaderLightDirections();
        BACKUP_L0.set(lightDirs[0]);
        BACKUP_L1.set(lightDirs[1]);

        Matrix4fStack mvs = RenderSystem.getModelViewStack();
        mvs.pushMatrix();
        mvs.identity();
        RenderSystem.applyModelViewMatrix();

        HitResult bHit = mc.hitResult;
        Entity bPick = mc.crosshairPickEntity;
        Frustum bFrustum = lra.pipcam$getCullingFrustum();
        Object veilMatrices = VeilBridge.backup();
        Object irisState = IrisSnapshot.capture();
        boolean fixedLight = PipState.INSTANCE.lighting != PipState.LIGHT_AUTO;

        rendering = true;
        current = fbo;
        try {
            DhDepth.beginFrame();
            renderDistantHorizons = DhStateSwap.enterPip();
            if (fixedLight) refreshLightmap(gameRenderer, pt);
            fbo.clear(Minecraft.ON_OSX);
            fbo.bindWrite(true);
            mainTarget.pipcam$setRedirect(fbo);
            levelRenderer.prepareCullFrustum(pos, POSE.last().pose(), TMP_PROJ);
            levelRenderer.renderLevel(dt, false, CAMERA, gameRenderer, gameRenderer.lightTexture(), POSE.last().pose(), TMP_PROJ);
            buffers.endBatch();
        } finally {
            rendering = false;
            renderDistantHorizons = false;
            current = null;
            DhStateSwap.exitPip();
            IrisSnapshot.restore(irisState);
            if (fixedLight) refreshLightmap(gameRenderer, pt); // back to the main view's lightmap
            VeilBridge.restore(veilMatrices);
            lra.pipcam$setCullingFrustum(bFrustum);
            mainTarget.pipcam$setRedirect(null);
            fbo.unbindWrite();
            mc.crosshairPickEntity = bPick;
            mc.hitResult = bHit;
            mvs.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setShaderLights(BACKUP_L0, BACKUP_L1);
            gameRenderer.resetProjectionMatrix(BACKUP_PROJECTION);
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.setShaderFogShape(fogShape);
            gra.pipcam$setRenderDistance(backupRD);
            Camera main = gameRenderer.getMainCamera();
            mc.getBlockEntityRenderDispatcher().prepare(mc.level, main, mc.hitResult);
            mc.getEntityRenderDispatcher().prepare(mc.level, main, mc.crosshairPickEntity);
            SodiumIrisSync.afterPipRender();
        }
    }
}
