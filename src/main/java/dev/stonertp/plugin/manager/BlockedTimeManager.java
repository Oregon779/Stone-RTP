package dev.stonertp.plugin.manager;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.model.TimeWindow;
import org.bukkit.permissions.Permissible;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BlockedTimeManager {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final StoneRTP plugin;
    private List<TimeWindow> windows = List.of();
    private ZoneId zoneId = ZoneId.systemDefault();
    private Clock clock = Clock.systemUTC();

    public BlockedTimeManager(StoneRTP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        ConfigManager cfg = plugin.getConfigManager();
        zoneId = parseZone(cfg.getBlockedTimesTimezone());

        List<TimeWindow> parsed = new ArrayList<>();
        for (String raw : cfg.getBlockedTimePeriods()) {
            try {
                parsed.add(TimeWindow.parse(raw));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid entry '" + raw + "' in blocked-times.periods (" + ex.getMessage()
                        + ") - expected HH:MM-HH:MM, skipping.");
            }
        }
        windows = List.copyOf(parsed);
    }

    private ZoneId parseZone(String raw) {
        if (raw == null || raw.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(raw.trim());
        } catch (DateTimeException ex) {
            plugin.getLogger().warning("Invalid blocked-times.timezone '" + raw + "', using the server's clock instead.");
            return ZoneId.systemDefault();
        }
    }

    public List<TimeWindow> getWindows() {
        return windows;
    }

    public LocalTime now() {
        return LocalTime.now(clock.withZone(zoneId));
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public String formatNow() {
        return CLOCK.format(now());
    }

    public String getZoneName() {
        return zoneId.getId();
    }

    public boolean isBlocked(Permissible initiator) {
        return activeWindow(now()) != null && !initiator.hasPermission("stonertp.bypass.blocktime");
    }

    public boolean isActive(TimeWindow window) {
        return window.contains(now());
    }

    // Follows back-to-back windows (e.g. 14-16 and 16-18) so players see the real reopening time.
    public String formatBlockedUntil() {
        TimeWindow active = activeWindow(now());
        if (active == null) {
            return "";
        }
        LocalTime until = active.end();
        for (int i = 0; i < windows.size(); i++) {
            TimeWindow next = activeWindow(until);
            if (next == null) {
                break;
            }
            until = next.end();
        }
        return CLOCK.format(until);
    }

    private TimeWindow activeWindow(LocalTime time) {
        for (TimeWindow window : windows) {
            if (window.contains(time)) {
                return window;
            }
        }
        return null;
    }

    public boolean add(TimeWindow window) {
        for (TimeWindow existing : windows) {
            if (existing.equals(window)) {
                return false;
            }
        }
        List<TimeWindow> updated = new ArrayList<>(windows);
        updated.add(window);
        persist(updated);
        return true;
    }

    public TimeWindow remove(int index) {
        if (index < 0 || index >= windows.size()) {
            return null;
        }
        List<TimeWindow> updated = new ArrayList<>(windows);
        TimeWindow removed = updated.remove(index);
        persist(updated);
        return removed;
    }

    private void persist(List<TimeWindow> updated) {
        windows = List.copyOf(updated);
        plugin.getConfigManager().setBlockedTimePeriods(updated.stream().map(TimeWindow::serialize).toList());
    }
}
