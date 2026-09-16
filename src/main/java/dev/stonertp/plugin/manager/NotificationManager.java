package dev.stonertp.plugin.manager;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.model.MessageDisplayType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NotificationManager {
    private final StoneRTP plugin;
    private final Map<UUID, BossBar> activeBossBars = new HashMap<>();

    public NotificationManager(StoneRTP plugin) {
        this.plugin = plugin;
    }

    public void sendCountdown(Player player, int secondsLeft, int totalSeconds) {
        MessageDisplayType type = plugin.getConfigManager().getNotificationType();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("seconds", String.valueOf(secondsLeft));
        MessageManager mm = plugin.getMessageManager();

        switch (type) {
            case CHAT -> player.sendMessage(mm.format(plugin.getConfigManager().getNotificationMessage("chat"), placeholders));
            case ACTIONBAR -> player.sendActionBar(mm.format(plugin.getConfigManager().getNotificationMessage("actionbar"), placeholders));
            case TITLE -> showTitle(player, placeholders);
            case BOSSBAR -> showBossBar(player, mm.format(plugin.getConfigManager().getNotificationMessage("bossbar"), placeholders), secondsLeft, totalSeconds);
        }
    }

    private void showTitle(Player player, Map<String, String> placeholders) {
        MessageManager mm = plugin.getMessageManager();
        Component title = mm.format(plugin.getConfigManager().getNotificationMessage("title"), placeholders);
        Component subtitle = mm.format(plugin.getConfigManager().getNotificationMessage("subtitle"), placeholders);
        int fadeIn = plugin.getConfigManager().getInt("notification.title-timing.fade-in-ticks", 5);
        int stay = plugin.getConfigManager().getInt("notification.title-timing.stay-ticks", 40);
        int fadeOut = plugin.getConfigManager().getInt("notification.title-timing.fade-out-ticks", 10);
        Title.Times times = Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L));
        player.showTitle(Title.title(title, subtitle, times));
    }

    private void showBossBar(Player player, Component message, int secondsLeft, int totalSeconds) {
        BossBar.Color color = parseColor(plugin.getConfigManager().getString("notification.bossbar.color", "YELLOW"));
        BossBar.Overlay style = parseStyle(plugin.getConfigManager().getString("notification.bossbar.style", "NOTCHED_10"));
        float progress = totalSeconds <= 0 ? 1f : Math.max(0f, Math.min(1f, (float) secondsLeft / (float) totalSeconds));

        BossBar bar = activeBossBars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(message, progress, color, style);
            activeBossBars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(message);
            bar.progress(progress);
        }
    }

    private BossBar.Color parseColor(String raw) {
        try {
            return BossBar.Color.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BossBar.Color.YELLOW;
        }
    }

    private BossBar.Overlay parseStyle(String raw) {
        try {
            return BossBar.Overlay.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BossBar.Overlay.NOTCHED_10;
        }
    }

    public void clear(Player player) {
        BossBar bar = activeBossBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
        player.clearTitle();
    }
}
