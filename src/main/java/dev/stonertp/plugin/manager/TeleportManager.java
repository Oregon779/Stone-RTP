package dev.stonertp.plugin.manager;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.model.RTPWorldSettings;
import dev.stonertp.plugin.model.TeleportRequest;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TeleportManager {
    private final StoneRTP plugin;
    private final Map<UUID, TeleportRequest> active = new HashMap<>();

    public TeleportManager(StoneRTP plugin) {
        this.plugin = plugin;
    }

    public boolean isTeleporting(UUID uuid) {
        return active.containsKey(uuid);
    }

    public void startRTP(Player player, String worldName) {
        startRTP(player, worldName, false);
    }

    // Zone standing time already acts as the warmup, and zones are free admin-placed pads, so
    // cooldown, cost and warmup are skipped here.
    public void startZoneRTP(Player player, String worldName) {
        startRTP(player, worldName, true);
    }

    private void startRTP(Player player, String worldName, boolean fromZone) {
        MessageManager mm = plugin.getMessageManager();

        if (isTeleporting(player.getUniqueId())) {
            mm.sendChat(player, "rtp.already-teleporting", null);
            return;
        }

        BlockedTimeManager blockedTimes = plugin.getBlockedTimeManager();
        if (blockedTimes.isBlocked(player)) {
            mm.sendChat(player, "rtp.blocked-time", Map.of("until", blockedTimes.formatBlockedUntil()));
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            mm.sendChat(player, "general.world-not-found", Map.of("world", worldName));
            return;
        }

        RTPWorldSettings settings = plugin.getConfigManager().getWorldSettings(worldName);
        if (!settings.enabled() || !settings.isConfigured()) {
            mm.sendChat(player, "general.world-disabled", Map.of("world", worldName));
            return;
        }

        CooldownManager cooldown = plugin.getCooldownManager();
        if (!fromZone && cooldown.isOnCooldown(player)) {
            String time = cooldown.formatRemaining(cooldown.getRemainingSeconds(player));
            mm.sendChat(player, "rtp.cooldown-active", Map.of("time", time));
            return;
        }

        EconomyManager economy = plugin.getEconomyManager();
        double cost = plugin.getConfigManager().getCostAmount();
        boolean chargeable = !fromZone && economy.canApplyCost() && !player.hasPermission("stonertp.bypass.cost");
        if (chargeable) {
            if (!economy.canAfford(player, cost)) {
                mm.sendChat(player, "rtp.insufficient-funds", Map.of("cost", economy.format(cost)));
                return;
            }
            economy.withdraw(player, cost);
            mm.sendChat(player, "rtp.cost-charged", Map.of("cost", economy.format(cost)));
        }

        TeleportRequest request = new TeleportRequest(worldName, player.getLocation().clone(), chargeable ? cost : 0);
        active.put(player.getUniqueId(), request);
        request.setLocationFuture(plugin.getSafeLocationFinder().find(world, settings));

        boolean bypassWarmup = fromZone
                || player.hasPermission("stonertp.bypass.warmup")
                || !plugin.getConfigManager().isWarmupEnabled();
        if (bypassWarmup) {
            mm.sendChat(player, "rtp.searching", null);
            awaitAndTeleport(player, request);
        } else {
            runWarmup(player, plugin.getConfigManager().getWarmupSeconds());
        }
    }

    private void runWarmup(Player player, int totalSeconds) {
        int[] secondsLeft = {totalSeconds};
        plugin.getNotificationManager().sendCountdown(player, secondsLeft[0], totalSeconds);
        plugin.getEffectManager().playCountdownSound(player, secondsLeft[0], totalSeconds);

        var task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            secondsLeft[0]--;
            TeleportRequest request = active.get(player.getUniqueId());
            if (request == null) {
                return;
            }
            if (secondsLeft[0] <= 0) {
                request.cancel();
                plugin.getNotificationManager().clear(player);
                awaitAndTeleport(player, request);
                return;
            }
            plugin.getNotificationManager().sendCountdown(player, secondsLeft[0], totalSeconds);
            plugin.getEffectManager().playCountdownSound(player, secondsLeft[0], totalSeconds);
        }, 20L, 20L);

        active.get(player.getUniqueId()).setTask(task);

        if (plugin.getEffectManager().isCountdownEnabled()) {
            long interval = plugin.getEffectManager().getCountdownUpdateIntervalTicks();
            var effectsTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (active.containsKey(player.getUniqueId())) {
                    plugin.getEffectManager().playCountdownFrame(player, Math.max(0, secondsLeft[0]), totalSeconds);
                }
            }, 0L, interval);
            active.get(player.getUniqueId()).setEffectsTask(effectsTask);
        }
    }

    private void awaitAndTeleport(Player player, TeleportRequest request) {
        request.getLocationFuture().thenAccept(location ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    TeleportRequest current = active.remove(player.getUniqueId());
                    if (current == null || !player.isOnline()) {
                        return;
                    }
                    if (location == null) {
                        plugin.getMessageManager().sendChat(player, "rtp.no-safe-location",
                                Map.of("attempts", String.valueOf(plugin.getConfigManager().getSafeLocationMaxAttempts())));
                        refund(player, current);
                        return;
                    }
                    preloadAndTeleport(player, location, current);
                }));
    }

    private void preloadAndTeleport(Player player, Location location, TeleportRequest request) {
        World world = location.getWorld();
        if (world == null) {
            teleport(player, location, request);
            return;
        }

        int centerChunkX = location.getBlockX() >> 4;
        int centerChunkZ = location.getBlockZ() >> 4;
        List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunkFutures.add(world.getChunkAtAsync(centerChunkX + dx, centerChunkZ + dz));
            }
        }

        CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0])).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    teleport(player, location, request);
                }));
    }

    private void teleport(Player player, Location location, TeleportRequest request) {
        Location origin = player.getLocation();
        plugin.getBackLocationManager().remember(player, origin);
        plugin.getEffectManager().playDeparture(origin.clone());
        player.teleportAsync(location).thenAccept(success -> {
            if (!success) {
                refund(player, request);
                return;
            }
            plugin.getCooldownManager().markTeleported(player);
            plugin.getEffectManager().playArrival(player);
            plugin.getMessageManager().sendChat(player, "rtp.success", Map.of(
                    "world", location.getWorld().getName(),
                    "x", String.valueOf(location.getBlockX()),
                    "y", String.valueOf(location.getBlockY()),
                    "z", String.valueOf(location.getBlockZ())
            ));
        });
    }

    private void refund(Player player, TeleportRequest request) {
        if (request.getCostCharged() > 0) {
            plugin.getEconomyManager().refund(player, request.getCostCharged());
        }
    }

    public void cancelOnQuit(UUID uuid) {
        TeleportRequest request = active.remove(uuid);
        if (request != null) {
            request.cancel();
        }
    }

    public void cancel(Player player, String messageKey) {
        TeleportRequest request = active.remove(player.getUniqueId());
        if (request == null) {
            return;
        }
        request.cancel();
        plugin.getNotificationManager().clear(player);
        refund(player, request);
        plugin.getMessageManager().sendChat(player, messageKey, null);
    }

    public boolean hasActiveWarmup(UUID uuid) {
        TeleportRequest request = active.get(uuid);
        return request != null && request.getTask() != null;
    }

    public Location getStartLocation(UUID uuid) {
        TeleportRequest request = active.get(uuid);
        return request != null ? request.getStartLocation() : null;
    }
}
