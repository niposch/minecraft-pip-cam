package dev.pipcam.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.pipcam.PipLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep PiP texture sizes out of Iris's shared dimensions for the main-view DH depth copy. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.compat.dh.LodRendererEvents$6", remap = false)
public class IrisDhTextureResizeMixin {
    @Inject(method = "onResize(Lcom/seibel/distanthorizons/api/methods/events/sharedParameterObjects/DhApiEventParam;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$skipPipResize(CallbackInfo ci) {
        if (PipLevelRenderer.rendering && RenderSystem.isOnRenderThread()) ci.cancel();
    }
}
