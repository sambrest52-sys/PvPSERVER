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
 * @param rules gameplay rules
 * @param contents 36 storage slots (0-8 hotbar), null entries allowed
 * @param armor helmet, chestplate, leggings, boots (nullable entries)
 * @param offhand offhand item or null
 * @param effects potion effects applied on spawn
 */
public record Kit(String id, String displayName, ItemStack icon, boolean enabled, boolean ranked, boolean unranked,
                  boolean ffa, boolean editable, int order, String knockback, Set<String> arenaTags,
                  Set<String> arenaWhitelist, KitRules rules, ItemStack[] contents, ItemStack[] armor, ItemStack offhand,
                  List<PotionEffect> effects) {

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
        if (!arenaWhitelist.isEmpty()) {
            return arenaWhitelist.contains(arenaName.toLowerCase(java.util.Locale.ROOT));
        }
        for (String tag : tags) {
            if (arenaTags.contains(tag)) {
                return true;
            }
        }
        return false;
    }
}
