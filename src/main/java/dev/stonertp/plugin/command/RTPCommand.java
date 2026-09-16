package dev.stonertp.plugin.command;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.manager.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RTPCommand implements CommandExecutor, TabCompleter {

    private final StoneRTP plugin;

    public RTPCommand(StoneRTP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (!(sender instanceof Player player)) {
            mm.sendChat(sender, "general.player-only", null);
            return true;
        }

        if (!player.hasPermission("stonertp.use")) {
            mm.sendChat(player, "general.no-permission", null);
            return true;
        }

        if (args.length == 0) {
            openMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "cancel" -> plugin.getTeleportManager().cancel(player, "rtp.cancelled-generic");
            case "player" -> teleportOther(player, args);
            default -> plugin.getTeleportManager().startRTP(player, args[0]);
        }
        return true;
    }

    private void teleportOther(Player sender, String[] args) {
        MessageManager mm = plugin.getMessageManager();
        if (!sender.hasPermission("stonertp.admin")) {
            mm.sendChat(sender, "general.no-permission", null);
            return;
        }
        if (args.length < 2) {
            mm.sendChat(sender, "general.unknown-subcommand", null);
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            mm.sendChat(sender, "general.player-not-found", Map.of("player", args[1]));
            return;
        }
        String world = args.length >= 3 ? args[2] : target.getWorld().getName();
        mm.sendChat(sender, "rtp.other-triggered", Map.of("player", target.getName()));
        plugin.getTeleportManager().startRTP(target, world);
    }

    private void openMenu(Player player) {
        MessageManager mm = plugin.getMessageManager();
        if (!player.hasPermission("stonertp.gui")) {
            mm.sendChat(player, "general.no-permission", null);
            return;
        }
        if (!plugin.getConfigManager().isGuiEnabled()) {
            mm.sendChat(player, "general.unknown-subcommand", null);
            return;
        }
        plugin.getGuiManager().open(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            Stream<String> worldNames = Bukkit.getWorlds().stream().map(World::getName);
            Stream<String> keywords = sender.hasPermission("stonertp.admin")
                    ? Stream.of("cancel", "player")
                    : Stream.of("cancel");
            return Stream.concat(worldNames, keywords)
                    .filter(s -> s.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("player") && sender.hasPermission("stonertp.admin")) {
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Stream.<String>empty().collect(Collectors.toList());
    }
}
