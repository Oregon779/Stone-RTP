package dev.stonertp.plugin.listener;

import dev.stonertp.plugin.StoneRTP;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class GUIListener implements Listener {
    private final StoneRTP plugin;

    public GUIListener(StoneRTP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        plugin.getGuiManager().untrack(player.getUniqueId());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (plugin.getGuiManager().isOpenMenu(event.getWhoClicked(), event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }

    // Runs for every inventory click on the server, so the menu check is one map lookup instead of a title parse.
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!plugin.getGuiManager().isOpenMenu(event.getWhoClicked(), event.getView().getTopInventory())) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player) || !event.getView().getTopInventory().equals(event.getClickedInventory())) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        String worldName = clicked.getItemMeta().getPersistentDataContainer()
                .get(plugin.getGuiManager().getWorldKey(), PersistentDataType.STRING);
        if (worldName == null) {
            return;
        }

        String key = plugin.getConfigManager().getGuiKeyForWorld(worldName);
        Sound clickSound = key != null
                ? plugin.getConfigManager().getGuiClickSoundFor(key, Sound.UI_BUTTON_CLICK)
                : plugin.getConfigManager().getSound("gui.sound-click", Sound.UI_BUTTON_CLICK);
        player.playSound(player.getLocation(), clickSound, 1.0f, 1.0f);

        // Closing/opening inventories inside InventoryClickEvent is unsafe per the Bukkit API contract.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.closeInventory();
            plugin.getTeleportManager().startRTP(player, worldName);
        });
    }
}
