package dev.stonertp.plugin;

import dev.stonertp.plugin.manager.CooldownManager;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownGroupsTest extends PluginTestBase {

    @Test
    void vipGroupPermissionShortensCooldown() {
        PlayerMock player = addPlayer();
        player.addAttachment(plugin, "stonertp.cooldown.vip", true);

        CooldownManager cooldowns = plugin.getCooldownManager();
        cooldowns.markTeleported(player);

        long remaining = cooldowns.getRemainingSeconds(player);
        assertTrue(remaining >= 14 && remaining <= 15, "VIP should get the 15s group cooldown, got " + remaining + "s");
    }

    @Test
    void lowestMatchingGroupWins() {
        PlayerMock player = addPlayer();
        player.addAttachment(plugin, "stonertp.cooldown.vip", true);
        player.addAttachment(plugin, "stonertp.cooldown.mvp", true);

        plugin.getCooldownManager().markTeleported(player);

        long remaining = plugin.getCooldownManager().getRemainingSeconds(player);
        assertTrue(remaining >= 4 && remaining <= 5, "MVP (5s) should beat VIP (15s), got " + remaining + "s");
    }

    @Test
    void playerWithoutGroupGetsDefaultCooldown() {
        PlayerMock player = addPlayer();
        plugin.getCooldownManager().markTeleported(player);

        long remaining = plugin.getCooldownManager().getRemainingSeconds(player);
        assertTrue(remaining >= 29 && remaining <= 30, "Default cooldown is 30s, got " + remaining + "s");
    }
}
