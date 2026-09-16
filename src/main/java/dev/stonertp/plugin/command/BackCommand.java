package dev.stonertp.plugin.command;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.manager.MessageManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BackCommand implements CommandExecutor {
    private final StoneRTP plugin;

    public BackCommand(StoneRTP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager mm = plugin.getMessageManager();

        if (!(sender instanceof Player player)) {
            mm.sendChat(sender, "general.player-only", null);
            return true;
        }

        if (!player.hasPermission("stonertp.back")) {
            mm.sendChat(player, "general.no-permission", null);
            return true;
        }

        Location location = plugin.getBackLocationManager().get(player);
        if (location == null || location.getWorld() == null) {
            mm.sendChat(player, "back.no-location", null);
            return true;
        }

        player.teleportAsync(location).thenAccept(success -> {
            if (success) {
                mm.sendChat(player, "back.success", null);
            }
        });
        return true;
    }
}
