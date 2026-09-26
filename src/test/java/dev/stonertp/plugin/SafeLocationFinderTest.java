package dev.stonertp.plugin;

import dev.stonertp.plugin.model.RTPWorldSettings;
import dev.stonertp.plugin.model.SearchMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SafeLocationFinderTest extends PluginTestBase {
    private static final int CAVERN_FLOOR = 30;

    // Nether-like column: bedrock floor, netherrack, an open cavern, more netherrack, then the bedrock roof.
    private WorldMock buildRoofedWorld(String name, World.Environment environment) {
        WorldMock roofed = addWorld(name);
        roofed.setEnvironment(environment);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = roofed.getMinHeight(); y < roofed.getMaxHeight(); y++) {
                    roofed.getBlockAt(x, y, z).setType(materialAt(y));
                }
            }
        }
        return roofed;
    }

    private static Material materialAt(int y) {
        if (y <= 4) {
            return Material.BEDROCK;
        }
        if (y <= CAVERN_FLOOR) {
            return Material.NETHERRACK;
        }
        if (y <= 60) {
            return Material.AIR;
        }
        if (y <= 122) {
            return Material.NETHERRACK;
        }
        return y <= 127 ? Material.BEDROCK : Material.AIR;
    }

    private Location search(WorldMock target, SearchMode mode) {
        RTPWorldSettings settings = new RTPWorldSettings(target.getName(), true, 0, 0, 0, 2, mode);
        CompletableFuture<Location> result = plugin.getSafeLocationFinder().find(target, settings);
        server.getScheduler().performTicks(80);
        return result.getNow(null);
    }

    @Test
    void netherLandsOnCavernFloorInsteadOfRoof() {
        Location landing = search(buildRoofedWorld("world_nether", World.Environment.NETHER), SearchMode.AUTO);
        assertNotNull(landing);
        assertEquals(CAVERN_FLOOR + 1, landing.getBlockY());
    }

    @Test
    void undetectedRoofedDimensionWorksWithForcedCaveMode() {
        Location landing = search(buildRoofedWorld("custom_dim", World.Environment.CUSTOM), SearchMode.CAVE);
        assertNotNull(landing);
        assertEquals(CAVERN_FLOOR + 1, landing.getBlockY());
    }

    @Test
    void surfaceModeStillLandsOnTop() {
        WorldMock flat = addWorld("flat");
        Location landing = search(flat, SearchMode.AUTO);
        assertNotNull(landing);
        assertEquals(flat.getHighestBlockYAt(landing.getBlockX(), landing.getBlockZ()) + 1, landing.getBlockY());
    }
}
