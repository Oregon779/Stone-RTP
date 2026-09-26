package dev.stonertp.plugin.manager;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.config.ConfigUpdater;
import dev.stonertp.plugin.model.MessageDisplayType;
import dev.stonertp.plugin.model.RTPWorldSettings;
import dev.stonertp.plugin.model.SearchMode;
import dev.stonertp.plugin.util.YamlFiles;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ConfigManager {
    private static final String RESOURCE_PATH = "config.yml";
    // Sections whose children belong to the admin; removing one must stick instead of being re-added from defaults.
    private static final Set<String> FREE_FORM_SECTIONS = Set.of("worlds", "cooldown.groups");
    private static final int DEFAULT_GUI_SIZE = 27;

    private final StoneRTP plugin;
    private final Map<String, Optional<Sound>> soundCache = new HashMap<>();
    private final Map<String, Optional<Particle>> particleCache = new HashMap<>();
    private File configFile;
    private YamlConfiguration config;
    private boolean readOnly;
    private Map<String, Integer> cooldownGroups = Map.of();

    public ConfigManager(StoneRTP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        configFile = new File(plugin.getDataFolder(), RESOURCE_PATH);
        if (!configFile.exists()) {
            plugin.saveResource(RESOURCE_PATH, false);
        }

        soundCache.clear();
        particleCache.clear();
        try {
            ConfigUpdater.UpdateResult result = ConfigUpdater.update(plugin, RESOURCE_PATH, configFile, FREE_FORM_SECTIONS);
            if (result.addedKeys() > 0) {
                plugin.getLogger().info("Added " + result.addedKeys() + " new option(s) to config.yml");
            }
            config = result.config();
            readOnly = false;
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("config.yml could not be read, so Stone RTP runs on its built-in defaults until it is fixed."
                    + " Your file was NOT changed - fix it and run /stonertp reload. Problem: " + ex.getMessage());
            config = loadBundledDefaults();
            readOnly = true;
        }
        cooldownGroups = readCooldownGroups();
        int guiSize = getInt("gui.size", DEFAULT_GUI_SIZE);
        if (getGuiSize() != guiSize) {
            plugin.getLogger().warning("gui.size must be 9, 18, 27, 36, 45 or 54 (got " + guiSize + "), using " + DEFAULT_GUI_SIZE + ".");
        }
    }

    private YamlConfiguration loadBundledDefaults() {
        try {
            YamlConfiguration bundled = ConfigUpdater.loadBundled(plugin, RESOURCE_PATH);
            return bundled != null ? bundled : new YamlConfiguration();
        } catch (IOException ex) {
            return new YamlConfiguration();
        }
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
        return Math.max(0, getDouble("cost.amount", 100.0));
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
        int size = getInt("gui.size", DEFAULT_GUI_SIZE);
        if (size < 9 || size > 54 || size % 9 != 0) {
            return DEFAULT_GUI_SIZE;
        }
        return size;
    }

    public boolean isFillerEnabled() {
        return getBoolean("gui.filler.enabled", true);
    }

    public String getFillerMaterial() {
        return getString("gui.filler.material", "GRAY_STAINED_GLASS_PANE");
    }

    // Effects ask for these many times per second; parse (and warn about typos) once per load instead.
    public Particle getParticle(String path, Particle fallback) {
        return particleCache.computeIfAbsent(path, this::parseParticle).orElse(fallback);
    }

    private Optional<Particle> parseParticle(String path) {
        String raw = getString(path, null);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Particle.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid particle '" + raw + "' at " + path + ", using default instead.");
            return Optional.empty();
        }
    }

    public Sound getSound(String path, Sound fallback) {
        return soundCache.computeIfAbsent(path, this::parseSound).orElse(fallback);
    }

    private Optional<Sound> parseSound(String path) {
        String raw = getString(path, null);
        if (raw == null) {
            return Optional.empty();
        }
        Sound sound = lookupSound(raw.trim());
        if (sound == null) {
            plugin.getLogger().warning("Invalid sound '" + raw + "' at " + path + ", using default instead.");
        }
        return Optional.ofNullable(sound);
    }

    // Accepts BLOCK_NOTE_BLOCK_HARP or block.note_block.harp without Sound#valueOf, which is marked for removal.
    static Sound lookupSound(String raw) {
        NamespacedKey key = NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
        if (key != null && (raw.contains(".") || raw.contains(":"))) {
            Sound byKey = RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT).get(key);
            if (byKey != null) {
                return byKey;
            }
        }
        try {
            Field constant = Sound.class.getField(raw.toUpperCase(Locale.ROOT));
            return constant.get(null) instanceof Sound sound ? sound : null;
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            return null;
        }
    }

    public boolean isWorldConfigured(String worldName) {
        return config.isConfigurationSection("worlds." + worldName);
    }

    public void setWorldEnabled(String worldName, boolean enabled) {
        config.set("worlds." + worldName + ".enabled", enabled);
        save();
    }

    private void save() {
        if (readOnly) {
            plugin.getLogger().warning("config.yml has errors, so this change only applies until the next restart/reload."
                    + " Fix the file first to make changes stick.");
            return;
        }
        try {
            YamlFiles.saveAtomically(config, configFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save config.yml: " + ex.getMessage());
        }
    }

    public String getBlockedTimesTimezone() {
        return getString("blocked-times.timezone", "");
    }

    public List<String> getBlockedTimePeriods() {
        return getStringList("blocked-times.periods");
    }

    public void setBlockedTimePeriods(List<String> periods) {
        config.set("blocked-times.periods", periods);
        save();
    }

    public int getZoneDefaultIntervalSeconds() {
        return Math.max(1, getInt("zones.default-interval-seconds", 10));
    }

    public String getZoneWandMaterial() {
        return getString("zones.wand-material", "BLAZE_ROD");
    }

    public RTPWorldSettings getWorldSettings(String worldName) {
        ConfigurationSection section = config.getConfigurationSection("worlds." + worldName);
        if (section == null) {
            return new RTPWorldSettings(worldName, false, 0, 0, 0, 0, SearchMode.AUTO);
        }
        return new RTPWorldSettings(
                worldName,
                section.getBoolean("enabled", true),
                section.getInt("center-x", 0),
                section.getInt("center-z", 0),
                section.getInt("radius-min", 100),
                section.getInt("radius-max", 5000),
                SearchMode.fromConfig(section.getString("search-mode", "AUTO"))
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
        return cooldownGroups;
    }

    // YAML treats the dots in "stonertp.cooldown.vip" as nesting, so the permission node is the full leaf path.
    private Map<String, Integer> readCooldownGroups() {
        Map<String, Integer> groups = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("cooldown.groups");
        if (section == null) {
            return Map.of();
        }
        for (String path : section.getKeys(true)) {
            if (!section.isConfigurationSection(path)) {
                groups.put(path, Math.max(0, section.getInt(path)));
            }
        }
        return Collections.unmodifiableMap(groups);
    }
}
