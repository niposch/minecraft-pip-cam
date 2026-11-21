package dev.pipcam.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.pipcam.PipLevelRenderer;
import dev.pipcam.PipState;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PiP lighting option: while (only) the PiP renders, pretend it is noon or midnight. Drives sky, sun and light. */
@Mixin(DimensionType.class)
public class DimensionTypeMixin {
    @Inject(method = "timeOfDay", at = @At("HEAD"), cancellable = true)
    private void pipcam$time(long dayTime, CallbackInfoReturnable<Float> cir) {
        int mode = PipState.INSTANCE.lighting;
        if (mode != PipState.LIGHT_AUTO && PipLevelRenderer.rendering && RenderSystem.isOnRenderThread()) {
            cir.setReturnValue(mode == PipState.LIGHT_DAY ? 0.0f : 0.5f);
        }
    }
}
