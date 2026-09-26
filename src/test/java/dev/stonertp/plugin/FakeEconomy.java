package dev.stonertp.plugin;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Minimal in-memory Vault economy: only the calls Stone RTP makes are implemented. */
public final class FakeEconomy {
    private final Map<UUID, Double> balances = new HashMap<>();
    private boolean failWithdrawals;

    public static FakeEconomy install(ServerMock server) {
        FakeEconomy economy = new FakeEconomy();
        Plugin vault = MockBukkit.createMockPlugin("Vault");
        Economy proxy = (Economy) Proxy.newProxyInstance(Economy.class.getClassLoader(), new Class<?>[]{Economy.class},
                (self, method, args) -> economy.handle(method.getName(), args));
        server.getServicesManager().register(Economy.class, proxy, vault, ServicePriority.Normal);
        return economy;
    }

    private Object handle(String method, Object[] args) {
        return switch (method) {
            case "isEnabled" -> true;
            case "getName" -> "FakeEconomy";
            case "format" -> String.format("$%.2f", (Double) args[0]);
            case "has" -> balance((OfflinePlayer) args[0]) >= (Double) args[1];
            case "getBalance" -> balance((OfflinePlayer) args[0]);
            case "withdrawPlayer" -> withdraw((OfflinePlayer) args[0], (Double) args[1]);
            case "depositPlayer" -> deposit((OfflinePlayer) args[0], (Double) args[1]);
            case "hashCode" -> System.identityHashCode(this);
            case "equals" -> args[0] == this;
            case "toString" -> "FakeEconomy";
            default -> throw new UnsupportedOperationException("FakeEconomy does not implement " + method);
        };
    }

    private EconomyResponse withdraw(OfflinePlayer player, double amount) {
        if (failWithdrawals) {
            return new EconomyResponse(0, balance(player), EconomyResponse.ResponseType.FAILURE, "declined");
        }
        balances.put(player.getUniqueId(), balance(player) - amount);
        return new EconomyResponse(amount, balance(player), EconomyResponse.ResponseType.SUCCESS, "");
    }

    private EconomyResponse deposit(OfflinePlayer player, double amount) {
        balances.put(player.getUniqueId(), balance(player) + amount);
        return new EconomyResponse(amount, balance(player), EconomyResponse.ResponseType.SUCCESS, "");
    }

    public double balance(OfflinePlayer player) {
        return balances.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void setBalance(OfflinePlayer player, double amount) {
        balances.put(player.getUniqueId(), amount);
    }

    public void setFailWithdrawals(boolean failWithdrawals) {
        this.failWithdrawals = failWithdrawals;
    }
}
