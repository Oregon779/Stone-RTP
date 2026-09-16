package dev.stonertp.plugin.listener;

import dev.stonertp.plugin.StoneRTP;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class PlayerMoveListener implements Listener {
    private final StoneRTP plugin;

    public PlayerMoveListener(StoneRTP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getTeleportManager().hasActiveWarmup(player.getUniqueId())) {
            return;
        }
        if (!plugin.getConfigManager().isCancelOnMove()) {
            return;
        }
        Location start = plugin.getTeleportManager().getStartLocation(player.getUniqueId());
        Location to = event.getTo();
        if (start == null || to == null || !start.getWorld().equals(to.getWorld())) {
            return;
        }
        double threshold = plugin.getConfigManager().getMoveCancelThreshold();
        if (start.distanceSquared(to) >= threshold * threshold) {
            plugin.getTeleportManager().cancel(player, "rtp.cancelled-move");
        }
    }
}
