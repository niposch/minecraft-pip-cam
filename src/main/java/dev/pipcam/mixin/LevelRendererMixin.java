package dev.pipcam.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.pipcam.PipLevelRenderer;
import dev.pipcam.PipState;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optionally suppresses rain/snow and clouds inside the PiP for a clearer, cheaper preview. */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void pipcam$skipWeather(CallbackInfo ci) {
        if (!PipState.INSTANCE.renderWeather && PipLevelRenderer.rendering && RenderSystem.isOnRenderThread()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void pipcam$skipClouds(CallbackInfo ci) {
        if (!PipState.INSTANCE.renderClouds && PipLevelRenderer.rendering && RenderSystem.isOnRenderThread()) {
            ci.cancel();
        }
    }
}
