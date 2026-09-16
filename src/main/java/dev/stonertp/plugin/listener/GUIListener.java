package dev.stonertp.plugin.listener;

import dev.stonertp.plugin.StoneRTP;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
    public void onClick(InventoryClickEvent event) {
        Component title = event.getView().title();
        String expectedTitle = plugin.getMessageManager().getRaw("gui.title");
        if (!title.equals(plugin.getMessageManager().format(expectedTitle, null))) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }
        ItemMeta meta = clicked.getItemMeta();
        String worldName = meta.getPersistentDataContainer().get(plugin.getGuiManager().getWorldKey(), PersistentDataType.STRING);
        if (worldName == null) {
            return;
        }

        String key = plugin.getConfigManager().getGuiKeyForWorld(worldName);
        org.bukkit.Sound clickSound = key != null
                ? plugin.getConfigManager().getGuiClickSoundFor(key, org.bukkit.Sound.UI_BUTTON_CLICK)
                : plugin.getConfigManager().getSound("gui.sound-click", org.bukkit.Sound.UI_BUTTON_CLICK);
        player.playSound(player.getLocation(), clickSound, 1.0f, 1.0f);

        player.closeInventory();
        plugin.getTeleportManager().startRTP(player, worldName);
    }
}
