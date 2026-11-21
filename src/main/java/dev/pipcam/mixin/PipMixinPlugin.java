package dev.pipcam.mixin;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Skips optional compatibility mixins when their target mod is not installed, keeping logs clean. */
public final class PipMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String name = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (name.startsWith("Dh")) return hasMod("distanthorizons");
        if (name.equals("IrisDhTextureResizeMixin")) return hasMod("iris") && hasMod("distanthorizons");
        if (name.startsWith("Iris")) return hasMod("iris");
        if (name.startsWith("Sodium")) return hasMod("sodium");
        if (name.startsWith("Sable")) return hasMod("sable");
        if (name.startsWith("Submarine")) return hasMod("create_submarine");
        if (name.startsWith("Flywheel")) return hasMod("flywheel") && hasMod("colorwheel");
        return true;
    }

    private static boolean hasMod(String modId) {
        return FMLLoader.getLoadingModList().getModFileById(modId) != null;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
