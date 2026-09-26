package dev.stonertp.plugin.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public static ItemBuilder playerHead() {
        return new ItemBuilder(Material.PLAYER_HEAD);
    }

    public ItemBuilder name(Component name) {
        meta.displayName(name);
        return this;
    }

    public ItemBuilder lore(List<Component> lore) {
        meta.lore(lore);
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        meta.setEnchantmentGlintOverride(glow);
        return this;
    }

    public ItemBuilder headOwner(OfflinePlayer player) {
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
        }
        return this;
    }

    public ItemBuilder headTexture(String base64Texture) {
        if (base64Texture == null || base64Texture.isBlank()) {
            return this;
        }
        if (meta instanceof SkullMeta skullMeta) {
            try {
                PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                profile.setProperty(new ProfileProperty("textures", base64Texture));
                skullMeta.setPlayerProfile(profile);
            } catch (Exception ignored) {
            }
        }
        return this;
    }

    public ItemBuilder hideAttributes() {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        return this;
    }

    public ItemBuilder data(NamespacedKey key, String value) {
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
