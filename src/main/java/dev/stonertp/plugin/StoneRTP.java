package dev.stonertp.plugin;

import dev.stonertp.plugin.command.RTPCommand;
import dev.stonertp.plugin.command.StoneRTPCommand;
import dev.stonertp.plugin.listener.GUIListener;
import dev.stonertp.plugin.listener.PlayerDamageListener;
import dev.stonertp.plugin.listener.PlayerMoveListener;
import dev.stonertp.plugin.command.BackCommand;
import dev.stonertp.plugin.listener.JoinListener;
import dev.stonertp.plugin.listener.QuitListener;
import dev.stonertp.plugin.listener.ZoneWandListener;
import dev.stonertp.plugin.manager.BackLocationManager;
import dev.stonertp.plugin.manager.BlockedTimeManager;
import dev.stonertp.plugin.manager.ConfigManager;
import dev.stonertp.plugin.manager.CooldownManager;
import dev.stonertp.plugin.manager.EconomyManager;
import dev.stonertp.plugin.manager.EffectManager;
import dev.stonertp.plugin.manager.GUIManager;
import dev.stonertp.plugin.manager.MessageManager;
import dev.stonertp.plugin.manager.NotificationManager;
import dev.stonertp.plugin.manager.SafeLocationFinder;
import dev.stonertp.plugin.manager.TeleportManager;
import dev.stonertp.plugin.manager.UpdateChecker;
import dev.stonertp.plugin.manager.ZoneManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class StoneRTP extends JavaPlugin {
    private ConfigManager configManager;
    private MessageManager messageManager;
    private EconomyManager economyManager;
    private CooldownManager cooldownManager;
    private EffectManager effectManager;
    private SafeLocationFinder safeLocationFinder;
    private NotificationManager notificationManager;
    private TeleportManager teleportManager;
    private GUIManager guiManager;
    private BackLocationManager backLocationManager;
    private UpdateChecker updateChecker;
    private BlockedTimeManager blockedTimeManager;
    private ZoneManager zoneManager;
    private org.bukkit.scheduler.BukkitTask cooldownPruneTask;

    @Override
    public void onEnable() {
        getLogger().info("Loading configuration...");
        configManager = new ConfigManager(this);
        configManager.load();

        getLogger().info("Loading messages (" + configManager.getLanguage() + ")...");
        messageManager = new MessageManager(this);
        messageManager.load();

        getLogger().info("Checking for Vault...");
        economyManager = new EconomyManager(this);
        economyManager.setup();

        cooldownManager = new CooldownManager(this);
        effectManager = new EffectManager(this);
        safeLocationFinder = new SafeLocationFinder(this);
        notificationManager = new NotificationManager(this);
        teleportManager = new TeleportManager(this);
        guiManager = new GUIManager(this);
        backLocationManager = new BackLocationManager();
        updateChecker = new UpdateChecker(this);
        blockedTimeManager = new BlockedTimeManager(this);
        blockedTimeManager.load();
        zoneManager = new ZoneManager(this);
        zoneManager.load();

        getLogger().info("Registering commands...");
        registerCommands();

        getLogger().info("Registering listeners...");
        registerListeners();

        getLogger().info("Starting update checker...");
        updateChecker.start();

        cooldownPruneTask = Bukkit.getScheduler().runTaskTimer(this, cooldownManager::pruneExpired, 20L * 60 * 10, 20L * 60 * 10);
        zoneManager.start();

        getLogger().info("Stone RTP has been enabled - /rtp is ready to use.");
    }

    @Override
    public void onDisable() {
        if (updateChecker != null) {
            updateChecker.stop();
        }
        if (cooldownPruneTask != null) {
            cooldownPruneTask.cancel();
        }
        if (zoneManager != null) {
            zoneManager.stop();
        }
        getLogger().info("Stone RTP has been disabled.");
    }

    public void reload() {
        configManager.reload();
        messageManager.load();
        economyManager.setup();
        blockedTimeManager.load();
        zoneManager.load();
        updateChecker.start();
    }

    private void registerCommands() {
        RTPCommand rtpCommand = new RTPCommand(this);
        getCommand("rtp").setExecutor((CommandExecutor) rtpCommand);
        getCommand("rtp").setTabCompleter((TabCompleter) rtpCommand);

        StoneRTPCommand stoneRTPCommand = new StoneRTPCommand(this);
        getCommand("stonertp").setExecutor((CommandExecutor) stoneRTPCommand);
        getCommand("stonertp").setTabCompleter((TabCompleter) stoneRTPCommand);

        BackCommand backCommand = new BackCommand(this);
        getCommand("back").setExecutor((CommandExecutor) backCommand);
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents((Listener) new PlayerMoveListener(this), (Plugin) this);
        pm.registerEvents((Listener) new PlayerDamageListener(this), (Plugin) this);
        pm.registerEvents((Listener) new GUIListener(this), (Plugin) this);
        pm.registerEvents((Listener) new JoinListener(this), (Plugin) this);
        pm.registerEvents((Listener) new QuitListener(this), (Plugin) this);
        pm.registerEvents(new ZoneWandListener(this), this);
        pm.registerEvents((Listener) updateChecker, (Plugin) this);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public EffectManager getEffectManager() {
        return effectManager;
    }

    public SafeLocationFinder getSafeLocationFinder() {
        return safeLocationFinder;
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public BackLocationManager getBackLocationManager() {
        return backLocationManager;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public BlockedTimeManager getBlockedTimeManager() {
        return blockedTimeManager;
    }

    public ZoneManager getZoneManager() {
        return zoneManager;
    }
}
