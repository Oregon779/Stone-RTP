package dev.stonertp.plugin;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** MockBukkit leaves teleportAsync unimplemented; resolve it with a normal (event-firing) teleport. Also counts particles received. */
public class AsyncTeleportPlayerMock extends PlayerMock {
    private int particlesReceived;

    public AsyncTeleportPlayerMock(ServerMock server, String name) {
        super(server, name, UUID.randomUUID());
    }

    @Override
    public CompletableFuture<Boolean> teleportAsync(Location location, PlayerTeleportEvent.TeleportCause cause, TeleportFlag... flags) {
        return CompletableFuture.completedFuture(teleport(location, cause));
    }

    @Override
    public void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        particlesReceived++;
    }

    @Override
    public <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ,
                                  double extra, T data) {
        particlesReceived++;
    }

    public int getParticlesReceived() {
        return particlesReceived;
    }
}
