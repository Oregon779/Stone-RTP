package dev.stonertp.plugin.manager;

import dev.stonertp.plugin.StoneRTP;
import dev.stonertp.plugin.model.RTPWorldSettings;
import dev.stonertp.plugin.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GUIManager {
    private static final String[] SLOT_KEYS = {"overworld", "nether", "end"};

    private final StoneRTP plugin;
    private final NamespacedKey worldKey;
    // The exact menu inventory each viewer has open; a click matches only that instance, never a same-titled chest.
    private final Map<UUID, Inventory> openMenus = new HashMap<>();

    public GUIManager(StoneRTP plugin) {
        this.plugin = plugin;
        this.worldKey = new NamespacedKey(plugin, "rtp-gui-world");
    }

    public NamespacedKey getWorldKey() {
        return worldKey;
    }

    public void open(Player player) {
        MessageManager mm = plugin.getMessageManager();
        ConfigManager cfg = plugin.getConfigManager();

        Inventory inventory = Bukkit.createInventory(null, cfg.getGuiSize(), mm.format(mm.getRaw("gui.title"), null));
        populate(inventory, player);

        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), inventory);

        Sound openSound = cfg.getSound("gui.sound-open", Sound.BLOCK_ENDER_CHEST_OPEN);
        player.playSound(player.getLocation(), openSound, 1.0f, 1.0f);
    }

    public void untrack(UUID uuid) {
        openMenus.remove(uuid);
    }

    public boolean isOpenMenu(HumanEntity viewer, Inventory topInventory) {
        Inventory menu = openMenus.get(viewer.getUniqueId());
        return menu != null && menu.equals(topInventory);
    }

    public void refreshOpenMenus() {
        for (Map.Entry<UUID, Inventory> entry : new ArrayList<>(openMenus.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !isOpenMenu(player, player.getOpenInventory().getTopInventory())) {
                openMenus.remove(entry.getKey());
                continue;
            }
            populate(entry.getValue(), player);
            player.updateInventory();
        }
    }

    // After a plugin reload the new instance doesn't know the old menus, so their items would become takeable.
    public void closeAll() {
        for (Map.Entry<UUID, Inventory> entry : new ArrayList<>(openMenus.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && isOpenMenu(player, player.getOpenInventory().getTopInventory())) {
                player.closeInventory();
            }
        }
        openMenus.clear();
    }

    private void populate(Inventory inventory, Player viewer) {
        ConfigManager cfg = plugin.getConfigManager();
        if (cfg.isFillerEnabled()) {
            fillBorder(inventory, cfg.getGuiFillerMaterials(), cfg.getGuiCornerMaterial());
        }
        for (String key : SLOT_KEYS) {
            placeWorldItem(inventory, key);
        }
    }

    private void fillBorder(Inventory inventory, List<String> outerMaterials, String cornerMaterial) {
        List<ItemStack> fillers = new ArrayList<>();
        for (String materialName : outerMaterials) {
            fillers.add(createFiller(materialName));
        }
        if (fillers.isEmpty()) {
            return;
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fillers.get(i % fillers.size()));
        }

        int size = inventory.getSize();
        int rows = size / 9;
        if (rows < 2 || cornerMaterial == null || cornerMaterial.isBlank()) {
            return;
        }
        ItemStack corner = createFiller(cornerMaterial);
        int[] corners = {0, 8, size - 9, size - 1};
        for (int slot : corners) {
            inventory.setItem(slot, corner);
        }
    }

    private void placeWorldItem(Inventory inventory, String key) {
        ConfigManager cfg = plugin.getConfigManager();
        MessageManager mm = plugin.getMessageManager();

        String worldName = cfg.getGuiWorldFor(key);
        int slot = cfg.getGuiSlotFor(key);
        if (worldName == null || slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        RTPWorldSettings settings = cfg.getWorldSettings(worldName);
        boolean available = Bukkit.getWorld(worldName) != null && settings.enabled() && settings.isConfigured();

        String namePath = available ? "gui." + key + ".name" : "gui.world-disabled.name";
        String lorePath = available ? "gui." + key + ".lore" : "gui.world-disabled.lore";

        ItemBuilder builder;
        String headTexture = cfg.getGuiHeadTextureFor(key);
        if (headTexture != null && !headTexture.isBlank()) {
            builder = ItemBuilder.playerHead().headTexture(headTexture);
        } else {
            builder = new ItemBuilder(parseMaterial(available ? cfg.getGuiMaterialFor(key) : "BARRIER"));
        }

        builder.name(mm.format(mm.getRaw(namePath), null))
                .lore(buildLore(lorePath, available ? worldLorePlaceholders() : Map.of()))
                .hideAttributes();

        if (available) {
            builder.data(worldKey, worldName);
            if (cfg.isGuiGlowFor(key)) {
                builder.glow(true);
            }
        }

        inventory.setItem(slot, builder.build());
    }

    private Map<String, String> worldLorePlaceholders() {
        ConfigManager cfg = plugin.getConfigManager();
        EconomyManager economy = plugin.getEconomyManager();
        MessageManager mm = plugin.getMessageManager();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("cost", economy.canApplyCost()
                ? economy.format(cfg.getCostAmount())
                : mm.getRaw("gui.free-label"));
        placeholders.put("cooldown", plugin.getCooldownManager().formatRemaining(cfg.getCooldownSeconds()));
        return placeholders;
    }

    private List<Component> buildLore(String path, Map<String, String> placeholders) {
        MessageManager mm = plugin.getMessageManager();
        List<Component> lore = new ArrayList<>();
        for (String line : mm.getRawList(path)) {
            lore.add(mm.format(line, placeholders));
        }
        return lore;
    }

    private ItemStack createFiller(String materialName) {
        ItemStack filler = new ItemStack(parseMaterial(materialName));
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.empty());
        filler.setItemMeta(meta);
        return filler;
    }

    private Material parseMaterial(String raw) {
        try {
            return Material.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Material.STONE;
        }
    }
}
