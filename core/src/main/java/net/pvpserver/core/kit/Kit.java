package net.pvpserver.core.kit;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.List;
import java.util.Set;

/**
 * Immutable kit definition from kits.yml.
 *
 * @param id identifier (lowercase)
 * @param displayName MiniMessage display name
 * @param icon menu icon
 * @param enabled whether the kit is usable at all
 * @param ranked available in ranked queues
 * @param unranked available in unranked queues / duels
 * @param ffa available in FFA
 * @param editable whether players may edit the layout
 * @param order menu sort order
 * @param knockback knockback profile id ("" = default)
 * @param arenaTags arena tags this kit can be played on
 * @param arenaWhitelist explicit arena names (overrides tags when non-empty)
 * @param arenaBlacklist arena names this kit is never played on
 * @param rules gameplay rules
 * @param contents 36 storage slots (0-8 hotbar), null entries allowed
 * @param armor helmet, chestplate, leggings, boots (nullable entries)
 * @param offhand offhand item or null
 * @param effects potion effects applied on spawn
 * @param description MiniMessage lines shown under the kit in menus
 * @param layoutHash fingerprint of which item sits in which slot; saved layouts made for another fingerprint are
 *                   stale (the kit changed) and fall back to the default layout
 */
public record Kit(String id, String displayName, ItemStack icon, boolean enabled, boolean ranked, boolean unranked,
                  boolean ffa, boolean editable, int order, String knockback, Set<String> arenaTags,
                  Set<String> arenaWhitelist, Set<String> arenaBlacklist, KitRules rules, ItemStack[] contents,
                  ItemStack[] armor, ItemStack offhand, List<PotionEffect> effects, List<String> description,
                  String layoutHash) {

    /** @return parsed display name component */
    public Component name() {
        return net.pvpserver.core.message.MessageService.mini().deserialize(displayName);
    }

    /** @return plain text display name */
    public String plainName() {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name());
    }

    /**
     * @param arenaName arena name
     * @param tags arena tags
     * @return whether the arena may host this kit
     */
    public boolean allowsArena(String arenaName, Set<String> tags) {
        String name = arenaName.toLowerCase(java.util.Locale.ROOT);
        if (arenaBlacklist.contains(name)) {
            return false;
        }
        if (!arenaWhitelist.isEmpty()) {
            return arenaWhitelist.contains(name);
        }
        for (String tag : tags) {
            if (arenaTags.contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Menu lore for this kit: the description, a blank line, then the menu's own lines.
     *
     * @param extra menu-specific lines (may be empty)
     * @return lore lines
     */
    public List<Component> lore(List<Component> extra) {
        List<Component> lines = new java.util.ArrayList<>(description.size() + extra.size() + 1);
        for (String line : description) {
            lines.add(net.pvpserver.core.message.MessageService.mini().deserialize(line));
        }
        boolean extraStartsBlank = !extra.isEmpty() && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(extra.get(0)).isBlank();
        if (!description.isEmpty() && !extra.isEmpty() && !extraStartsBlank) {
            lines.add(Component.empty());
        }
        lines.addAll(extra);
        return lines;
    }

    /**
     * Fingerprint of the slot contents (material and amount per slot). Enchantments and names are ignored, so only
     * changes that move or replace items invalidate saved layouts.
     *
     * @param contents 36 storage slots
     * @return short hexadecimal hash
     */
    public static String layoutHash(ItemStack[] contents) {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null) {
                key.append(i).append('=').append(item.getType().name()).append('x').append(item.getAmount()).append(';');
            }
        }
        return Integer.toHexString(key.toString().hashCode());
    }
}
