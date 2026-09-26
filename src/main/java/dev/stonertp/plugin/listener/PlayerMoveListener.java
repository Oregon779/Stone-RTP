package dev.stonertp.plugin.listener;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.manager.TeleportManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PlayerMoveListener implements Listener {
    private final StoneRTP plugin;

    public PlayerMoveListener(StoneRTP plugin) {
        this.plugin = plugin;
    }

    // Fires for every player many times per second; head rotation alone is filtered out before any lookup.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        checkMovedAway(event.getPlayer(), event.getTo());
    }

    // PlayerTeleportEvent has its own handler list, so /home, /spawn or portals never reached onMove.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        checkMovedAway(event.getPlayer(), event.getTo());
    }

    private void checkMovedAway(Player player, Location to) {
        TeleportManager teleports = plugin.getTeleportManager();
        if (!teleports.hasActiveWarmup(player.getUniqueId()) || !plugin.getConfigManager().isCancelOnMove()) {
            return;
        }
        Location start = teleports.getStartLocation(player.getUniqueId());
        if (start == null || to == null) {
            return;
        }
        boolean otherWorld = !start.getWorld().equals(to.getWorld());
        double threshold = plugin.getConfigManager().getMoveCancelThreshold();
        if (otherWorld || start.distanceSquared(to) >= threshold * threshold) {
            teleports.cancel(player, "rtp.cancelled-move");
        }
    }
}
