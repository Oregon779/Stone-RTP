package dev.stonertp.plugin.command;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.manager.BlockedTimeManager;
import dev.stonertp.plugin.manager.ConfigManager;
import dev.stonertp.plugin.manager.MessageManager;
import dev.stonertp.plugin.manager.ZoneManager;
import dev.stonertp.plugin.model.RTPZone;
import dev.stonertp.plugin.model.TimeWindow;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class StoneRTPCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("reload", "toggle", "blocktime", "zone", "help", "checkupdate");
    private static final List<String> TOGGLE_KEYS = List.of("overworld", "nether", "end");
    private static final List<String> TOGGLE_STATES = List.of("on", "off");
    private static final List<String> BLOCKTIME_ACTIONS = List.of("add", "remove", "list");
    private static final List<String> ZONE_ACTIONS = List.of("wand", "create", "delete", "list");

    private final StoneRTP plugin;

    public StoneRTPCommand(StoneRTP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (!sender.hasPermission("stonertp.admin")) {
            mm.sendChat(sender, "general.no-permission", null);
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reload();
                mm.sendChat(sender, "general.reload-success", null);
            }
            case "toggle" -> toggleWorld(sender, args);
            case "blocktime" -> handleBlockTime(sender, args);
            case "zone" -> handleZone(sender, args);
            case "checkupdate" -> {
                mm.sendChat(sender, "update.check-triggered", null);
                plugin.getUpdateChecker().checkNow();
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void toggleWorld(CommandSender sender, String[] args) {
        MessageManager mm = plugin.getMessageManager();
        ConfigManager cfg = plugin.getConfigManager();

        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        String requested = args[1].toLowerCase();
        String worldName = TOGGLE_KEYS.contains(requested) ? cfg.getGuiWorldFor(requested) : args[1];
        if (worldName == null || !cfg.isWorldConfigured(worldName)) {
            mm.sendChat(sender, "rtp.world-not-configured", Map.of("world", mm.escape(args[1])));
            return;
        }

        boolean newState;
        if (args.length >= 3) {
            String state = args[2].toLowerCase();
            if (!TOGGLE_STATES.contains(state)) {
                mm.sendChat(sender, "general.toggle-usage", null);
                return;
            }
            newState = state.equals("on");
        } else {
            newState = !cfg.getWorldSettings(worldName).enabled();
        }

        cfg.setWorldEnabled(worldName, newState);
        plugin.getGuiManager().refreshOpenMenus();

        mm.sendChat(sender, newState ? "rtp.world-toggle-on" : "rtp.world-toggle-off", Map.of("world", worldName));
    }

    private void handleBlockTime(CommandSender sender, String[] args) {
        MessageManager mm = plugin.getMessageManager();
        BlockedTimeManager blockedTimes = plugin.getBlockedTimeManager();
        String action = args.length >= 2 ? args[1].toLowerCase() : "list";

        switch (action) {
            case "list" -> listBlockTimes(sender);
            case "add" -> {
                if (args.length < 3) {
                    mm.sendChat(sender, "blocktime.usage", null);
                    return;
                }
                TimeWindow window;
                try {
                    window = args.length >= 4
                            ? new TimeWindow(TimeWindow.parseTime(args[2]), TimeWindow.parseTime(args[3]))
                            : TimeWindow.parse(args[2]);
                } catch (IllegalArgumentException ex) {
                    mm.sendChat(sender, "blocktime.invalid", null);
                    return;
                }
                Map<String, String> placeholders = Map.of("start", window.formatStart(), "end", window.formatEnd());
                if (!blockedTimes.add(window)) {
                    mm.sendChat(sender, "blocktime.duplicate", placeholders);
                    return;
                }
                mm.sendChat(sender, "blocktime.added", placeholders);
            }
            case "remove" -> {
                if (args.length < 3) {
                    mm.sendChat(sender, "blocktime.usage", null);
                    return;
                }
                TimeWindow removed = null;
                try {
                    removed = blockedTimes.remove(Integer.parseInt(args[2]) - 1);
                } catch (NumberFormatException ignored) {
                }
                if (removed == null) {
                    mm.sendChat(sender, "blocktime.invalid-index", null);
                    return;
                }
                mm.sendChat(sender, "blocktime.removed", Map.of("start", removed.formatStart(), "end", removed.formatEnd()));
            }
            default -> mm.sendChat(sender, "blocktime.usage", null);
        }
    }

    private void listBlockTimes(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();
        BlockedTimeManager blockedTimes = plugin.getBlockedTimeManager();
        List<TimeWindow> windows = blockedTimes.getWindows();

        mm.sendChat(sender, "blocktime.list-header", Map.of("now", blockedTimes.formatNow(), "timezone", blockedTimes.getZoneName()));
        if (windows.isEmpty()) {
            mm.sendRaw(sender, "blocktime.list-empty", null);
            return;
        }
        for (int i = 0; i < windows.size(); i++) {
            TimeWindow window = windows.get(i);
            mm.sendRaw(sender, "blocktime.list-entry", Map.of(
                    "index", String.valueOf(i + 1),
                    "start", window.formatStart(),
                    "end", window.formatEnd(),
                    "active", blockedTimes.isActive(window) ? mm.getRaw("blocktime.active-suffix") : ""
            ));
        }
    }

    private void handleZone(CommandSender sender, String[] args) {
        MessageManager mm = plugin.getMessageManager();
        ZoneManager zones = plugin.getZoneManager();
        String action = args.length >= 2 ? args[1].toLowerCase() : "";

        switch (action) {
            case "list" -> listZones(sender);
            case "delete" -> {
                if (args.length < 3) {
                    mm.sendChat(sender, "zone.usage", null);
                    return;
                }
                if (zones.isStorageBroken()) {
                    mm.sendChat(sender, "zone.storage-broken", null);
                    return;
                }
                RTPZone existing = zones.getZone(args[2]);
                if (existing == null) {
                    mm.sendChat(sender, "zone.not-found", Map.of("name", mm.escape(args[2])));
                    return;
                }
                zones.delete(existing.name());
                mm.sendChat(sender, "zone.deleted", Map.of("name", existing.name()));
            }
            case "wand" -> {
                if (!(sender instanceof Player player)) {
                    mm.sendChat(sender, "general.player-only", null);
                    return;
                }
                player.getInventory().addItem(zones.createWand()).values()
                        .forEach(leftover -> player.getWorld().dropItem(player.getLocation(), leftover));
                mm.sendChat(player, "zone.wand-given", null);
            }
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    mm.sendChat(sender, "general.player-only", null);
                    return;
                }
                createZone(player, args);
            }
            default -> mm.sendChat(sender, "zone.usage", null);
        }
    }

    private void createZone(Player player, String[] args) {
        MessageManager mm = plugin.getMessageManager();
        ZoneManager zones = plugin.getZoneManager();

        if (args.length < 3) {
            mm.sendChat(player, "zone.usage", null);
            return;
        }
        String name = args[2];
        if (!zones.isValidName(name)) {
            mm.sendChat(player, "zone.invalid-name", null);
            return;
        }
        if (zones.isStorageBroken()) {
            mm.sendChat(player, "zone.storage-broken", null);
            return;
        }
        if (zones.getZone(name) != null) {
            mm.sendChat(player, "zone.name-taken", Map.of("name", name));
            return;
        }

        int interval = plugin.getConfigManager().getZoneDefaultIntervalSeconds();
        if (args.length >= 4) {
            try {
                interval = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                interval = 0;
            }
            if (interval < 1) {
                mm.sendChat(player, "zone.invalid-interval", null);
                return;
            }
        }

        Location[] selection = zones.getSelection(player.getUniqueId());
        if (selection == null || selection[0] == null || selection[1] == null) {
            mm.sendChat(player, "zone.no-selection", null);
            return;
        }
        if (!selection[0].getWorld().equals(selection[1].getWorld())) {
            mm.sendChat(player, "zone.different-worlds", null);
            return;
        }

        RTPZone zone = zones.create(name, selection[0], selection[1], interval);
        mm.sendChat(player, "zone.created", Map.of(
                "name", zone.name(),
                "x", String.valueOf(zone.sizeX()),
                "y", String.valueOf(zone.sizeY()),
                "z", String.valueOf(zone.sizeZ()),
                "seconds", String.valueOf(zone.intervalSeconds())
        ));

        World world = selection[0].getWorld();
        if (!zones.isRtpEnabled(world)) {
            mm.sendChat(player, "zone.world-not-enabled", Map.of("world", world.getName()));
        }
    }

    private void listZones(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();
        List<RTPZone> zones = List.copyOf(plugin.getZoneManager().getZones());

        if (zones.isEmpty()) {
            mm.sendChat(sender, "zone.list-empty", null);
            return;
        }
        mm.sendChat(sender, "zone.list-header", Map.of("count", String.valueOf(zones.size())));
        for (RTPZone zone : zones) {
            mm.sendRaw(sender, "zone.list-entry", Map.of(
                    "name", zone.name(),
                    "world", zone.worldName(),
                    "min", zone.minX() + ", " + zone.minY() + ", " + zone.minZ(),
                    "max", zone.maxX() + ", " + zone.maxY() + ", " + zone.maxZ(),
                    "seconds", String.valueOf(zone.intervalSeconds())
            ));
        }
    }

    private void sendHelp(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();
        mm.sendRaw(sender, "help.header", null);
        mm.sendRaw(sender, "help.rtp", null);
        mm.sendRaw(sender, "help.cancel", null);
        mm.sendRaw(sender, "help.player", null);
        mm.sendRaw(sender, "help.back", null);
        mm.sendRaw(sender, "help.toggle", null);
        mm.sendRaw(sender, "help.blocktime", null);
        mm.sendRaw(sender, "help.zone", null);
        mm.sendRaw(sender, "help.reload", null);
        mm.sendRaw(sender, "help.checkupdate", null);
        mm.sendRaw(sender, "help.help", null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("stonertp.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS.stream(), args[0]);
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            return switch (sub) {
                case "toggle" -> filter(TOGGLE_KEYS.stream(), args[1]);
                case "blocktime" -> filter(BLOCKTIME_ACTIONS.stream(), args[1]);
                case "zone" -> filter(ZONE_ACTIONS.stream(), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            if (sub.equals("toggle")) {
                return filter(TOGGLE_STATES.stream(), args[2]);
            }
            if (sub.equals("blocktime") && args[1].equalsIgnoreCase("remove")) {
                int count = plugin.getBlockedTimeManager().getWindows().size();
                return filter(IntStream.rangeClosed(1, count).mapToObj(String::valueOf), args[2]);
            }
            if (sub.equals("zone") && args[1].equalsIgnoreCase("delete")) {
                return filter(plugin.getZoneManager().getZones().stream().map(RTPZone::name), args[2]);
            }
        }
        return List.of();
    }

    private List<String> filter(Stream<String> options, String partial) {
        String lower = partial.toLowerCase();
        return options.filter(option -> option.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
