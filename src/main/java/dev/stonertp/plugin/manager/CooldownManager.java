package dev.stonertp.plugin.manager;

import dev.stonertp.plugin.StoneRTP;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {
    private final StoneRTP plugin;
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    public CooldownManager(StoneRTP plugin) {
        this.plugin = plugin;
    }

    public boolean isOnCooldown(Player player) {
        if (!plugin.getConfigManager().isCooldownEnabled()) {
            return false;
        }
        if (player.hasPermission("stonertp.bypass.cooldown")) {
            return false;
        }
        return getRemainingSeconds(player) > 0;
    }

    public long getRemainingSeconds(Player player) {
        Long last = lastTeleport.get(player.getUniqueId());
        if (last == null) {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - last) / 1000L;
        long remaining = getApplicableCooldownSeconds(player) - elapsed;
        return Math.max(0, remaining);
    }

    private long getApplicableCooldownSeconds(Player player) {
        long best = plugin.getConfigManager().getCooldownSeconds();
        for (Map.Entry<String, Integer> group : plugin.getConfigManager().getCooldownGroups().entrySet()) {
            if (player.hasPermission(group.getKey()) && group.getValue() < best) {
                best = group.getValue();
            }
        }
        return best;
    }

    public String formatRemaining(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }

    public void markTeleported(Player player) {
        lastTeleport.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void clear(UUID uuid) {
        lastTeleport.remove(uuid);
    }

    public void pruneExpired() {
        long cutoffMillis = System.currentTimeMillis() - (getMaxPossibleCooldownSeconds() * 1000L);
        lastTeleport.values().removeIf(timestamp -> timestamp < cutoffMillis);
    }

    private long getMaxPossibleCooldownSeconds() {
        long max = plugin.getConfigManager().getCooldownSeconds();
        for (int seconds : plugin.getConfigManager().getCooldownGroups().values()) {
            max = Math.max(max, seconds);
        }
        return max;
    }
}
