package net.pvpserver.core.combat;

import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.kit.KitService;
import net.pvpserver.core.knockback.KnockbackProfile;
import net.pvpserver.core.knockback.KnockbackService;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies per-kit combat settings (attack speed, hit delay, knockback profile) and owns cooldowns and combat tags.
 */
public final class CombatService {

    private static final double VANILLA_ATTACK_SPEED = 4.0;

    private final KitService kits;
    private final KnockbackService knockback;
    private final CombatTagService tags;
    private final Cooldowns cooldowns = new Cooldowns();
    private final Map<UUID, Kit> activeKits = new ConcurrentHashMap<>();
    private final Map<UUID, String> knockbackOverrides = new ConcurrentHashMap<>();
    private double oldCombatAttackSpeed = 1024.0;
    private boolean removeEmptyBottles = true;

    /**
     * @param kits kit service
     * @param knockback knockback service
     * @param tags combat tag service
     */
    public CombatService(KitService kits, KnockbackService knockback, CombatTagService tags) {
        this.kits = kits;
        this.knockback = knockback;
        this.tags = tags;
    }

    /**
     * @param section {@code combat} section of config.yml
     */
    public void configure(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        oldCombatAttackSpeed = section.getDouble("old-combat-attack-speed", 1024.0);
        removeEmptyBottles = section.getBoolean("remove-empty-bottles", true);
        tags.configure(section.getInt("combat-tag-seconds", 15), section.getInt("kill-credit-seconds", 12));
    }

    /**
     * Gives the kit (with the player's layout) and applies its combat settings.
     *
     * @param player player
     * @param kit kit
     */
    public void applyKit(Player player, Kit kit) {
        kits.giveKit(player, kit);
        activate(player, kit);
    }

    /**
     * Applies combat settings for a kit without touching the inventory.
     *
     * @param player player
     * @param kit kit
     */
    public void activate(Player player, Kit kit) {
        activeKits.put(player.getUniqueId(), kit);
        setAttackSpeed(player, kit.rules().oldCombat() ? oldCombatAttackSpeed : VANILLA_ATTACK_SPEED);
        player.setMaximumNoDamageTicks(kit.rules().hitDelay());
        player.setNoDamageTicks(0);
    }

    /**
     * Restores vanilla combat settings and forgets the active kit, cooldowns and tags.
     *
     * @param player player
     */
    public void reset(Player player) {
        activeKits.remove(player.getUniqueId());
        knockbackOverrides.remove(player.getUniqueId());
        cooldowns.clear(player.getUniqueId());
        tags.clear(player);
        setAttackSpeed(player, VANILLA_ATTACK_SPEED);
        player.setMaximumNoDamageTicks(20);
        player.resetCooldown();
    }

    private static void setAttackSpeed(Player player, double value) {
        AttributeInstance attribute = player.getAttribute(Attribute.ATTACK_SPEED);
        if (attribute != null) {
            attribute.setBaseValue(value);
        }
    }

    /**
     * @param player player
     * @return active kit or null (lobby, spectating ...)
     */
    public Kit activeKit(Player player) {
        return activeKits.get(player.getUniqueId());
    }

    /**
     * Overrides the knockback profile for a player regardless of kit (used by FFA arenas with their own profile).
     *
     * @param player player
     * @param profile profile id or null to clear
     */
    public void overrideKnockback(Player player, String profile) {
        if (profile == null || profile.isBlank()) {
            knockbackOverrides.remove(player.getUniqueId());
        } else {
            knockbackOverrides.put(player.getUniqueId(), profile);
        }
    }

    /**
     * @param victim victim
     * @return the knockback profile that applies to hits on the victim, or null for vanilla
     */
    public KnockbackProfile knockbackFor(Player victim) {
        if (!knockback.enabled()) {
            return null;
        }
        String override = knockbackOverrides.get(victim.getUniqueId());
        if (override != null && "vanilla".equalsIgnoreCase(override)) {
            return null;
        }
        if (override != null) {
            return knockback.profile(override);
        }
        Kit kit = activeKits.get(victim.getUniqueId());
        if (kit == null || "vanilla".equalsIgnoreCase(kit.knockback())) {
            return null;
        }
        return knockback.profile(kit.knockback());
    }

    /** @return cooldown tracker */
    public Cooldowns cooldowns() {
        return cooldowns;
    }

    /** @return combat tags */
    public CombatTagService tags() {
        return tags;
    }

    /** @return whether empty bottles/bowls are removed automatically */
    public boolean removeEmptyBottles() {
        return removeEmptyBottles;
    }

    /**
     * Forgets a player completely (quit).
     *
     * @param uuid player id
     */
    public void forget(UUID uuid) {
        activeKits.remove(uuid);
        knockbackOverrides.remove(uuid);
        cooldowns.clear(uuid);
    }
}
