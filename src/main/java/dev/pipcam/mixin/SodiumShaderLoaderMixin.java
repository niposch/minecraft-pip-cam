package dev.pipcam.mixin;

import dev.pipcam.IrisBridge;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Patches Sodium's plain chunk shaders when an Iris shader pack is active.
 *
 * <p>While a shader pack is active, the main view renders with the pack's own shaders and Sodium's plain chunk
 * programs are used ONLY by the PiP. Because Iris builds chunk meshes in its extended shader-pack format (without
 * vanilla alpha-cutoff bits, optionally with AO packed in vertex alpha, and without baked directional face shading),
 * we patch the plain Sodium shader to:
 * <ol>
 *   <li>Use Minecraft's fixed 0.5 cutout threshold so leaves, grass and plants cut out cleanly.</li>
 *   <li>Apply separate vertex-alpha AO when Iris packs AO into alpha.</li>
 *   <li>Reconstruct exact per-triangle world-space face normals via {@code dFdx/dFdy} and apply vanilla directional
 *       shading directly on terrain fragments — leaving DH LODs, entities, sky and water cleanly untouched.</li>
 * </ol>
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public class SodiumShaderLoaderMixin {
    private static final String SHADE_SNIPPET = """
            color *= v_Color;
                {
                    vec3 _pipC = cross(dFdx(v_PipWorldPos), dFdy(v_PipWorldPos));
                    if (dot(_pipC, _pipC) > 1e-12) {
                        vec3 _pipW = normalize(_pipC);
                        vec3 _pipA = _pipW * _pipW;
                        float _pipUp = _pipW.y > 0.0 ? 1.0 : 0.5;
                        color.rgb *= clamp(_pipA.x * 0.6 + _pipA.y * _pipUp + _pipA.z * 0.8, 0.5, 1.0);
                    }
                }""";

    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, require = 0)
    private static void pipcam$patch(ResourceLocation name, CallbackInfoReturnable<String> cir) {
        if (!IrisBridge.isPresent()) return;
        boolean ext = IrisBridge.extendedChunkFormat();
        boolean shade = IrisBridge.isPackInUse() && IrisBridge.meshesUnshaded();
        if (!ext && !shade) return;

        String path = name.getPath(), src = cir.getReturnValue();
        boolean changed = false;

        if (ext && path.endsWith("chunk_material.glsl")) {
            src = src.replace("float _material_alpha_cutoff(uint material) {",
                    "float _material_alpha_cutoff(uint material) {\n    return 0.5;");
            src = src.replace("bool _material_use_mips(uint material) {",
                    "bool _material_use_mips(uint material) {\n    return true;");
            changed = true;
        } else if (path.endsWith("block_layer_opaque.fsh")) {
            if (ext) {
                src = src.replace("#ifdef USE_FRAGMENT_DISCARD", "#if 1");
                changed = true;
            }
            if (shade && src.contains("in vec4 v_Color;") && src.contains("color *= v_Color;")) {
                src = src.replace("in vec4 v_Color;", "in vec4 v_Color;\nin vec3 v_PipWorldPos;");
                src = src.replace("color *= v_Color;", SHADE_SNIPPET);
                changed = true;
            }
        } else if (path.endsWith("block_layer_opaque.vsh")) {
            if (ext && IrisBridge.usesSeparateAo()) {
                src = src.replace("_vert_color * texture(u_LightTex",
                        "vec4(_vert_color.rgb * _vert_color.a, 1.0) * texture(u_LightTex");
                changed = true;
            }
            if (shade && src.contains("out vec4 v_Color;") && src.contains("vec3 position = _vert_position + translation;")) {
                src = src.replace("out vec4 v_Color;", "out vec4 v_Color;\nout vec3 v_PipWorldPos;");
                src = src.replace("vec3 position = _vert_position + translation;",
                        "vec3 position = _vert_position + translation;\n    v_PipWorldPos = position;");
                changed = true;
            }
        }

        if (changed) cir.setReturnValue(src);
    }
}
