package dev.stonertp.plugin;

import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiTest extends PluginTestBase {

    @Test
    void clicksInsideTheMenuAreCancelled() {
        PlayerMock player = addPlayer();
        player.performCommand("rtp");

        InventoryClickEvent event = player.simulateInventoryClick(player.getOpenInventory(), ClickType.LEFT, 0);
        assertTrue(event.isCancelled());
    }

    @Test
    void shiftClicksFromOwnInventoryWhileMenuIsOpenAreCancelled() {
        PlayerMock player = addPlayer();
        player.performCommand("rtp");

        InventoryClickEvent event = player.simulateInventoryClick(player.getOpenInventory(), ClickType.SHIFT_LEFT, 30);
        assertTrue(event.isCancelled(), "Otherwise items could be pushed into the menu");
    }

    @Test
    void otherInventoriesWithTheSameTitleAreLeftAlone() {
        PlayerMock player = addPlayer();
        Component title = plugin.getMessageManager().format(plugin.getMessageManager().getRaw("gui.title"), null);
        Inventory lookalike = server.createInventory(null, 27, title);
        player.openInventory(lookalike);

        InventoryClickEvent event = player.simulateInventoryClick(player.getOpenInventory(), ClickType.LEFT, 0);
        assertFalse(event.isCancelled(), "Another plugin's chest that happens to share the title must keep working");
    }

    @Test
    void clickingAWorldButtonStartsTheTeleport() {
        PlayerMock player = addPlayer();
        player.performCommand("rtp");

        player.simulateInventoryClick(player.getOpenInventory(), ClickType.LEFT, 11);
        server.getScheduler().performTicks(2);

        assertTrue(plugin.getTeleportManager().isTeleporting(player.getUniqueId()));
    }
}
