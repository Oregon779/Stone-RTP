package dev.stonertp.plugin.config;

import dev.stonertp.plugin.util.YamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class ConfigUpdater {
    private ConfigUpdater() {
    }

    // Throws rather than rewriting a file it can't parse; entries under freeFormSections are never re-added once removed.
    public static UpdateResult update(JavaPlugin plugin, String resourcePath, File targetFile, Set<String> freeFormSections)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration current = new YamlConfiguration();
        current.load(targetFile);

        YamlConfiguration defaults = loadBundled(plugin, resourcePath);
        if (defaults == null) {
            return new UpdateResult(current, 0);
        }

        int added = mergeSection(defaults, current, freeFormSections);
        if (added == 0) {
            return new UpdateResult(current, 0);
        }
        YamlFiles.saveAtomically(current, targetFile);

        // set() stored the defaults' own section objects; re-parse so every section belongs to this config.
        YamlConfiguration reparsed = new YamlConfiguration();
        reparsed.loadFromString(current.saveToString());
        return new UpdateResult(reparsed, added);
    }

    public static YamlConfiguration loadBundled(JavaPlugin plugin, String resourcePath) throws IOException {
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        }
    }

    private static int mergeSection(ConfigurationSection defaults, ConfigurationSection current, Set<String> freeFormSections) {
        int added = 0;
        for (String key : defaults.getKeys(false)) {
            Object defaultValue = defaults.get(key);

            if (!current.contains(key, true)) {
                current.set(key, defaultValue);
                added++;
                continue;
            }

            String fullPath = current.getCurrentPath() == null || current.getCurrentPath().isEmpty()
                    ? key
                    : current.getCurrentPath() + "." + key;
            if (freeFormSections.contains(fullPath)) {
                continue;
            }
            if (defaultValue instanceof ConfigurationSection defaultSection && current.isConfigurationSection(key)) {
                added += mergeSection(defaultSection, current.getConfigurationSection(key), freeFormSections);
            }
        }
        return added;
    }

    public record UpdateResult(YamlConfiguration config, int addedKeys) {
    }
}
