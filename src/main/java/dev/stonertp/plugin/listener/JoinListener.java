package dev.stonertp.plugin.listener;

import dev.stonertp.plugin.StoneRTP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {
    private final StoneRTP plugin;

    public JoinListener(StoneRTP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfigManager().isOnJoinEnabled()) {
            return;
        }
        Player player = event.getPlayer();
        boolean firstJoin = !player.hasPlayedBefore();
        if (plugin.getConfigManager().isOnJoinOnlyFirstJoin() && !firstJoin) {
            return;
        }

        String world = plugin.getConfigManager().getOnJoinWorld();
        long delay = plugin.getConfigManager().getOnJoinDelayTicks();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getTeleportManager().startRTP(player, world);
            }
        }, delay);
    }
}
