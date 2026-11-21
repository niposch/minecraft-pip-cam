package dev.pipcam.mixin;

import dev.pipcam.PipLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Distant Horizons' TAA keeps the previous frame's camera/projection and colour history in singletons. If the PiP
 * pass ran TAA, the main view would reproject from the PiP camera (ghosting, retention, flicker). The PiP skips DH TAA.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.render.openGl.postProcessing.antialiasing.GlDhTaaRenderer_neoforge", remap = false)
public class DhTaaMixins {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$skipTaa(CallbackInfo ci) {
        if (PipLevelRenderer.rendering) ci.cancel();
    }
}
