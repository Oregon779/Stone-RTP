package dev.stonertp.plugin;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSafetyTest extends PluginTestBase {

    private static final String BROKEN_YAML = "language: en\nworlds:\n  world:\n    enabled: true\n   radius-max: [oops\n";

    @Test
    void configWithSyntaxErrorIsNeverOverwritten() throws Exception {
        File config = dataFile("config.yml");
        Files.writeString(config.toPath(), BROKEN_YAML);

        plugin.reload();

        assertEquals(BROKEN_YAML, Files.readString(config.toPath()), "A typo must not wipe the admin's whole config.yml");
    }

    @Test
    void brokenConfigStillLeavesPluginUsable() throws Exception {
        Files.writeString(dataFile("config.yml").toPath(), BROKEN_YAML);
        plugin.reload();

        assertTrue(plugin.getConfigManager().getWorldSettings("world").enabled(),
                "With an unreadable config.yml the bundled defaults should keep RTP working");
    }

    @Test
    void messagesFileWithSyntaxErrorIsNeverOverwritten() throws Exception {
        File messages = dataFile("languages/en/messages.yml");
        Files.writeString(messages.toPath(), "prefix: \"[oops\ngeneral:\n  no-permission: x\n");
        String before = Files.readString(messages.toPath());

        plugin.reload();

        assertEquals(before, Files.readString(messages.toPath()));
        assertFalse(plugin.getMessageManager().getRaw("rtp.success").isEmpty(), "Bundled texts should be used as fallback");
    }

    @Test
    void deletedWorldEntryIsNotResurrected() throws Exception {
        File config = dataFile("config.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config);
        yaml.set("worlds.world_the_end", null);
        yaml.save(config);

        plugin.reload();

        assertFalse(YamlConfiguration.loadConfiguration(config).contains("worlds.world_the_end"),
                "Removing a world from config.yml must disable RTP there, not get reverted on the next start");
    }

    @Test
    void clearedCooldownGroupsStayCleared() throws Exception {
        File config = dataFile("config.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(config);
        yaml.set("cooldown.groups", Map.of());
        yaml.save(config);

        plugin.reload();

        assertTrue(plugin.getConfigManager().getCooldownGroups().isEmpty(),
                "config.yml says 'leave empty ({}) to disable' - the example groups must not come back");
    }

    @Test
    void worldToggleKeepsCommentsAndSurvivesReload() throws Exception {
        addWorld("world_nether");
        plugin.getConfigManager().setWorldEnabled("world_nether", false);
        plugin.reload();

        assertFalse(plugin.getConfigManager().getWorldSettings("world_nether").enabled());
        assertTrue(Files.readString(dataFile("config.yml").toPath()).contains("# ---------- COOLDOWN ----------"));
    }

    @Test
    void brokenZonesFileIsNotOverwrittenByNextZoneSave() throws Exception {
        File zones = dataFile("zones.yml");
        Files.writeString(zones.toPath(), BROKEN_YAML);
        plugin.reload();

        plugin.getZoneManager().create("spawn", new Location(world, 0, 60, 0), new Location(world, 5, 65, 5), 10);

        assertEquals(BROKEN_YAML, Files.readString(zones.toPath()), "Zones the admin already had must not be lost");
    }
}
