package dev.stonertp.plugin.manager;

import dev.stonertp.plugin.StoneRTP;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {
    private final StoneRTP plugin;
    private Economy economy;

    public EconomyManager(StoneRTP plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        economy = null;
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found - random teleport costs are disabled even if enabled in config.yml.");
            return;
        }
        RegisteredServiceProvider<Economy> provider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            plugin.getLogger().warning("Vault is installed but no economy plugin is registered - random teleport costs are disabled.");
            return;
        }
        economy = provider.getProvider();
        plugin.getLogger().info("Vault found - random teleport costs are now active if enabled in config.yml.");
    }

    public boolean isPresent() {
        return economy != null;
    }

    public boolean canApplyCost() {
        return isPresent() && plugin.getConfigManager().isCostEnabled();
    }

    public boolean canAfford(Player player, double amount) {
        if (!isPresent()) {
            return true;
        }
        return economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (!isPresent() || amount <= 0) {
            return true;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public void refund(Player player, double amount) {
        if (!isPresent() || amount <= 0) {
            return;
        }
        economy.depositPlayer(player, amount);
    }

    public String format(double amount) {
        if (!isPresent()) {
            return String.valueOf(amount);
        }
        return economy.format(amount);
    }
}
