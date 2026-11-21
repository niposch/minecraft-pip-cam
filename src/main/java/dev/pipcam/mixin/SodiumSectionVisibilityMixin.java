package dev.pipcam.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.pipcam.PipLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Use Sodium's spatial section search for the detached camera on either side of a loaded boundary. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager", remap = false)
public class SodiumSectionVisibilityMixin {
    @Inject(method = "isOutOfGraph", at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$useSpatialSectionSearch(CallbackInfoReturnable<Boolean> cir) {
        // The graph search starts at the current section and can miss visible terrain across a gap.
        // The spatial search still limits the scan to the camera frustum and render distance.
        if (PipLevelRenderer.rendering && RenderSystem.isOnRenderThread()) cir.setReturnValue(true);
    }
}
