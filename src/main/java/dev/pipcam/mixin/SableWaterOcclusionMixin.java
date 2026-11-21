package dev.pipcam.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.pipcam.PipLevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sable's shared water masks and their world state belong to the main view. */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer", remap = false)
public class SableWaterOcclusionMixin {
    @Inject(method = "preRenderTranslucent", at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$skipWaterMask(CallbackInfo ci) {
        if (PipLevelRenderer.rendering && RenderSystem.isOnRenderThread()) ci.cancel();
    }

    @Inject(method = "setupTranslucentShader", at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$disableWaterMask(ShaderInstance shader, CallbackInfo ci) {
        if (!PipLevelRenderer.rendering || !RenderSystem.isOnRenderThread()) return;
        var enabled = shader == null ? null : shader.getUniform("SableWaterOcclusionEnabled");
        if (enabled != null) enabled.set(0f);
        ci.cancel();
    }
}
