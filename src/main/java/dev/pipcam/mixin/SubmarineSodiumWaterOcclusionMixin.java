package dev.pipcam.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.pipcam.PipLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "com.maxenonyme.createsubmarine.submarine.client.renderer.SodiumWaterOcclusionBridge", remap = false)
public class SubmarineSodiumWaterOcclusionMixin {
    // Use the bridge's uniform reset without changing its shared pixel-perfect state or pose caches.
    @ModifyVariable(method = "applyToProgram(IZ)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private static boolean pipcam$disableWaterMask(boolean translucent) {
        return translucent && !(PipLevelRenderer.rendering && RenderSystem.isOnRenderThread());
    }
}
