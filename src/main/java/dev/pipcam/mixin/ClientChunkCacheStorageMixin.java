package dev.pipcam.mixin;

import dev.pipcam.ChunkRetention;
import dev.pipcam.PipChunkStorage;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.atomic.AtomicReferenceArray;

@Mixin(targets = "net.minecraft.client.multiplayer.ClientChunkCache$Storage")
public abstract class ClientChunkCacheStorageMixin implements PipChunkStorage {
    @Shadow @Final AtomicReferenceArray<LevelChunk> chunks;

    @Shadow abstract int getIndex(int x, int z);

    /** Raw ring-buffer lookup that ignores the view-range check (validated by position). */
    @Override
    public LevelChunk pipcam$raw(int x, int z) {
        LevelChunk c = this.chunks.get(this.getIndex(x, z));
        return c != null && c.getPos().x == x && c.getPos().z == z ? c : null;
    }

    /** When Minecraft reuses a ring-buffer slot, don't unload a chunk we are retaining for the PiP. */
    @Redirect(method = "replace(ILnet/minecraft/world/level/chunk/LevelChunk;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;unload(Lnet/minecraft/world/level/chunk/LevelChunk;)V"))
    private void pipcam$evict(ClientLevel level, LevelChunk chunk) {
        if (!ChunkRetention.keepOnEvict(chunk)) level.unload(chunk);
    }
}
