package net.pvpserver.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fluent {@link ItemStack} builder using Adventure components. Names and lore have italics disabled by default.
 */
public final class ItemBuilder {

    private final ItemStack item;

    private ItemBuilder(ItemStack item) {
        this.item = item;
    }

    /**
     * @param material material
     * @return builder
     */
    public static ItemBuilder of(Material material) {
        return new ItemBuilder(new ItemStack(material));
    }

    /**
     * @param item base item (copied)
     * @return builder
     */
    public static ItemBuilder of(ItemStack item) {
        return new ItemBuilder(item.clone());
    }

    /**
     * @param amount stack size
     * @return this
     */
    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize() > 0 ? Math.max(item.getMaxStackSize(), 1) : 64)));
        return this;
    }

    /**
     * @param name display name
     * @return this
     */
    public ItemBuilder name(Component name) {
        return meta(meta -> meta.displayName(noItalic(name)));
    }

    /**
     * @param lore lore lines
     * @return this
     */
    public ItemBuilder lore(List<Component> lore) {
        List<Component> lines = new ArrayList<>(lore.size());
        for (Component line : lore) {
            lines.add(noItalic(line));
        }
        return meta(meta -> meta.lore(lines));
    }

    /**
     * Appends lore lines.
     *
     * @param extra lines
     * @return this
     */
    public ItemBuilder addLore(List<Component> extra) {
        return meta(meta -> {
            List<Component> lines = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            for (Component line : extra) {
                lines.add(noItalic(line));
            }
            meta.lore(lines);
        });
    }

    /**
     * @param glow whether to force the enchantment glint
     * @return this
     */
    public ItemBuilder glow(boolean glow) {
        return meta(meta -> meta.setEnchantmentGlintOverride(glow ? Boolean.TRUE : null));
    }

    /**
     * @param enchantment enchantment
     * @param level level
     * @return this
     */
    public ItemBuilder enchant(Enchantment enchantment, int level) {
        return meta(meta -> meta.addEnchant(enchantment, level, true));
    }

    /**
     * @param unbreakable unbreakable flag
     * @return this
     */
    public ItemBuilder unbreakable(boolean unbreakable) {
        return meta(meta -> meta.setUnbreakable(unbreakable));
    }

    /**
     * Hides attributes, enchants, potion effects and similar tooltip noise.
     *
     * @return this
     */
    public ItemBuilder hideFlags() {
        return meta(meta -> meta.addItemFlags(ItemFlag.values()));
    }

    /**
     * @param type base potion type (for potion materials)
     * @return this
     */
    public ItemBuilder potion(PotionType type) {
        return meta(meta -> {
            if (meta instanceof PotionMeta potionMeta) {
                potionMeta.setBasePotionType(type);
            }
        });
    }

    /**
     * @param color leather armour / potion colour
     * @return this
     */
    public ItemBuilder color(Color color) {
        return meta(meta -> {
            if (meta instanceof LeatherArmorMeta leather) {
                leather.setColor(color);
            } else if (meta instanceof PotionMeta potionMeta) {
                potionMeta.setColor(color);
            }
        });
    }

    /**
     * @param owner skull owner (player heads)
     * @return this
     */
    public ItemBuilder skull(OfflinePlayer owner) {
        return meta(meta -> {
            if (meta instanceof SkullMeta skull) {
                skull.setOwningPlayer(owner);
            }
        });
    }

    /**
     * @param key tag key
     * @param value string value
     * @return this
     */
    public ItemBuilder tag(NamespacedKey key, String value) {
        return meta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value));
    }

    /**
     * @param key tag key
     * @param value int value
     * @return this
     */
    public ItemBuilder tag(NamespacedKey key, int value) {
        return meta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, value));
    }

    /**
     * @param model custom model data (0 = none)
     * @return this
     */
    @SuppressWarnings("deprecation")
    public ItemBuilder customModelData(int model) {
        return model <= 0 ? this : meta(meta -> meta.setCustomModelData(model));
    }

    /**
     * @param consumer arbitrary meta edit
     * @return this
     */
    public ItemBuilder meta(Consumer<ItemMeta> consumer) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            consumer.accept(meta);
            item.setItemMeta(meta);
        }
        return this;
    }

    /** @return the built item */
    public ItemStack build() {
        return item;
    }

    private static Component noItalic(Component component) {
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
