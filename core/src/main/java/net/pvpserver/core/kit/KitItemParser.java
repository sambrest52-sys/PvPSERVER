package net.pvpserver.core.kit;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.message.MessageService;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Parses kit items and effects from YAML. Item sections support:
 * {@code material, amount, name, lore, enchants (list of "key:level"), unbreakable, potion, effects (custom potion
 * effects, "type:amplifier:seconds"), color, glow, tag}.
 */
public final class KitItemParser {

    /** PDC key marking special items (e.g. golden heads). */
    public static final String SPECIAL_TAG = "special";

    private final Logger logger;
    private final NamespacedKey specialKey;

    /**
     * @param logger logger for invalid entries
     * @param specialKey PDC key for special item tags
     */
    public KitItemParser(Logger logger, NamespacedKey specialKey) {
        this.logger = logger;
        this.specialKey = specialKey;
    }

    /**
     * @param kitId kit id for error messages
     * @param section item section
     * @return item or null if invalid
     */
    public ItemStack parse(String kitId, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return parse(kitId, section.getValues(false));
    }

    /**
     * @param kitId kit id for error messages
     * @param map item map (from a YAML list entry)
     * @return item or null if invalid
     */
    public ItemStack parse(String kitId, Map<?, ?> map) {
        Object materialName = map.get("material");
        Material material = materialName == null ? null : Material.matchMaterial(String.valueOf(materialName).toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem() || material == Material.AIR) {
            logger.warning("Kit " + kitId + ": invalid material '" + materialName + "'");
            return null;
        }
        ItemBuilder builder = ItemBuilder.of(material);
        Object amount = map.get("amount");
        if (amount instanceof Number number) {
            builder.amount(number.intValue());
        }
        Object name = map.get("name");
        if (name != null) {
            builder.name(MessageService.mini().deserialize(String.valueOf(name)));
        }
        Object lore = map.get("lore");
        if (lore instanceof List<?> lines) {
            builder.lore(lines.stream().map(l -> MessageService.mini().deserialize(String.valueOf(l))).toList());
        }
        Object enchants = map.get("enchants");
        if (enchants instanceof List<?> list) {
            for (Object entry : list) {
                applyEnchant(kitId, builder, String.valueOf(entry));
            }
        } else if (enchants instanceof Map<?, ?> enchantMap) {
            for (Map.Entry<?, ?> entry : enchantMap.entrySet()) {
                applyEnchant(kitId, builder, entry.getKey() + ":" + entry.getValue());
            }
        }
        if (Boolean.TRUE.equals(map.get("unbreakable"))) {
            builder.unbreakable(true).meta(meta -> meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE));
        }
        Object potion = map.get("potion");
        if (potion != null) {
            PotionType type = potionType(String.valueOf(potion));
            if (type == null) {
                logger.warning("Kit " + kitId + ": invalid potion type '" + potion + "'");
            } else {
                builder.potion(type);
            }
        }
        Object potionEffects = map.get("effects");
        if (potionEffects instanceof List<?> list && !list.isEmpty()) {
            List<PotionEffect> custom = parseEffects(kitId, list.stream().map(String::valueOf).toList());
            builder.meta(meta -> {
                if (meta instanceof PotionMeta potionMeta) {
                    for (PotionEffect effect : custom) {
                        potionMeta.addCustomEffect(effect.withParticles(true).withIcon(true), true);
                    }
                    if (!potionMeta.hasColor() && !custom.isEmpty()) {
                        potionMeta.setColor(custom.get(0).getType().getColor());
                    }
                } else {
                    logger.warning("Kit " + kitId + ": 'effects' only applies to potions (" + material + ")");
                }
            });
        }
        Object color = map.get("color");
        if (color != null) {
            try {
                builder.color(Color.fromRGB(Integer.parseInt(String.valueOf(color).replace("#", ""), 16)));
            } catch (IllegalArgumentException e) {
                logger.warning("Kit " + kitId + ": invalid color '" + color + "'");
            }
        }
        if (Boolean.TRUE.equals(map.get("glow"))) {
            builder.glow(true);
        }
        Object tag = map.get("tag");
        if (tag != null) {
            builder.tag(specialKey, String.valueOf(tag).toLowerCase(Locale.ROOT));
        }
        return builder.build();
    }

    private void applyEnchant(String kitId, ItemBuilder builder, String raw) {
        String[] parts = raw.split(":");
        String key = parts[0].trim().toLowerCase(Locale.ROOT);
        int level = parts.length > 1 ? parseInt(parts[1].trim(), 1) : 1;
        Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.minecraft(key));
        if (enchantment == null) {
            logger.warning("Kit " + kitId + ": unknown enchantment '" + key + "'");
            return;
        }
        builder.enchant(enchantment, level);
    }

    /**
     * Parses effects written as {@code "speed:1:-1"} (type, amplifier starting at 0, seconds or -1 for infinite).
     *
     * @param kitId kit id
     * @param raw entries
     * @return effects
     */
    public List<PotionEffect> parseEffects(String kitId, List<String> raw) {
        List<PotionEffect> effects = new ArrayList<>();
        for (String entry : raw) {
            String[] parts = entry.split(":");
            PotionEffectType type = RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT)
                    .get(NamespacedKey.minecraft(parts[0].trim().toLowerCase(Locale.ROOT)));
            if (type == null) {
                logger.warning("Kit " + kitId + ": unknown effect '" + parts[0] + "'");
                continue;
            }
            int amplifier = parts.length > 1 ? parseInt(parts[1].trim(), 0) : 0;
            int seconds = parts.length > 2 ? parseInt(parts[2].trim(), -1) : -1;
            int ticks = seconds < 0 ? PotionEffect.INFINITE_DURATION : seconds * 20;
            effects.add(new PotionEffect(type, ticks, amplifier, false, false, true));
        }
        return effects;
    }

    private static PotionType potionType(String raw) {
        String key = raw.trim().toLowerCase(Locale.ROOT);
        PotionType type = RegistryAccess.registryAccess().getRegistry(RegistryKey.POTION).get(NamespacedKey.minecraft(key));
        if (type != null) {
            return type;
        }
        try {
            return PotionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
