package dev.stonertp.plugin;

import dev.stonertp.plugin.model.TimeWindow;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneTest extends PluginTestBase {
    private PlayerMock player;

    @BeforeEach
    void placePlayerInZone() {
        plugin.getZoneManager().create("spawn", new Location(world, -5, 0, -5), new Location(world, 5, 300, 5), 3);
        player = addPlayer();
        player.setLocation(new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1, 0.5));
    }

    private boolean triggered(UUID id) {
        return plugin.getTeleportManager().isTeleporting(id) || !plugin.getZoneManager().getZone("spawn").contains(player.getLocation());
    }

    @Test
    void standingInsideTeleportsAfterInterval() {
        server.getScheduler().performTicks(20 * 2);
        assertFalse(triggered(player.getUniqueId()), "Too early");

        server.getScheduler().performTicks(20 * 3);
        assertTrue(triggered(player.getUniqueId()));
    }

    @Test
    void bypassPermissionIsNeverTeleported() {
        player.addAttachment(plugin, "stonertp.bypass.zone", true);
        server.getScheduler().performTicks(20 * 6);
        assertFalse(triggered(player.getUniqueId()));
    }

    @Test
    void blockedTimePausesZones() {
        LocalTime now = plugin.getBlockedTimeManager().now();
        plugin.getBlockedTimeManager().add(new TimeWindow(now.minusHours(1), now.plusHours(1)));

        server.getScheduler().performTicks(20 * 6);
        assertFalse(triggered(player.getUniqueId()));
    }

    @Test
    void leavingTheZoneResetsTheCountdown() {
        server.getScheduler().performTicks(20 * 2);
        player.setLocation(new Location(world, 50, player.getLocation().getY(), 50));
        server.getScheduler().performTicks(20);
        player.setLocation(new Location(world, 0.5, player.getLocation().getY(), 0.5));
        server.getScheduler().performTicks(20 * 2);

        assertFalse(plugin.getTeleportManager().isTeleporting(player.getUniqueId()), "Countdown should have restarted");
    }
}
