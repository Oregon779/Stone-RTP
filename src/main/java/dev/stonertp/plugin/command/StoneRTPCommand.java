package dev.stonertp.plugin.command;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.manager.ConfigManager;
import dev.stonertp.plugin.manager.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StoneRTPCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("reload", "toggle", "help", "checkupdate");
    private static final List<String> TOGGLE_KEYS = List.of("overworld", "nether", "end");
    private static final List<String> TOGGLE_STATES = List.of("on", "off");

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
            case "checkupdate" -> {
                mm.sendChat(sender, "update.check-triggered", null);
                plugin.getUpdateChecker().checkNow();
            }
            case "help" -> sendHelp(sender);
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
            mm.sendChat(sender, "rtp.world-not-configured", Map.of("world", args[1]));
            return;
        }

        boolean newState;
        if (args.length >= 3) {
            newState = args[2].equalsIgnoreCase("on");
        } else {
            newState = !cfg.getWorldSettings(worldName).enabled();
        }

        cfg.setWorldEnabled(worldName, newState);
        plugin.getGuiManager().refreshOpenMenus();

        mm.sendChat(sender, newState ? "rtp.world-toggle-on" : "rtp.world-toggle-off", Map.of("world", worldName));
    }

    private void sendHelp(CommandSender sender) {
        MessageManager mm = plugin.getMessageManager();
        mm.sendRaw(sender, "help.header", null);
        mm.sendRaw(sender, "help.rtp", null);
        mm.sendRaw(sender, "help.cancel", null);
        mm.sendRaw(sender, "help.player", null);
        mm.sendRaw(sender, "help.back", null);
        mm.sendRaw(sender, "help.toggle", null);
        mm.sendRaw(sender, "help.reload", null);
        mm.sendRaw(sender, "help.checkupdate", null);
        mm.sendRaw(sender, "help.help", null);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("stonertp.admin")) {
            return Stream.<String>empty().collect(Collectors.toList());
        }
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            String partial = args[1].toLowerCase();
            return TOGGLE_KEYS.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("toggle")) {
            String partial = args[2].toLowerCase();
            return TOGGLE_STATES.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        return Stream.<String>empty().collect(Collectors.toList());
    }
}
