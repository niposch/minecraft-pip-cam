package dev.pipcam.mixin;

import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightTexture.class)
public interface LightTextureAccess {
    @Accessor("updateLightTexture")
    void pipcam$setUpdateLightTexture(boolean value);
}
