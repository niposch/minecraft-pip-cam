package dev.pipcam.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.pipcam.PipLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Distant Horizons reads the target framebuffer straight from Minecraft's main RenderTarget fields.
 * Point it at the PiP framebuffer on the render thread while the PiP renders.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper_neoforge", remap = false)
public class DhRenderWrapperMixin {
    @Unique
    private static RenderTarget pipcam$target() {
        RenderTarget f = PipLevelRenderer.current;
        return f != null && RenderSystem.isOnRenderThread() ? f : null;
    }

    @Inject(method = "getTargetFramebuffer", at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$fb(CallbackInfoReturnable<Integer> cir) {
        RenderTarget f = pipcam$target();
        if (f != null) cir.setReturnValue(f.frameBufferId);
    }

    @Inject(method = "getGlColorTextureId", at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$color(CallbackInfoReturnable<Integer> cir) {
        RenderTarget f = pipcam$target();
        if (f != null) cir.setReturnValue(f.getColorTextureId());
    }

    @Inject(method = "getGlDepthTextureId", at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$depth(CallbackInfoReturnable<Integer> cir) {
        RenderTarget f = pipcam$target();
        if (f != null) cir.setReturnValue(f.getDepthTextureId());
    }

    /** DH fades its LODs out inside the vanilla render distance, assuming real chunks are there. Around the PiP
     * camera that is only true for the area actually loaded, so report that radius instead. */
    @Inject(method = "getRenderDistance", at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$renderDistance(CallbackInfoReturnable<Integer> cir) {
        if (pipcam$target() != null) {
            cir.setReturnValue(PipLevelRenderer.vanillaRadiusChunks);
        }
    }

    @Inject(method = "getTargetFramebufferViewportWidth", at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$w(CallbackInfoReturnable<Integer> cir) {
        RenderTarget f = pipcam$target();
        if (f != null) cir.setReturnValue(f.viewWidth);
    }

    @Inject(method = "getTargetFramebufferViewportHeight", at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$h(CallbackInfoReturnable<Integer> cir) {
        RenderTarget f = pipcam$target();
        if (f != null) cir.setReturnValue(f.viewHeight);
    }
}
