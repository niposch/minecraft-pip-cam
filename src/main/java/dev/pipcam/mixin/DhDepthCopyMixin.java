package dev.pipcam.mixin;

import dev.pipcam.DhDepth;
import dev.pipcam.PipLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.render.openGl.GlDhMetaRenderer_neoforge", remap = false)
public abstract class DhDepthCopyMixin {
    @Shadow public abstract int getActiveDepthTextureId();

    @Inject(method = "copyToMcTexture", at = @At("RETURN"), require = 0)
    private void pipcam$copyDepth(@Coerce Object params, CallbackInfo ci) {
        if (PipLevelRenderer.canRenderDistantHorizons()) {
            DhDepth.copy(PipLevelRenderer.current, getActiveDepthTextureId(), params);
        }
    }
}
