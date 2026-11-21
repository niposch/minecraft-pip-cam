package dev.pipcam.mixin;

import dev.pipcam.DhDepth;
import dev.pipcam.PipCamMod;
import dev.pipcam.PipLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Distant Horizons in the PiP.
 *
 * <p>DH clears its whole LOD render cache whenever "Iris shader pack in use" differs from the previous frame. The PiP
 * renders with Iris's vanilla pipeline (answer: no) while the main view is shaded (answer: yes), so without this DH
 * would wipe its cache on every PiP frame: no LODs in the PiP and flickering LODs in the main view. While the PiP
 * renders we pretend the previous frame was also unshaded, then restore DH's real value afterwards.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.api.internal.ClientApi", remap = false)
public class DhClientApiMixin {
    @Unique private static Field pipcam$lastFrameField;
    @Unique private static boolean pipcam$fieldMissing;
    @Unique private boolean pipcam$saved;
    @Unique private int pipcam$depth;

    @Inject(method = {"renderLods", "renderDeferredLodsForShaders"},
            at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$head(CallbackInfo ci) {
        if (!PipLevelRenderer.rendering) return;
        if (!PipLevelRenderer.canRenderDistantHorizons()) {
            ci.cancel();
            return;
        }
        Field f = pipcam$field();
        if (f == null) return;
        if (pipcam$depth++ == 0) {
            try {
                pipcam$saved = f.getBoolean(this);
                f.setBoolean(this, false);
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    @Inject(method = {"renderLods", "renderDeferredLodsForShaders"},
            at = @At("RETURN"), require = 0)
    private void pipcam$tail(CallbackInfo ci) {
        if (pipcam$depth <= 0) return;
        if (--pipcam$depth == 0) {
            Field f = pipcam$field();
            if (f != null) {
                try {
                    f.setBoolean(this, pipcam$saved);
                } catch (IllegalAccessException ignored) {
                }
            }
        }
    }

    @Inject(method = {"renderFadeOpaque", "renderFadeTransparent"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$skipVanillaFade(CallbackInfo ci) {
        // The PiP uses depth at the overlap. A radial color fade would overwrite that result.
        if (PipLevelRenderer.rendering && (DhDepth.copiedThisFrame()
                || !PipLevelRenderer.canRenderDistantHorizons() || PipLevelRenderer.vanillaRadiusChunks == 0)) {
            ci.cancel();
        }
    }

    @Unique
    private Field pipcam$field() {
        if (pipcam$lastFrameField == null && !pipcam$fieldMissing) {
            try {
                Field f = this.getClass().getDeclaredField("irisShadersEnabledLastFrame");
                f.setAccessible(true);
                pipcam$lastFrameField = f;
            } catch (NoSuchFieldException e) {
                pipcam$fieldMissing = true;
                PipCamMod.LOGGER.warn("Distant Horizons internals changed; LODs in the PiP may flicker");
            }
        }
        return pipcam$lastFrameField;
    }
}
