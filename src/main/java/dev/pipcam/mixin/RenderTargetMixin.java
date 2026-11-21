package dev.pipcam.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.pipcam.PipRenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Temporarily redirects Minecraft's main {@link RenderTarget} fields to the PiP's offscreen {@link RenderTarget}
 * so vanilla, Sodium, Iris, and other renderers draw directly into the PiP without requiring Veil.
 */
@Mixin(RenderTarget.class)
public abstract class RenderTargetMixin implements PipRenderTarget {
    @Shadow public int width;
    @Shadow public int height;
    @Shadow public int viewWidth;
    @Shadow public int viewHeight;
    @Shadow public int frameBufferId;
    @Shadow protected int colorTextureId;
    @Shadow protected int depthBufferId;

    @Unique private boolean pipcam$redirected;
    @Unique private int pipcam$savedWidth;
    @Unique private int pipcam$savedHeight;
    @Unique private int pipcam$savedViewWidth;
    @Unique private int pipcam$savedViewHeight;
    @Unique private int pipcam$savedFboId;
    @Unique private int pipcam$savedColorId;
    @Unique private int pipcam$savedDepthId;

    @Override
    public void pipcam$setRedirect(RenderTarget target) {
        if (target != null) {
            if (!pipcam$redirected) {
                pipcam$savedWidth = this.width;
                pipcam$savedHeight = this.height;
                pipcam$savedViewWidth = this.viewWidth;
                pipcam$savedViewHeight = this.viewHeight;
                pipcam$savedFboId = this.frameBufferId;
                pipcam$savedColorId = this.colorTextureId;
                pipcam$savedDepthId = this.depthBufferId;
                pipcam$redirected = true;
            }
            this.width = target.width;
            this.height = target.height;
            this.viewWidth = target.viewWidth;
            this.viewHeight = target.viewHeight;
            this.frameBufferId = target.frameBufferId;
            this.colorTextureId = target.getColorTextureId();
            this.depthBufferId = target.getDepthTextureId();
        } else if (pipcam$redirected) {
            pipcam$restore();
        }
    }

    @Unique
    private void pipcam$restore() {
        this.width = pipcam$savedWidth;
        this.height = pipcam$savedHeight;
        this.viewWidth = pipcam$savedViewWidth;
        this.viewHeight = pipcam$savedViewHeight;
        this.frameBufferId = pipcam$savedFboId;
        this.colorTextureId = pipcam$savedColorId;
        this.depthBufferId = pipcam$savedDepthId;
        pipcam$redirected = false;
    }

    @Inject(method = "destroyBuffers", at = @At("HEAD"))
    private void pipcam$beforeDestroy(CallbackInfo ci) {
        if (pipcam$redirected) pipcam$restore();
    }

    @Inject(method = "createBuffers", at = @At("HEAD"))
    private void pipcam$beforeCreate(int width, int height, boolean clearError, CallbackInfo ci) {
        if (pipcam$redirected) pipcam$restore();
    }
}
