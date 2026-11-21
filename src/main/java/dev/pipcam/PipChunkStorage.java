package dev.pipcam;

import net.minecraft.world.level.chunk.LevelChunk;

/** Duck interface implemented on ClientChunkCache.Storage by a mixin. */
public interface PipChunkStorage {
    LevelChunk pipcam$raw(int x, int z);
}
