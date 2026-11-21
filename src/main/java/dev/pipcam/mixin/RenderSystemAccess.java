package dev.pipcam.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderSystem.class)
public interface RenderSystemAccess {
    @Accessor("shaderLightDirections")
    static Vector3f[] pipcam$getShaderLightDirections() {
        throw new AssertionError();
    }
}
