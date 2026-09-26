package dev.stonertp.plugin.listener;

import dev.stonertp.plugin.StoneRTP;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class PlayerDamageListener implements Listener {
    private final StoneRTP plugin;

    public PlayerDamageListener(StoneRTP plugin) {
        this.plugin = plugin;
    }

    // MONITOR + ignoreCancelled: damage that spawn protection/god mode already cancelled wasn't taken.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.getTeleportManager().hasActiveWarmup(player.getUniqueId())) {
            return;
        }
        if (!plugin.getConfigManager().isCancelOnDamage()) {
            return;
        }
        plugin.getTeleportManager().cancel(player, "rtp.cancelled-damage");
    }
}
