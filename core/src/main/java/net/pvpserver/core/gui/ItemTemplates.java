package net.pvpserver.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.pvpserver.core.message.MessageService;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds configurable items from a YAML section:
 * <pre>
 * some-item:
 *   material: DIAMOND_SWORD
 *   name: "&lt;primary&gt;Ranked Queue"
 *   lore: ["&lt;gray&gt;Line"]
 *   glow: true
 *   amount: 1
 *   model: 0
 * </pre>
 */
public final class ItemTemplates {

    private ItemTemplates() {
    }

    /**
     * @param section item section (may be null)
     * @param fallback material when missing/invalid
     * @param messages parser
     * @param resolvers placeholders for name and lore
     * @return item
     */
    public static ItemStack build(ConfigurationSection section, Material fallback, MessageService messages, TagResolver... resolvers) {
        if (section == null) {
            return new ItemStack(fallback);
        }
        Material material = material(section.getString("material"), fallback);
        ItemBuilder builder = ItemBuilder.of(material).amount(section.getInt("amount", 1)).hideFlags();
        String name = section.getString("name");
        if (name != null) {
            builder.name(messages.parse(name, resolvers));
        }
        List<Component> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            lore.add(messages.parse(line, resolvers));
        }
        if (!lore.isEmpty()) {
            builder.lore(lore);
        }
        if (section.getBoolean("glow", false)) {
            builder.glow(true);
        }
        builder.customModelData(section.getInt("model", 0));
        return builder.build();
    }

    /**
     * @param config yaml root
     * @param path path of the item section
     * @param fallback fallback material
     * @param messages parser
     * @param resolvers placeholders
     * @return item
     */
    public static ItemStack build(YamlConfiguration config, String path, Material fallback, MessageService messages, TagResolver... resolvers) {
        return build(config.getConfigurationSection(path), fallback, messages, resolvers);
    }

    /**
     * @param name material name (case-insensitive)
     * @param fallback fallback
     * @return material
     */
    public static Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return material == null || !material.isItem() ? fallback : material;
    }
}
