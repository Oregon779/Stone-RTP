package dev.stonertp.plugin;

import org.bukkit.Chunk;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.concurrent.CompletableFuture;

/** MockBukkit leaves async chunk loading unimplemented; Paper completes it on the main thread, as done here. */
public class AsyncChunkWorldMock extends WorldMock {

    @Override
    public CompletableFuture<Chunk> getChunkAtAsync(int x, int z, boolean generate, boolean urgent) {
        return CompletableFuture.completedFuture(getChunkAt(x, z));
    }
}
