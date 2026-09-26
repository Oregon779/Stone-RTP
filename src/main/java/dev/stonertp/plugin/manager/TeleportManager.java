package dev.stonertp.plugin.manager;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.model.RTPWorldSettings;
import dev.stonertp.plugin.model.TeleportRequest;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
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

    private enum Source { PLAYER, ZONE, ADMIN }

    public TeleportManager(StoneRTP plugin) {
        this.plugin = plugin;
    }

    public boolean isTeleporting(UUID uuid) {
        return active.containsKey(uuid);
    }

    public void startRTP(Player player, String worldName) {
        start(player, worldName, Source.PLAYER, player);
    }

    // Standing in the zone already acts as the warmup, and zones are free admin-placed pads.
    public void startZoneRTP(Player player, String worldName) {
        start(player, worldName, Source.ZONE, player);
    }

    // An admin's forced RTP never charges the target, ignores their cooldown and can't be walked out of.
    public boolean startForcedRTP(Player target, String worldName, CommandSender admin) {
        return start(target, worldName, Source.ADMIN, admin);
    }

    private boolean start(Player player, String worldName, Source source, CommandSender feedback) {
        MessageManager mm = plugin.getMessageManager();
        ConfigManager cfg = plugin.getConfigManager();
        UUID id = player.getUniqueId();

        if (isTeleporting(id)) {
            if (source == Source.ADMIN) {
                mm.sendChat(feedback, "rtp.other-busy", Map.of("player", player.getName()));
            } else {
                mm.sendChat(player, "rtp.already-teleporting", null);
            }
            return false;
        }

        BlockedTimeManager blockedTimes = plugin.getBlockedTimeManager();
        if (blockedTimes.isBlocked(source == Source.ADMIN ? feedback : player)) {
            mm.sendChat(feedback, "rtp.blocked-time", Map.of("until", blockedTimes.formatBlockedUntil()));
            return false;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            mm.sendChat(feedback, "general.world-not-found", Map.of("world", mm.escape(worldName)));
            return false;
        }

        RTPWorldSettings settings = cfg.getWorldSettings(worldName);
        if (!settings.enabled() || !settings.isConfigured()) {
            mm.sendChat(feedback, "general.world-disabled", Map.of("world", mm.escape(worldName)));
            return false;
        }

        CooldownManager cooldown = plugin.getCooldownManager();
        if (source == Source.PLAYER && cooldown.isOnCooldown(player)) {
            String time = cooldown.formatRemaining(cooldown.getRemainingSeconds(player));
            mm.sendChat(player, "rtp.cooldown-active", Map.of("time", time));
            return false;
        }

        EconomyManager economy = plugin.getEconomyManager();
        double cost = cfg.getCostAmount();
        boolean chargeable = source == Source.PLAYER && cost > 0 && economy.canApplyCost()
                && !player.hasPermission("stonertp.bypass.cost");
        if (chargeable) {
            if (!economy.canAfford(player, cost)) {
                mm.sendChat(player, "rtp.insufficient-funds", Map.of("cost", economy.format(cost)));
                return false;
            }
            // Only money that actually left the account may ever be refunded later.
            if (!economy.withdraw(player, cost)) {
                mm.sendChat(player, "rtp.payment-failed", Map.of("cost", economy.format(cost)));
                return false;
            }
            mm.sendChat(player, "rtp.cost-charged", Map.of("cost", economy.format(cost)));
        }

        TeleportRequest request = new TeleportRequest(worldName, player.getLocation().clone(), chargeable ? cost : 0,
                source == Source.ADMIN);
        active.put(id, request);
        request.setLocationFuture(plugin.getSafeLocationFinder().find(world, settings, () -> active.get(id) == request));

        if (source == Source.ADMIN) {
            mm.sendChat(feedback, "rtp.other-triggered", Map.of("player", player.getName()));
        }

        int warmupSeconds = cfg.getWarmupSeconds();
        boolean skipWarmup = source != Source.PLAYER
                || warmupSeconds <= 0
                || !cfg.isWarmupEnabled()
                || player.hasPermission("stonertp.bypass.warmup");
        if (skipWarmup) {
            mm.sendChat(player, "rtp.searching", null);
            awaitAndTeleport(player, request);
        } else {
            runWarmup(player, request, warmupSeconds);
        }
        return true;
    }

    private void runWarmup(Player player, TeleportRequest request, int totalSeconds) {
        NotificationManager notifications = plugin.getNotificationManager();
        EffectManager effects = plugin.getEffectManager();
        int[] secondsLeft = {totalSeconds};
        notifications.sendCountdown(player, totalSeconds, totalSeconds);
        effects.playCountdownSound(player, totalSeconds, totalSeconds);

        request.setTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            secondsLeft[0]--;
            if (secondsLeft[0] <= 0) {
                request.cancelTasks();
                notifications.clear(player);
                effects.clearRotation(player);
                awaitAndTeleport(player, request);
                return;
            }
            notifications.sendCountdown(player, secondsLeft[0], totalSeconds);
            effects.playCountdownSound(player, secondsLeft[0], totalSeconds);
        }, 20L, 20L));

        if (effects.isCountdownEnabled()) {
            request.setEffectsTask(Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> effects.playCountdownFrame(player, Math.max(0, secondsLeft[0]), totalSeconds),
                    0L, effects.getCountdownUpdateIntervalTicks()));
        }
    }

    // The request stays active until the teleport itself starts, so no second /rtp can slip in during the preload.
    private void awaitAndTeleport(Player player, TeleportRequest request) {
        UUID id = player.getUniqueId();
        request.getLocationFuture().whenComplete((location, error) -> runOnMainThread(() -> {
            if (active.get(id) != request) {
                return;
            }
            if (location == null) {
                active.remove(id);
                plugin.getMessageManager().sendChat(player, "rtp.no-safe-location",
                        Map.of("attempts", String.valueOf(plugin.getConfigManager().getSafeLocationMaxAttempts())));
                refund(player, request);
                return;
            }
            preloadAndTeleport(player, location, request);
        }));
    }

    private void preloadAndTeleport(Player player, Location location, TeleportRequest request) {
        UUID id = player.getUniqueId();
        World world = location.getWorld();
        int centerChunkX = location.getBlockX() >> 4;
        int centerChunkZ = location.getBlockZ() >> 4;
        List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunkFutures.add(world.getChunkAtAsync(centerChunkX + dx, centerChunkZ + dz));
            }
        }

        // whenComplete (not thenRun): a failed neighbour chunk must not leave the request stuck forever.
        CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture<?>[0])).whenComplete((ignored, error) -> runOnMainThread(() -> {
            if (active.get(id) != request || !player.isOnline()) {
                return;
            }
            active.remove(id);
            teleport(player, location, request);
        }));
    }

    private void teleport(Player player, Location location, TeleportRequest request) {
        MessageManager mm = plugin.getMessageManager();
        Location origin = player.getLocation();
        plugin.getEffectManager().playDeparture(origin.clone());
        player.teleportAsync(location).whenComplete((success, error) -> runOnMainThread(() -> {
            if (error != null || !Boolean.TRUE.equals(success)) {
                refund(player, request);
                mm.sendChat(player, "rtp.teleport-failed", null);
                return;
            }
            plugin.getBackLocationManager().remember(player, origin);
            if (!request.isForcedByAdmin()) {
                plugin.getCooldownManager().markTeleported(player);
            }
            if (!player.isOnline()) {
                return;
            }
            plugin.getEffectManager().playArrival(player);
            mm.sendChat(player, "rtp.success", Map.of(
                    "world", location.getWorld().getName(),
                    "x", String.valueOf(location.getBlockX()),
                    "y", String.valueOf(location.getBlockY()),
                    "z", String.valueOf(location.getBlockZ())
            ));
            if (request.isForcedByAdmin()) {
                mm.sendChat(player, "rtp.other-success", null);
            }
        }));
    }

    private void runOnMainThread(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    private void refund(OfflinePlayer player, TeleportRequest request) {
        if (request.getCostCharged() > 0) {
            plugin.getEconomyManager().refund(player, request.getCostCharged());
        }
    }

    public void cancelOnQuit(Player player) {
        TeleportRequest request = active.remove(player.getUniqueId());
        if (request == null) {
            return;
        }
        request.cancelTasks();
        plugin.getNotificationManager().clear(player);
        plugin.getEffectManager().clearRotation(player);
        refund(player, request);
    }

    public boolean cancel(Player player, String messageKey) {
        TeleportRequest request = active.remove(player.getUniqueId());
        if (request == null) {
            return false;
        }
        request.cancelTasks();
        plugin.getNotificationManager().clear(player);
        plugin.getEffectManager().clearRotation(player);
        refund(player, request);
        plugin.getMessageManager().sendChat(player, messageKey, null);
        return true;
    }

    // Plugin disable/reload mid-warmup: give the money back and remove countdown bars that would otherwise stay forever.
    public void shutdown() {
        for (Map.Entry<UUID, TeleportRequest> entry : active.entrySet()) {
            TeleportRequest request = entry.getValue();
            request.cancelTasks();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                plugin.getNotificationManager().clear(player);
            }
            refund(Bukkit.getOfflinePlayer(entry.getKey()), request);
        }
        active.clear();
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
