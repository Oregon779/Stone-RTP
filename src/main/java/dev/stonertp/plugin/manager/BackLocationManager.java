package dev.stonertp.plugin.manager;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class BackLocationManager {
    private static final int MAX_ENTRIES = 5000;

    private final Map<UUID, Location> lastLocations = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Location> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public void remember(Player player, Location location) {
        lastLocations.put(player.getUniqueId(), location.clone());
    }

    public Location get(Player player) {
        return lastLocations.get(player.getUniqueId());
    }

    public void clear(UUID uuid) {
        lastLocations.remove(uuid);
    }
}
