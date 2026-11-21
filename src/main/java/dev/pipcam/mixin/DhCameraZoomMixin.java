package dev.pipcam.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.pipcam.PipLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the terrain builder's shared zoom based on the player view. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.core.render.CameraZoom", remap = false)
public class DhCameraZoomMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$keepPlayerZoom(CallbackInfo ci) {
        if (PipLevelRenderer.rendering && RenderSystem.isOnRenderThread()) ci.cancel();
    }
}
