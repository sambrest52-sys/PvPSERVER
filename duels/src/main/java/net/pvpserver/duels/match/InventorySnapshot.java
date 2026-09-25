package net.pvpserver.duels.match;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Frozen copy of a player's inventory and vitals at the moment they died or the match ended, shown in the
 * post-match inventory viewer.
 *
 * @param id snapshot id
 * @param owner player id
 * @param name player name
 * @param contents 36 storage slots
 * @param armor armor (boots first)
 * @param offhand offhand
 * @param health health
 * @param food food level
 * @param effects active potion effects
 * @param hits hits landed
 * @param longestCombo longest combo
 * @param potionsThrown potions thrown
 * @param potionsMissed potions missed
 * @param healthPotions healing potions left
 * @param createdAt capture time
 */
public record InventorySnapshot(UUID id, UUID owner, String name, ItemStack[] contents, ItemStack[] armor, ItemStack offhand,
                                double health, int food, List<PotionEffect> effects, int hits, int longestCombo,
                                int potionsThrown, int potionsMissed, int healthPotions, long createdAt) {

    /**
     * Captures a player.
     *
     * @param player player
     * @param participant match stats (may be null)
     * @return snapshot
     */
    public static InventorySnapshot capture(Player player, MatchParticipant participant) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        ItemStack[] contents = new ItemStack[storage.length];
        int healthPots = 0;
        for (int i = 0; i < storage.length; i++) {
            ItemStack item = storage[i];
            contents[i] = item == null ? null : item.clone();
            if (item != null && (item.getType() == Material.SPLASH_POTION || item.getType() == Material.POTION)
                    && item.getItemMeta() instanceof PotionMeta meta) {
                PotionType type = meta.getBasePotionType();
                if (type == PotionType.HEALING || type == PotionType.STRONG_HEALING) {
                    healthPots += item.getAmount();
                }
            }
        }
        ItemStack[] armor = player.getInventory().getArmorContents().clone();
        for (int i = 0; i < armor.length; i++) {
            armor[i] = armor[i] == null ? null : armor[i].clone();
        }
        ItemStack offhand = player.getInventory().getItemInOffHand().clone();
        return new InventorySnapshot(UUID.randomUUID(), player.getUniqueId(), player.getName(), contents, armor, offhand,
                Math.max(0, player.getHealth()), player.getFoodLevel(), new ArrayList<>(player.getActivePotionEffects()),
                participant == null ? 0 : participant.hits(), participant == null ? 0 : participant.longestCombo(),
                participant == null ? 0 : participant.potionsThrown(), participant == null ? 0 : participant.potionsMissed(),
                healthPots, System.currentTimeMillis());
    }
}
