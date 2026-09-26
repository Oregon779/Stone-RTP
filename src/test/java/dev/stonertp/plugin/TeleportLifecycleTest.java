package dev.stonertp.plugin;

import dev.stonertp.plugin.manager.TeleportManager;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportLifecycleTest extends PluginTestBase {
    private FakeEconomy economy;

    private TeleportManager teleports() {
        return plugin.getTeleportManager();
    }

    private void enableCost(double amount) {
        economy = FakeEconomy.install(server);
        setConfig(Map.of("cost.enabled", true, "cost.amount", amount));
    }

    @Test
    void cancellingWarmupRefundsCost() {
        enableCost(100);
        PlayerMock player = addPlayer();
        economy.setBalance(player, 500);

        teleports().startRTP(player, "world");
        assertEquals(400, economy.balance(player), 0.001);

        player.performCommand("rtp cancel");
        assertEquals(500, economy.balance(player), 0.001);
    }

    @Test
    void quittingDuringWarmupRefundsCost() {
        enableCost(100);
        PlayerMock player = addPlayer();
        economy.setBalance(player, 500);

        teleports().startRTP(player, "world");
        assertTrue(teleports().hasActiveWarmup(player.getUniqueId()));
        player.disconnect();

        assertEquals(500, economy.balance(player), 0.001, "Money paid for a teleport that never happened must come back");
    }

    @Test
    void declinedWithdrawalNeitherTeleportsNorPaysOut() {
        enableCost(100);
        PlayerMock player = addPlayer();
        economy.setBalance(player, 500);
        economy.setFailWithdrawals(true);

        teleports().startRTP(player, "world");
        player.performCommand("rtp cancel");

        assertFalse(teleports().isTeleporting(player.getUniqueId()));
        assertEquals(500, economy.balance(player), 0.001, "A refund for money never taken would print money");
    }

    @Test
    void forcedRtpDoesNotChargeTheTarget() {
        enableCost(100);
        PlayerMock admin = addPlayer();
        admin.setOp(true);
        PlayerMock target = addPlayer();
        economy.setBalance(target, 500);

        admin.performCommand("rtp player " + target.getName());

        assertTrue(teleports().isTeleporting(target.getUniqueId()));
        assertEquals(500, economy.balance(target), 0.001);
    }

    @Test
    void forcedRtpIgnoresTargetCooldownAndWarmup() {
        PlayerMock admin = addPlayer();
        admin.setOp(true);
        PlayerMock target = addPlayer();
        plugin.getCooldownManager().markTeleported(target);

        admin.performCommand("rtp player " + target.getName());

        assertTrue(teleports().isTeleporting(target.getUniqueId()), "An admin's forced RTP must not be blocked by the target's cooldown");
        assertFalse(teleports().hasActiveWarmup(target.getUniqueId()), "The target must not be able to walk away from a forced RTP");
    }

    @Test
    void forcedRtpErrorsAreReportedToTheAdmin() {
        PlayerMock admin = addPlayer();
        admin.setOp(true);
        PlayerMock target = addPlayer();

        admin.performCommand("rtp player " + target.getName() + " no_such_world");

        List<String> adminMessages = drainMessages(admin);
        assertTrue(anyContains(adminMessages, "doesn't exist"), "Admin messages: " + adminMessages);
        assertFalse(anyContains(adminMessages, "Randomly teleporting"), "No success message for a teleport that didn't start");
        assertFalse(anyContains(drainMessages(target), "doesn't exist"));
    }

    @Test
    void bossBarCountdownReappearsAfterRelog() {
        setConfig("notification.type", "BOSSBAR");
        PlayerMock player = addPlayer();

        teleports().startRTP(player, "world");
        List<BossBar> firstBars = bars(player);
        assertEquals(1, firstBars.size());

        player.disconnect();
        player.reconnect();
        teleports().startRTP(player, "world");

        assertTrue(bars(player).stream().anyMatch(bar -> bar != firstBars.getFirst()),
                "The countdown bar must be shown again after rejoining, got " + bars(player));
    }

    private static List<BossBar> bars(PlayerMock player) {
        List<BossBar> result = new ArrayList<>();
        player.activeBossBars().forEach(result::add);
        return result;
    }

    @Test
    void damageCancelledByAnotherPluginDoesNotCancelWarmup() {
        PlayerMock player = addPlayer();
        teleports().startRTP(player, "world");

        EntityDamageEvent event = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.CONTACT,
                DamageSource.builder(DamageType.CACTUS).build(), 1.0);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);

        assertTrue(teleports().hasActiveWarmup(player.getUniqueId()), "No damage was actually taken");
    }

    @Test
    void realDamageCancelsWarmup() {
        PlayerMock player = addPlayer();
        teleports().startRTP(player, "world");

        server.getPluginManager().callEvent(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.CONTACT,
                DamageSource.builder(DamageType.CACTUS).build(), 1.0));

        assertFalse(teleports().hasActiveWarmup(player.getUniqueId()));
    }

    @Test
    void teleportingElsewhereDuringWarmupCancelsIt() {
        WorldMock other = addWorld("other");
        PlayerMock player = addPlayer();
        teleports().startRTP(player, "world");

        player.teleport(new Location(other, 0, 70, 0));

        assertFalse(teleports().hasActiveWarmup(player.getUniqueId()), "Leaving via /home, /spawn, portals... counts as moving");
    }

    @Test
    void cancelWithoutRunningTeleportGivesFeedback() {
        PlayerMock player = addPlayer();
        player.performCommand("rtp cancel");
        assertFalse(drainMessages(player).isEmpty(), "/rtp cancel must not silently do nothing");
    }
}
