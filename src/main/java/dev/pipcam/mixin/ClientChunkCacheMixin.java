package dev.pipcam.mixin;

import dev.pipcam.ChunkRetention;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(ClientChunkCache.class)
public class ClientChunkCacheMixin {
    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/LevelChunk;",
            at = @At("RETURN"), cancellable = true)
    private void pipcam$ghost(int x, int z, ChunkStatus status, boolean require, CallbackInfoReturnable<LevelChunk> cir) {
        LevelChunk r = cir.getReturnValue();
        if (r == null || r instanceof EmptyLevelChunk) {
            LevelChunk g = ChunkRetention.ghost(x, z);
            if (g != null) cir.setReturnValue(g);
        }
    }

    @Inject(method = "replaceWithPacketData", at = @At("HEAD"))
    private void pipcam$fresh(int x, int z, FriendlyByteBuf buf, CompoundTag tag,
                              Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> c,
                              CallbackInfoReturnable<LevelChunk> cir) {
        ChunkRetention.onFresh(x, z);
    }
}
