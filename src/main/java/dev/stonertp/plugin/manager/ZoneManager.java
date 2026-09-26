package dev.stonertp.plugin.manager;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.model.RTPWorldSettings;
import dev.stonertp.plugin.model.RTPZone;
import dev.stonertp.plugin.util.ItemBuilder;
import dev.stonertp.plugin.util.YamlFiles;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class ZoneManager {
    private static final String FILE_NAME = "zones.yml";
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final StoneRTP plugin;
    private final NamespacedKey wandKey;
    private final Map<String, RTPZone> zones = new LinkedHashMap<>();
    private final Map<UUID, Location[]> selections = new HashMap<>();
    private final Map<UUID, Stay> stays = new HashMap<>();
    private Material wandMaterial = Material.BLAZE_ROD;
    private BukkitTask task;
    private boolean storageBroken;

    private record Stay(String zoneName, int elapsedSeconds) {
    }

    public ZoneManager(StoneRTP plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "zone-wand");
    }

    public void load() {
        wandMaterial = parseWandMaterial(plugin.getConfigManager().getZoneWandMaterial());
        zones.clear();
        stays.clear();
        storageBroken = false;

        File file = file();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            storageBroken = true;
            plugin.getLogger().severe("zones.yml could not be read, so no RTP zones are active and zone changes are blocked"
                    + " until it is fixed (the file was NOT changed). Fix it and run /stonertp reload. Problem: " + ex.getMessage());
            return;
        }
        ConfigurationSection section = yaml.getConfigurationSection("zones");
        if (section == null) {
            return;
        }
        int defaultInterval = plugin.getConfigManager().getZoneDefaultIntervalSeconds();
        for (String name : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(name);
            String world = entry != null ? entry.getString("world") : null;
            if (world == null) {
                plugin.getLogger().warning("Zone '" + name + "' in zones.yml has no world, skipping.");
                continue;
            }
            zones.put(key(name), new RTPZone(
                    name,
                    world,
                    entry.getInt("min-x"),
                    entry.getInt("min-y"),
                    entry.getInt("min-z"),
                    entry.getInt("max-x"),
                    entry.getInt("max-y"),
                    entry.getInt("max-z"),
                    Math.max(1, entry.getInt("interval-seconds", defaultInterval))
            ));
        }
    }

    private Material parseWandMaterial(String raw) {
        Material material = raw == null ? null : Material.matchMaterial(raw.trim());
        if (material == null || !material.isItem() || material.isAir()) {
            plugin.getLogger().warning("Invalid zones.wand-material '" + raw + "', using BLAZE_ROD instead.");
            return Material.BLAZE_ROD;
        }
        return material;
    }

    public boolean isStorageBroken() {
        return storageBroken;
    }

    private void save() {
        if (storageBroken) {
            plugin.getLogger().warning("Not saving zones.yml because it could not be read - saving now would delete the zones in it.");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (RTPZone zone : zones.values()) {
            String path = "zones." + zone.name();
            yaml.set(path + ".world", zone.worldName());
            yaml.set(path + ".min-x", zone.minX());
            yaml.set(path + ".min-y", zone.minY());
            yaml.set(path + ".min-z", zone.minZ());
            yaml.set(path + ".max-x", zone.maxX());
            yaml.set(path + ".max-y", zone.maxY());
            yaml.set(path + ".max-z", zone.maxZ());
            yaml.set(path + ".interval-seconds", zone.intervalSeconds());
        }
        try {
            YamlFiles.saveAtomically(yaml, file());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save zones.yml: " + ex.getMessage());
        }
    }

    private File file() {
        return new File(plugin.getDataFolder(), FILE_NAME);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        stays.clear();
    }

    private void tick() {
        if (zones.isEmpty()) {
            stays.clear();
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            tickPlayer(player);
        }
    }

    private void tickPlayer(Player player) {
        UUID id = player.getUniqueId();
        MessageManager mm = plugin.getMessageManager();
        RTPZone zone = zoneAt(player.getLocation());

        if (zone == null) {
            if (stays.remove(id) != null) {
                mm.sendActionBar(player, "zone.left", null);
            }
            return;
        }

        TeleportManager teleports = plugin.getTeleportManager();
        if (teleports.isTeleporting(id) || player.hasPermission("stonertp.bypass.zone") || !isRtpEnabled(player.getWorld())) {
            stays.remove(id);
            return;
        }

        BlockedTimeManager blockedTimes = plugin.getBlockedTimeManager();
        if (blockedTimes.isBlocked(player)) {
            stays.remove(id);
            mm.sendActionBar(player, "zone.paused", Map.of("until", blockedTimes.formatBlockedUntil()));
            return;
        }

        Stay stay = stays.get(id);
        int elapsed = stay != null && stay.zoneName().equals(zone.name()) ? stay.elapsedSeconds() + 1 : 0;
        if (elapsed >= zone.intervalSeconds()) {
            stays.remove(id);
            teleports.startZoneRTP(player, player.getWorld().getName());
            return;
        }
        stays.put(id, new Stay(zone.name(), elapsed));
        mm.sendActionBar(player, "zone.countdown", Map.of("seconds", String.valueOf(zone.intervalSeconds() - elapsed)));
    }

    public boolean isRtpEnabled(World world) {
        RTPWorldSettings settings = plugin.getConfigManager().getWorldSettings(world.getName());
        return settings.enabled() && settings.isConfigured();
    }

    private RTPZone zoneAt(Location location) {
        for (RTPZone zone : zones.values()) {
            if (zone.contains(location)) {
                return zone;
            }
        }
        return null;
    }

    public void forget(UUID uuid) {
        selections.remove(uuid);
        stays.remove(uuid);
    }

    public void setCorner(Player player, int corner, Location blockLocation) {
        Location[] selection = selections.computeIfAbsent(player.getUniqueId(), k -> new Location[2]);
        selection[corner - 1] = blockLocation.clone();

        MessageManager mm = plugin.getMessageManager();
        mm.sendChat(player, "zone.pos-set", Map.of(
                "pos", String.valueOf(corner),
                "x", String.valueOf(blockLocation.getBlockX()),
                "y", String.valueOf(blockLocation.getBlockY()),
                "z", String.valueOf(blockLocation.getBlockZ()),
                "world", blockLocation.getWorld().getName()
        ));

        Location other = selection[corner == 1 ? 1 : 0];
        if (other != null && other.getWorld().equals(blockLocation.getWorld())) {
            RTPZone preview = RTPZone.fromCorners("preview", selection[0], selection[1], 1);
            mm.sendChat(player, "zone.selection-size", Map.of(
                    "x", String.valueOf(preview.sizeX()),
                    "y", String.valueOf(preview.sizeY()),
                    "z", String.valueOf(preview.sizeZ())
            ));
        }
    }

    public Location[] getSelection(UUID uuid) {
        return selections.get(uuid);
    }

    public boolean isValidName(String name) {
        return NAME_PATTERN.matcher(name).matches();
    }

    public RTPZone getZone(String name) {
        return zones.get(key(name));
    }

    public Collection<RTPZone> getZones() {
        return zones.values();
    }

    public RTPZone create(String name, Location cornerA, Location cornerB, int intervalSeconds) {
        RTPZone zone = RTPZone.fromCorners(name, cornerA, cornerB, intervalSeconds);
        zones.put(key(name), zone);
        save();
        return zone;
    }

    public boolean delete(String name) {
        RTPZone removed = zones.remove(key(name));
        if (removed == null) {
            return false;
        }
        stays.values().removeIf(stay -> stay.zoneName().equals(removed.name()));
        save();
        return true;
    }

    public ItemStack createWand() {
        MessageManager mm = plugin.getMessageManager();
        List<Component> lore = mm.getRawList("zone.wand-lore").stream()
                .map(line -> mm.format(line, null))
                .toList();
        return new ItemBuilder(wandMaterial)
                .name(mm.format(mm.getRaw("zone.wand-name"), null))
                .lore(lore)
                .glow(true)
                .data(wandKey, "1")
                .build();
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != wandMaterial || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.STRING);
    }
}
