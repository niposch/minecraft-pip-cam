package dev.pipcam.mixin;

import dev.pipcam.FlywheelBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * When Colorwheel is active (Iris shaders on), report visualization as unsupported during the unshaded PiP pass
 * so Colorwheel stays untouched and Create/Flywheel block entities and contraptions fall back to their vanilla renderers.
 */
@Pseudo
@Mixin(targets = "dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl", remap = false)
public class FlywheelVisualizationMixin {
    @Inject(method = "supportsVisualization", at = @At("HEAD"), cancellable = true, require = 0)
    private static void pipcam$fallbackVanilla(CallbackInfoReturnable<Boolean> cir) {
        if (FlywheelBridge.useVanillaFallback()) {
            cir.setReturnValue(false);
        }
    }
}
