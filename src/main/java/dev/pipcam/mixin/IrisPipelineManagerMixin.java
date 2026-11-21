package dev.pipcam.mixin;

import dev.pipcam.IrisBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * While the PiP is rendering, Iris is told to use its plain vanilla pipeline.
 * The saved Iris setting and the main view's shader pipeline are never touched.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.PipelineManager", remap = false)
public class IrisPipelineManagerMixin {
    @Inject(method = "getPipelineNullable", at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$nullable(CallbackInfoReturnable<Object> cir) {
        if (IrisBridge.forceVanilla()) {
            Object p = IrisBridge.vanillaPipeline();
            if (p != null) cir.setReturnValue(p);
        }
    }

    @Inject(method = "preparePipeline", at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$prepare(@Coerce Object dim, CallbackInfoReturnable<Object> cir) {
        if (IrisBridge.forceVanilla()) {
            Object p = IrisBridge.vanillaPipeline();
            if (p != null) cir.setReturnValue(p);
        }
    }

    @Inject(method = "getPipeline", at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$optional(CallbackInfoReturnable<Optional<Object>> cir) {
        if (IrisBridge.forceVanilla()) {
            Object p = IrisBridge.vanillaPipeline();
            if (p != null) cir.setReturnValue(Optional.of(p));
        }
    }
}
