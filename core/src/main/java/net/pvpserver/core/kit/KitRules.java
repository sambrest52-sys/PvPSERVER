package net.pvpserver.core.kit;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Gameplay rules attached to a kit. Parsed from the {@code rules} section of a kit in kits.yml.
 *
 * @param hunger whether food depletes
 * @param regen regeneration mode
 * @param build whether blocks may be placed
 * @param breakPlacedOnly whether only blocks placed during the match may be broken (else any block in bounds)
 * @param healthDisplay show health under player names
 * @param hitDelay maximum no-damage ticks (vanilla 20; hits land every hitDelay/2 ticks)
 * @param oldCombat remove the attack cooldown (1.8 style clicking)
 * @param fallDamage whether fall damage applies
 * @param pearlCooldown ender pearl cooldown seconds (0 = none)
 * @param gappleCooldown golden apple cooldown seconds (0 = none)
 * @param potionVelocity splash potion throw velocity multiplier (1.0 = vanilla)
 * @param noDamage cancel all damage (boxing/sumo still register hits and knockback)
 * @param sumo sumo rules: touching water or falling below the arena loses the round
 * @param boxing boxing rules: first to {@code boxingHits} hits wins
 * @param boxingHits hits required to win boxing
 * @param bridge bridge rules: score by jumping into the enemy goal
 * @param bridgeGoals goals required to win a bridge game
 * @param rounds default rounds to win (best-of = rounds*2-1)
 * @param maxDuration seconds before a match ends as a draw (0 = unlimited)
 * @param dropItems whether players can drop items
 * @param deathDropsLoot whether killed FFA players drop loot (off by default)
 * @param soup soup rules: right-clicking mushroom stew heals instantly and leaves no bowl
 * @param soupHeal health restored per soup (half hearts)
 * @param breakable block types that may be broken during a match regardless of who placed them (spleef);
 *                  empty = decided by {@code build} and {@code breakPlacedOnly}
 * @param arrowRegen seconds after shooting until a used arrow is given back (bridge); 0 = off
 */
public record KitRules(boolean hunger, RegenMode regen, boolean build, boolean breakPlacedOnly, boolean healthDisplay,
                       int hitDelay, boolean oldCombat, boolean fallDamage, int pearlCooldown, int gappleCooldown,
                       double potionVelocity, boolean noDamage, boolean sumo, boolean boxing, int boxingHits,
                       boolean bridge, int bridgeGoals, int rounds, int maxDuration, boolean dropItems,
                       boolean deathDropsLoot, boolean soup, double soupHeal, Set<Material> breakable, double arrowRegen) {

    /** Rules for kits without a rules section. */
    public static final KitRules DEFAULT = new KitRules(true, RegenMode.VANILLA, false, true, true, 20, false, true,
            15, 0, 1.0, false, false, false, 100, false, 3, 1, 900, false, false, false, 7.0, Set.of(), 0.0);

    /** Keys accepted in a kit's {@code rules} section (unknown keys are reported when kits load). */
    public static final Set<String> KEYS = Set.of("hunger", "regen", "build", "break-placed-only", "health-display",
            "hit-delay", "old-combat", "fall-damage", "pearl-cooldown", "gapple-cooldown", "potion-velocity", "no-damage",
            "sumo", "boxing", "boxing-hits", "bridge", "bridge-goals", "rounds", "max-duration", "drop-items",
            "death-drops-loot", "soup", "soup-heal", "breakable", "arrow-regen");

    /**
     * @param material block type
     * @param placedThisMatch whether a player placed that block during the match
     * @return whether a fighter may break the block (the arena bounds are checked separately)
     */
    public boolean canBreak(Material material, boolean placedThisMatch) {
        if (!breakable.isEmpty()) {
            return breakable.contains(material) || (build && placedThisMatch);
        }
        return build && (!breakPlacedOnly || placedThisMatch);
    }

    /**
     * @param section rules section, may be null
     * @return parsed rules with defaults for missing keys
     */
    public static KitRules parse(ConfigurationSection section) {
        if (section == null) {
            return DEFAULT;
        }
        RegenMode regen;
        try {
            regen = RegenMode.valueOf(section.getString("regen", "VANILLA").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            regen = RegenMode.VANILLA;
        }
        return new KitRules(
                section.getBoolean("hunger", DEFAULT.hunger),
                regen,
                section.getBoolean("build", DEFAULT.build),
                section.getBoolean("break-placed-only", DEFAULT.breakPlacedOnly),
                section.getBoolean("health-display", DEFAULT.healthDisplay),
                Math.max(0, section.getInt("hit-delay", DEFAULT.hitDelay)),
                section.getBoolean("old-combat", DEFAULT.oldCombat),
                section.getBoolean("fall-damage", DEFAULT.fallDamage),
                Math.max(0, section.getInt("pearl-cooldown", DEFAULT.pearlCooldown)),
                Math.max(0, section.getInt("gapple-cooldown", DEFAULT.gappleCooldown)),
                section.getDouble("potion-velocity", DEFAULT.potionVelocity),
                section.getBoolean("no-damage", DEFAULT.noDamage),
                section.getBoolean("sumo", DEFAULT.sumo),
                section.getBoolean("boxing", DEFAULT.boxing),
                Math.max(1, section.getInt("boxing-hits", DEFAULT.boxingHits)),
                section.getBoolean("bridge", DEFAULT.bridge),
                Math.max(1, section.getInt("bridge-goals", DEFAULT.bridgeGoals)),
                Math.max(1, section.getInt("rounds", DEFAULT.rounds)),
                Math.max(0, section.getInt("max-duration", DEFAULT.maxDuration)),
                section.getBoolean("drop-items", DEFAULT.dropItems),
                section.getBoolean("death-drops-loot", DEFAULT.deathDropsLoot),
                section.getBoolean("soup", DEFAULT.soup),
                Math.max(0.5, section.getDouble("soup-heal", DEFAULT.soupHeal)),
                materials(section.getStringList("breakable")),
                Math.max(0.0, section.getDouble("arrow-regen", DEFAULT.arrowRegen)));
    }

    private static Set<Material> materials(List<String> names) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                set.add(material);
            }
        }
        return Collections.unmodifiableSet(set);
    }
}
