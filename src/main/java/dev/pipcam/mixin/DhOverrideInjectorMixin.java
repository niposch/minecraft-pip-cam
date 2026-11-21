package dev.pipcam.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.pipcam.PipCamMod;
import dev.pipcam.PipLevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/**
 * With a shader pack active, Iris registers its own Distant Horizons framebuffer and shader program through DH's
 * override API. If the PiP render reaches them, DH re-attaches the PiP's depth texture to Iris's framebuffer, which
 * then breaks the shaded main view (LOD terrain smeared over the sky) even after the PiP is turned off. While the PiP
 * renders, DH gets only its own built-in ("core") rendering overrides, never third-party ones such as Iris's.
 */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.coreapi.DependencyInjection.OverrideInjector", remap = false)
public class DhOverrideInjectorMixin {
    @Unique private static final String RENDER_OVERRIDES = "com.seibel.distanthorizons.api.interfaces.override.rendering.";
    @Unique private static final int CORE_PRIORITY = -1;
    @Unique private static Method pipcam$lookupByPriority;
    @Unique private static boolean pipcam$lookupWarned;

    @Inject(method = "get(Ljava/lang/Class;)Lcom/seibel/distanthorizons/api/interfaces/override/IDhApiOverrideable;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$get(Class<?> type, CallbackInfoReturnable<Object> cir) {
        if (pipcam$isPipRenderOverride(type)) cir.setReturnValue(pipcam$coreOverride(type));
    }

    @Inject(method = "get(Ljava/lang/Class;I)Lcom/seibel/distanthorizons/api/interfaces/override/IDhApiOverrideable;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void pipcam$getPriority(Class<?> type, int priority, CallbackInfoReturnable<Object> cir) {
        if (priority != CORE_PRIORITY && pipcam$isPipRenderOverride(type)) {
            cir.setReturnValue(pipcam$coreOverride(type));
        }
    }

    @Unique
    private static boolean pipcam$isPipRenderOverride(Class<?> type) {
        return PipLevelRenderer.rendering && RenderSystem.isOnRenderThread() && type.getName().startsWith(RENDER_OVERRIDES);
    }

    @Unique
    private Object pipcam$coreOverride(Class<?> type) {
        try {
            if (pipcam$lookupByPriority == null) {
                pipcam$lookupByPriority = this.getClass().getMethod("get", Class.class, int.class);
            }
            // Iris can leave an empty list after unbinding. DH's priority lookup handles that case.
            return pipcam$lookupByPriority.invoke(this, type, CORE_PRIORITY);
        } catch (ReflectiveOperationException e) {
            if (!pipcam$lookupWarned) {
                pipcam$lookupWarned = true;
                PipCamMod.LOGGER.warn("Could not look up a core Distant Horizons override for the PiP", e);
            }
            return null;
        }
    }
}
