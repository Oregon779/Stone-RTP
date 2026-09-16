package dev.stonertp.plugin.manager;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.config.ConfigUpdater;
import dev.stonertp.plugin.model.MessageDisplayType;
import dev.stonertp.plugin.model.RTPWorldSettings;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private static final String RESOURCE_PATH = "config.yml";

    private final StoneRTP plugin;
    private File configFile;
    private YamlConfiguration config;

    public ConfigManager(StoneRTP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        configFile = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (!configFile.exists()) {
            plugin.saveResource(RESOURCE_PATH, false);
        }

        try {
            ConfigUpdater.UpdateResult result = ConfigUpdater.update(plugin, RESOURCE_PATH, configFile);
            if (result.addedKeys() > 0) {
                plugin.getLogger().info("Added " + result.addedKeys() + " new option(s) to config.yml");
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to update config.yml: " + ex.getMessage());
        }

        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void reload() {
        load();
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public double getDouble(String path, double def) {
        return config.getDouble(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    public String getLanguage() {
        return config.getString("language", "en");
    }

    public MessageDisplayType getNotificationType() {
        return MessageDisplayType.fromConfig(getString("notification.type", "ACTIONBAR"), MessageDisplayType.ACTIONBAR);
    }

    public boolean isCooldownEnabled() {
        return getBoolean("cooldown.enabled", true);
    }

    public long getCooldownSeconds() {
        return getInt("cooldown.seconds", 30);
    }

    public boolean isWarmupEnabled() {
        return getBoolean("warmup.enabled", true);
    }

    public int getWarmupSeconds() {
        return getInt("warmup.seconds", 5);
    }

    public boolean isCancelOnMove() {
        return getBoolean("warmup.cancel-on-move", true);
    }

    public double getMoveCancelThreshold() {
        return getDouble("warmup.move-cancel-threshold", 0.6);
    }

    public boolean isCancelOnDamage() {
        return getBoolean("warmup.cancel-on-damage", true);
    }

    public boolean isCostEnabled() {
        return getBoolean("cost.enabled", false);
    }

    public double getCostAmount() {
        return getDouble("cost.amount", 100.0);
    }

    public int getSafeLocationMaxAttempts() {
        return Math.max(1, getInt("safe-location.max-attempts", 30));
    }

    public int getSafeLocationMinY() {
        return getInt("safe-location.min-y", -58);
    }

    public int getSafeLocationMaxY() {
        return getInt("safe-location.max-y", 300);
    }

    public boolean isAvoidWater() {
        return getBoolean("safe-location.avoid-water", true);
    }

    public boolean isAvoidLava() {
        return getBoolean("safe-location.avoid-lava", true);
    }

    public List<String> getUnsafeMaterials() {
        return getStringList("safe-location.unsafe-materials");
    }

    public boolean isGuiEnabled() {
        return getBoolean("gui.enabled", true);
    }

    public int getGuiSize() {
        return getInt("gui.size", 27);
    }

    public boolean isFillerEnabled() {
        return getBoolean("gui.filler.enabled", true);
    }

    public String getFillerMaterial() {
        return getString("gui.filler.material", "GRAY_STAINED_GLASS_PANE");
    }

    public Particle getParticle(String path, Particle fallback) {
        String raw = getString(path, null);
        if (raw == null) {
            return fallback;
        }
        try {
            return Particle.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid particle '" + raw + "' at " + path + ", using default instead.");
            return fallback;
        }
    }

    public Sound getSound(String path, Sound fallback) {
        String raw = getString(path, null);
        if (raw == null) {
            return fallback;
        }
        try {
            return Sound.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid sound '" + raw + "' at " + path + ", using default instead.");
            return fallback;
        }
    }

    public boolean isWorldConfigured(String worldName) {
        return config.isConfigurationSection("worlds." + worldName);
    }

    public void setWorldEnabled(String worldName, boolean enabled) {
        config.set("worlds." + worldName + ".enabled", enabled);
        try {
            config.save(configFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save config.yml: " + ex.getMessage());
        }
    }

    public RTPWorldSettings getWorldSettings(String worldName) {
        ConfigurationSection section = config.getConfigurationSection("worlds." + worldName);
        if (section == null) {
            return new RTPWorldSettings(worldName, false, 0, 0, 0, 0);
        }
        return new RTPWorldSettings(
                worldName,
                section.getBoolean("enabled", true),
                section.getInt("center-x", 0),
                section.getInt("center-z", 0),
                section.getInt("radius-min", 100),
                section.getInt("radius-max", 5000)
        );
    }

    public Map<String, RTPWorldSettings> getAllWorldSettings() {
        Map<String, RTPWorldSettings> result = new LinkedHashMap<>();
        ConfigurationSection worldsSection = config.getConfigurationSection("worlds");
        if (worldsSection == null) {
            return result;
        }
        for (String worldName : worldsSection.getKeys(false)) {
            result.put(worldName, getWorldSettings(worldName));
        }
        return result;
    }

    public String getGuiWorldFor(String slotKey) {
        return getString("gui." + slotKey + ".world", null);
    }

    public int getGuiSlotFor(String slotKey) {
        return getInt("gui." + slotKey + ".slot", 0);
    }

    public String getGuiMaterialFor(String slotKey) {
        return getString("gui." + slotKey + ".material", "STONE");
    }

    public String getGuiHeadTextureFor(String slotKey) {
        return getString("gui." + slotKey + ".head-texture", null);
    }

    public boolean isGuiGlowFor(String slotKey) {
        return getBoolean("gui." + slotKey + ".glow", false);
    }

    public String getGuiCornerMaterial() {
        return getString("gui.filler.corner-material", null);
    }

    private static final String[] GUI_SLOT_KEYS = {"overworld", "nether", "end"};

    public String getGuiKeyForWorld(String worldName) {
        for (String key : GUI_SLOT_KEYS) {
            if (worldName.equals(getGuiWorldFor(key))) {
                return key;
            }
        }
        return null;
    }

    public Sound getGuiClickSoundFor(String slotKey, Sound fallback) {
        return getSound("gui." + slotKey + ".click-sound", fallback);
    }

    public List<String> getGuiFillerMaterials() {
        List<String> materials = getStringList("gui.filler.materials");
        return materials.isEmpty() ? List.of(getFillerMaterial()) : materials;
    }

    public String getNotificationMessage(String type) {
        return getString("notification.messages." + type.toLowerCase(), "");
    }

    public boolean isRespectWorldBorder() {
        return getBoolean("safe-location.respect-world-border", true);
    }

    public List<String> getBlacklistedBiomes() {
        return getStringList("safe-location.blacklisted-biomes");
    }

    public boolean isOnJoinEnabled() {
        return getBoolean("on-join.enabled", false);
    }

    public boolean isOnJoinOnlyFirstJoin() {
        return getBoolean("on-join.only-first-join", true);
    }

    public String getOnJoinWorld() {
        return getString("on-join.world", "world");
    }

    public long getOnJoinDelayTicks() {
        return getInt("on-join.delay-ticks", 40);
    }

    public Map<String, Integer> getCooldownGroups() {
        Map<String, Integer> groups = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("cooldown.groups");
        if (section == null) {
            return groups;
        }
        for (String permission : section.getKeys(false)) {
            groups.put(permission, section.getInt(permission));
        }
        return groups;
    }
}
