package dev.pipcam.mixin;

import dev.pipcam.PipLevelRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the PiP renders, report the PiP camera as the "main" camera. Many renderers (Distant Horizons, Create,
 * Sable, culling mods) query this instead of the camera passed to renderLevel.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getMainCamera", at = @At("HEAD"), cancellable = true)
    private void pipcam$camera(CallbackInfoReturnable<Camera> cir) {
        // render thread only: async culling threads (Entity Culling, DH) must keep seeing the real camera
        if (PipLevelRenderer.rendering && com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) cir.setReturnValue(PipLevelRenderer.camera());
    }
}
