package dev.stonertp.plugin.listener;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.manager.ZoneManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class ZoneWandListener implements Listener {
    private final StoneRTP plugin;

    public ZoneWandListener(StoneRTP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ZoneManager zones = plugin.getZoneManager();
        Player player = event.getPlayer();
        if (!zones.isWand(event.getItem()) || !player.hasPermission("stonertp.admin")) {
            return;
        }
        event.setCancelled(true);

        Block block = event.getClickedBlock();
        if (block != null) {
            zones.setCorner(player, action == Action.LEFT_CLICK_BLOCK ? 1 : 2, block.getLocation());
        }
    }

    // Creative-mode left clicks break blocks instantly; make sure selecting never destroys the corner block.
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.getZoneManager().isWand(player.getInventory().getItemInMainHand()) && player.hasPermission("stonertp.admin")) {
            event.setCancelled(true);
        }
    }
}
