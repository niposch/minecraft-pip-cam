package dev.pipcam;

import com.mojang.blaze3d.pipeline.RenderTarget;

/** Duck interface on {@link RenderTarget} that temporarily redirects rendering to an offscreen target. */
public interface PipRenderTarget {
    void pipcam$setRedirect(RenderTarget target);
}
