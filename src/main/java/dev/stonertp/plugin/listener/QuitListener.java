package dev.stonertp.plugin.listener;

import dev.stonertp.plugin.StoneRTP;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuitListener implements Listener {
    private final StoneRTP plugin;

    public QuitListener(StoneRTP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getTeleportManager().cancelOnQuit(player.getUniqueId());
        plugin.getEffectManager().clearRotation(player);
        plugin.getGuiManager().untrack(player.getUniqueId());
        plugin.getZoneManager().forget(player.getUniqueId());
    }
}
