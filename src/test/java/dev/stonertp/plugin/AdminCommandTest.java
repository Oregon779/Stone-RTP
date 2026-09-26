package dev.stonertp.plugin;

import dev.stonertp.plugin.model.TimeWindow;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminCommandTest extends PluginTestBase {
    private PlayerMock admin;

    @BeforeEach
    void createAdmin() {
        admin = addPlayer();
        admin.setOp(true);
    }

    @Test
    void blocktimeAddStoresWindow() {
        admin.performCommand("stonertp blocktime add 14:00 18:00");
        assertEquals(List.of(TimeWindow.parse("14:00-18:00")), plugin.getBlockedTimeManager().getWindows());
        assertEquals(List.of("14:00-18:00"), plugin.getConfigManager().getBlockedTimePeriods());
    }

    @Test
    void blocktimeAddAcceptsSingleRangeArgument() {
        admin.performCommand("stonertp blocktime add 22-2");
        assertEquals(List.of(TimeWindow.parse("22:00-02:00")), plugin.getBlockedTimeManager().getWindows());
    }

    @Test
    void blocktimeRejectsGarbage() {
        admin.performCommand("stonertp blocktime add 25:00 18:00");
        admin.performCommand("stonertp blocktime add 14:00 14:00");
        admin.performCommand("stonertp blocktime add " + "9".repeat(300) + " 18:00");
        assertTrue(plugin.getBlockedTimeManager().getWindows().isEmpty());
    }

    @Test
    void blocktimeRemoveOutOfRangeIsHarmless() {
        admin.performCommand("stonertp blocktime add 14:00 18:00");
        admin.performCommand("stonertp blocktime remove 5");
        admin.performCommand("stonertp blocktime remove -1");
        admin.performCommand("stonertp blocktime remove abc");
        assertEquals(1, plugin.getBlockedTimeManager().getWindows().size());
    }

    @Test
    void toggleWithUnknownStateDoesNotSilentlyDisable() {
        admin.performCommand("stonertp toggle end maybe");
        assertTrue(plugin.getConfigManager().getWorldSettings("world_the_end").enabled(),
                "'maybe' is neither on nor off and must not be treated as off");
    }

    @Test
    void toggleOnOffWorks() {
        admin.performCommand("stonertp toggle end off");
        assertFalse(plugin.getConfigManager().getWorldSettings("world_the_end").enabled());
        admin.performCommand("stonertp toggle end on");
        assertTrue(plugin.getConfigManager().getWorldSettings("world_the_end").enabled());
    }

    @Test
    void zoneCreateValidatesNameAndSelection() {
        admin.performCommand("stonertp zone create spawn");
        assertTrue(anyContains(drainMessages(admin), "corner"), "No selection yet");

        admin.performCommand("stonertp zone create " + "x".repeat(40));
        admin.performCommand("stonertp zone create bad.name");
        admin.performCommand("stonertp zone create <red>evil");
        assertTrue(plugin.getZoneManager().getZones().isEmpty());
    }

    @Test
    void wandSelectsCornersAndCreatesZone() {
        admin.performCommand("stonertp zone wand");
        ItemStack wand = findWand(admin);
        assertNotNull(wand, "Admin should have received the zone wand");

        click(admin, wand, Action.LEFT_CLICK_BLOCK, world.getBlockAt(0, 60, 0));
        click(admin, wand, Action.RIGHT_CLICK_BLOCK, world.getBlockAt(4, 64, 6));
        admin.performCommand("stonertp zone create spawn 7");

        var zone = plugin.getZoneManager().getZone("spawn");
        assertNotNull(zone);
        assertEquals(5, zone.sizeX());
        assertEquals(5, zone.sizeY());
        assertEquals(7, zone.sizeZ());
        assertEquals(7, zone.intervalSeconds());
    }

    @Test
    void wandClickIsCancelledSoCornerBlockSurvives() {
        admin.performCommand("stonertp zone wand");
        ItemStack wand = findWand(admin);
        PlayerInteractEvent event = click(admin, wand, Action.LEFT_CLICK_BLOCK, world.getBlockAt(1, 60, 1));
        assertTrue(event.isCancelled());
    }

    @Test
    void zoneDeleteRemovesZone() {
        admin.performCommand("stonertp zone wand");
        ItemStack wand = findWand(admin);
        click(admin, wand, Action.LEFT_CLICK_BLOCK, world.getBlockAt(0, 60, 0));
        click(admin, wand, Action.RIGHT_CLICK_BLOCK, world.getBlockAt(2, 62, 2));
        admin.performCommand("stonertp zone create spawn");
        admin.performCommand("stonertp zone delete SPAWN");
        assertNull(plugin.getZoneManager().getZone("spawn"));
    }

    @Test
    void wandIsDroppedWhenInventoryIsFull() {
        for (int slot = 0; slot < admin.getInventory().getSize(); slot++) {
            admin.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        }
        admin.performCommand("stonertp zone wand");
        boolean dropped = world.getEntities().stream().anyMatch(entity -> entity instanceof org.bukkit.entity.Item item
                && plugin.getZoneManager().isWand(item.getItemStack()));
        assertTrue(dropped, "With a full inventory the wand must not just vanish");
    }

    private ItemStack findWand(PlayerMock player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (plugin.getZoneManager().isWand(item)) {
                return item;
            }
        }
        return null;
    }

    private PlayerInteractEvent click(PlayerMock player, ItemStack item, Action action, Block block) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, action, item, block, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event;
    }
}
