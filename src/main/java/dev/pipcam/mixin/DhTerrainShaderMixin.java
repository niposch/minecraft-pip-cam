package dev.pipcam.mixin;

import dev.pipcam.PipCamMod;
import dev.pipcam.PipLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** DH advances its TAA jitter frame index on every terrain render; the PiP must not advance the main view's sequence. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.render.openGl.terrain.GlDhTerrainShaderProgram_neoforge", remap = false)
public class DhTerrainShaderMixin {
    @Unique private static Field pipcam$frameField;
    @Unique private static boolean pipcam$frameMissing;
    @Unique private int pipcam$savedFrame;

    @Unique
    private Field pipcam$field() {
        if (pipcam$frameField == null && !pipcam$frameMissing) {
            try {
                pipcam$frameField = this.getClass().getDeclaredField("frameIndexMod8");
                pipcam$frameField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                pipcam$frameMissing = true;
            }
        }
        return pipcam$frameField;
    }

    @Unique private static Field pipcam$clipField;
    @Unique private static Method pipcam$setUniform;
    @Unique private static boolean pipcam$clipFailed;

    /**
     * DH fades out (dithers) every LOD within "near clip + 16 blocks" of the camera, assuming real chunks are drawn
     * there. Around a PiP camera far from the player there are none, which left a see-through hole in the ground.
     */
    @Unique
    private void pipcam$noClip() {
        if (pipcam$clipFailed) return;
        try {
            if (pipcam$clipField == null) {
                pipcam$clipField = this.getClass().getDeclaredField("uClipDistance");
                pipcam$clipField.setAccessible(true);
                Class<?> c = this.getClass();
                while (pipcam$setUniform == null && c != null) {
                    try {
                        pipcam$setUniform = c.getDeclaredMethod("setUniform", int.class, float.class);
                    } catch (NoSuchMethodException e) {
                        c = c.getSuperclass();
                    }
                }
                if (pipcam$setUniform == null) throw new NoSuchMethodException("setUniform(int, float)");
                pipcam$setUniform.setAccessible(true);
            }
            // The fade shader evaluates smoothstep(clip, 1.5 * clip, distance). Its edges must differ.
            pipcam$setUniform.invoke(this, pipcam$clipField.getInt(this), 0.001f);
        } catch (Throwable t) {
            pipcam$clipFailed = true;
            PipCamMod.LOGGER.warn("Could not adjust Distant Horizons clip distance for the PiP", t);
        }
    }

    @Inject(method = "fillUniformData", at = @At("HEAD"), require = 0)
    private void pipcam$save(CallbackInfo ci) {
        Field f;
        if (PipLevelRenderer.rendering && (f = pipcam$field()) != null) {
            try { pipcam$savedFrame = f.getInt(this); } catch (IllegalAccessException ignored) {}
        }
    }

    @Inject(method = "fillUniformData", at = @At("RETURN"), require = 0)
    private void pipcam$restore(CallbackInfo ci) {
        Field f;
        if (PipLevelRenderer.rendering && (f = pipcam$field()) != null) {
            try { f.setInt(this, pipcam$savedFrame); } catch (IllegalAccessException ignored) {}
        }
        if (PipLevelRenderer.rendering && PipLevelRenderer.vanillaRadiusChunks == 0) pipcam$noClip();
    }
}
