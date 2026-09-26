package dev.stonertp.plugin;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarmupFlowTest extends PluginTestBase {

    private PlayerMock playerAt(double x, double z) {
        PlayerMock player = addPlayer();
        player.setLocation(new Location(world, x, world.getHighestBlockYAt((int) x, (int) z) + 1, z));
        return player;
    }

    @Test
    void fullWarmupEndsInTeleportWithCooldown() {
        PlayerMock player = playerAt(0.5, 0.5);
        Location start = player.getLocation().clone();

        plugin.getTeleportManager().startRTP(player, "world");
        server.getScheduler().performTicks(20 * 7);

        assertFalse(plugin.getTeleportManager().isTeleporting(player.getUniqueId()));
        assertTrue(player.getLocation().distance(start) >= 99, "Default radius-min is 100");
        assertTrue(anyContains(drainMessages(player), "Teleported to"));
        assertTrue(plugin.getCooldownManager().isOnCooldown(player));

        Location back = plugin.getBackLocationManager().get(player);
        assertEquals(start.getBlockX(), back.getBlockX(), "/back must lead to where the RTP started");
        assertEquals(start.getBlockZ(), back.getBlockZ());
    }

    @Test
    void countdownParticlesReachOnlyPlayersWithin32Blocks() {
        AsyncTeleportPlayerMock teleporting = (AsyncTeleportPlayerMock) playerAt(0.5, 0.5);
        AsyncTeleportPlayerMock bystander = (AsyncTeleportPlayerMock) playerAt(20.5, 0.5);
        AsyncTeleportPlayerMock farAway = (AsyncTeleportPlayerMock) playerAt(200.5, 0.5);

        plugin.getTeleportManager().startRTP(teleporting, "world");
        server.getScheduler().performTicks(20);

        assertTrue(teleporting.getParticlesReceived() > 0);
        assertEquals(teleporting.getParticlesReceived(), bystander.getParticlesReceived(), "Bystanders see the same show");
        assertEquals(0, farAway.getParticlesReceived());
    }

    @Test
    void shuttingDownMidWarmupRefundsAndRemovesBossBar() {
        FakeEconomy economy = FakeEconomy.install(server);
        setConfig(Map.of("cost.enabled", true, "cost.amount", 100.0, "notification.type", "BOSSBAR"));
        PlayerMock player = playerAt(0.5, 0.5);
        economy.setBalance(player, 500);

        plugin.getTeleportManager().startRTP(player, "world");
        assertEquals(400, economy.balance(player), 0.001);

        server.getPluginManager().disablePlugin(plugin);

        assertEquals(500, economy.balance(player), 0.001);
        assertFalse(player.activeBossBars().iterator().hasNext(), "A stuck countdown bar would stay on screen forever");
    }

    @Test
    void playerTypedTagsAreShownLiterallyNotInterpreted() {
        PlayerMock player = playerAt(0.5, 0.5);
        player.performCommand("rtp <red><click:run_command:'/op'>fake");

        List<String> messages = drainMessages(player);
        assertTrue(anyContains(messages, "<red><click:run_command:'/op'>fake"), "Got: " + messages);
    }
}
