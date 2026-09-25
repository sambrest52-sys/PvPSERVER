package net.pvpserver.core.combat;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import net.pvpserver.core.api.event.PracticeDeathEvent;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.kit.RegenMode;
import net.pvpserver.core.knockback.KnockbackProfile;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.state.PlayerStateService;
import net.pvpserver.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces kit combat rules for players with an active kit: custom knockback, hit delay, hunger/regen modes,
 * pearl and golden apple cooldowns, golden heads, potion velocity, fake deaths and ghost block resync.
 */
public final class CombatListener implements Listener {

    private final CombatService combat;
    private final PlayerStateService states;
    private final MessageService messages;
    private final NamespacedKey specialKey;
    private final Map<UUID, Long> baseKnockbackTick = new HashMap<>();
    private final Map<UUID, Integer> legacyRegenCounters = new HashMap<>();
    private final Map<UUID, Integer> deathTick = new HashMap<>();
    private List<PotionEffect> goldenHeadEffects = List.of();

    /**
     * @param combat combat service
     * @param states state machine
     * @param messages messages
     * @param specialKey PDC key for special items
     */
    public CombatListener(CombatService combat, PlayerStateService states, MessageService messages, NamespacedKey specialKey) {
        this.combat = combat;
        this.states = states;
        this.messages = messages;
        this.specialKey = specialKey;
        Tasks.timer(this::legacyRegenTick, 20L, 20L);
    }

    /**
     * @param section {@code combat} section of config.yml
     */
    public void configure(ConfigurationSection section) {
        List<PotionEffect> effects = new ArrayList<>();
        if (section != null) {
            for (String raw : section.getStringList("golden-head-effects")) {
                String[] parts = raw.split(":");
                PotionEffectType type = org.bukkit.Registry.EFFECT.get(NamespacedKey.minecraft(parts[0].toLowerCase(java.util.Locale.ROOT)));
                if (type != null) {
                    int amplifier = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                    int seconds = parts.length > 2 ? Integer.parseInt(parts[2]) : 5;
                    effects.add(new PotionEffect(type, seconds * 20, amplifier));
                }
            }
        }
        goldenHeadEffects = effects;
    }

    // ------------------------------------------------------------------ knockback

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getHitBy() instanceof Player attacker)) {
            return;
        }
        if (event.getCause() != EntityKnockbackEvent.Cause.ENTITY_ATTACK) {
            return;
        }
        KnockbackProfile profile = combat.knockbackFor(victim);
        if (profile == null) {
            return;
        }
        int tick = Bukkit.getCurrentTick();
        Long baseTick = baseKnockbackTick.get(victim.getUniqueId());
        Vector current = victim.getVelocity();
        if (baseTick == null || baseTick != tick) {
            baseKnockbackTick.put(victim.getUniqueId(), (long) tick);
            double dx = victim.getX() - attacker.getX();
            double dz = victim.getZ() - attacker.getZ();
            @SuppressWarnings("deprecation")
            boolean onGround = victim.isOnGround();
            Vector desired = profile.base(current, dx, dz, onGround);
            event.setKnockback(desired.subtract(current));
        } else {
            double level = Math.max(0.0, event.getKnockbackStrength() / 0.5);
            event.setKnockback(profile.extra(attacker.getLocation().getYaw(), level));
        }
    }

    // ------------------------------------------------------------------ damage rules + fake deaths

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageRules(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Kit kit = combat.activeKit(victim);
        if (kit == null) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK && kit.rules().oldCombat()) {
            event.setCancelled(true);
            return;
        }
        if (cause == EntityDamageEvent.DamageCause.FALL && !kit.rules().fallDamage()) {
            event.setCancelled(true);
            return;
        }
        if (kit.rules().noDamage() && cause != EntityDamageEvent.DamageCause.VOID) {
            // Keep the hit (knockback, hurt animation, hit counting) but remove the damage.
            if (event instanceof EntityDamageByEntityEvent) {
                event.setDamage(0);
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !states.get(victim).combat()) {
            return;
        }
        Player attacker = event instanceof EntityDamageByEntityEvent byEntity ? resolveAttacker(byEntity.getDamager()) : null;
        if (attacker != null && attacker != victim) {
            combat.tags().tag(attacker, victim);
        }
        boolean lethal = event.getCause() == EntityDamageEvent.DamageCause.VOID
                || victim.getHealth() - event.getFinalDamage() <= 0.0;
        if (!lethal) {
            return;
        }
        event.setCancelled(true);
        int tick = Bukkit.getCurrentTick();
        Integer last = deathTick.put(victim.getUniqueId(), tick);
        if (last != null && last == tick) {
            return;
        }
        Player killer = attacker;
        if (killer == null || killer == victim) {
            UUID last2 = combat.tags().lastAttacker(victim);
            killer = last2 == null ? null : Bukkit.getPlayer(last2);
        }
        victim.setFireTicks(0);
        victim.setFallDistance(0);
        AttributeInstance max = victim.getAttribute(Attribute.MAX_HEALTH);
        victim.setHealth(max == null ? 20 : max.getValue());
        Bukkit.getPluginManager().callEvent(new PracticeDeathEvent(victim, killer, event.getCause()));
    }

    /**
     * @param damager direct damager
     * @return responsible player (projectile shooter) or null
     */
    public static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ hunger & regen

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Kit kit = combat.activeKit(player);
        if (kit != null && !kit.rules().hunger() && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Kit kit = combat.activeKit(player);
        if (kit == null || kit.rules().regen() == RegenMode.VANILLA) {
            return;
        }
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
            event.setCancelled(true);
        }
    }

    private void legacyRegenTick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Kit kit = combat.activeKit(player);
            if (kit == null || kit.rules().regen() != RegenMode.LEGACY) {
                legacyRegenCounters.remove(player.getUniqueId());
                continue;
            }
            int counter = legacyRegenCounters.merge(player.getUniqueId(), 1, Integer::sum);
            if (counter < 4) {
                continue;
            }
            legacyRegenCounters.put(player.getUniqueId(), 0);
            AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
            double maxHealth = max == null ? 20 : max.getValue();
            if (player.getFoodLevel() >= 18 && player.getHealth() < maxHealth && !player.isDead()) {
                player.setHealth(Math.min(maxHealth, player.getHealth() + 1.0));
                player.setExhaustion(player.getExhaustion() + 3.0f);
            }
        }
    }

    // ------------------------------------------------------------------ items

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(PlayerLaunchProjectileEvent event) {
        Player player = event.getPlayer();
        Kit kit = combat.activeKit(player);
        if (kit == null) {
            return;
        }
        if (event.getProjectile() instanceof EnderPearl && kit.rules().pearlCooldown() > 0) {
            long remaining = combat.cooldowns().remaining(player.getUniqueId(), "pearl");
            if (remaining > 0) {
                event.setCancelled(true);
                event.setShouldConsume(false);
                messages.send(player, "combat.pearl-cooldown", MessageService.p("seconds", String.format(java.util.Locale.ROOT, "%.1f", remaining / 1000.0)));
                Tasks.later(player::updateInventory, 1L);
                return;
            }
            combat.cooldowns().set(player.getUniqueId(), "pearl", kit.rules().pearlCooldown() * 1000L);
            Tasks.later(() -> player.setCooldown(Material.ENDER_PEARL, kit.rules().pearlCooldown() * 20), 1L);
        }
        if (event.getProjectile() instanceof ThrownPotion potion && kit.rules().potionVelocity() != 1.0) {
            potion.setVelocity(potion.getVelocity().multiply(kit.rules().potionVelocity()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Kit kit = combat.activeKit(player);
        ItemStack item = event.getItem();
        Material type = item.getType();
        if (kit != null && (type == Material.GOLDEN_APPLE || type == Material.ENCHANTED_GOLDEN_APPLE) && kit.rules().gappleCooldown() > 0) {
            long remaining = combat.cooldowns().remaining(player.getUniqueId(), "gapple");
            if (remaining > 0) {
                event.setCancelled(true);
                messages.send(player, "combat.gapple-cooldown", MessageService.p("seconds", String.format(java.util.Locale.ROOT, "%.1f", remaining / 1000.0)));
                return;
            }
            combat.cooldowns().set(player.getUniqueId(), "gapple", kit.rules().gappleCooldown() * 1000L);
        }
        if (item.hasItemMeta() && "golden_head".equals(item.getItemMeta().getPersistentDataContainer().get(specialKey, PersistentDataType.STRING))) {
            Tasks.later(() -> goldenHeadEffects.forEach(player::addPotionEffect), 1L);
        }
        if (combat.removeEmptyBottles() && (type == Material.POTION || type == Material.MUSHROOM_STEW)) {
            EquipmentSlot hand = event.getHand();
            Tasks.later(() -> {
                ItemStack held = player.getInventory().getItem(hand);
                if (held.getType() == Material.GLASS_BOTTLE || held.getType() == Material.BOWL) {
                    player.getInventory().setItem(hand, null);
                }
            }, 1L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Kit kit = combat.activeKit(player);
        if (kit == null) {
            return;
        }
        Material type = event.getItemDrop().getItemStack().getType();
        if (type == Material.GLASS_BOTTLE || type == Material.BOWL) {
            event.getItemDrop().remove();
            return;
        }
        if (!kit.rules().dropItems()) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ ghost blocks

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlaceMonitor(BlockPlaceEvent event) {
        if (!event.isCancelled() && event.canBuild()) {
            return;
        }
        Player player = event.getPlayer();
        Block placed = event.getBlockPlaced();
        Block against = event.getBlockAgainst();
        Tasks.later(() -> {
            if (!player.isOnline()) {
                return;
            }
            resend(player, placed);
            resend(player, against);
            player.updateInventory();
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreakMonitor(BlockBreakEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Tasks.later(() -> {
            if (!player.isOnline()) {
                return;
            }
            resend(player, block);
            for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                resend(player, block.getRelative(face));
            }
        }, 1L);
    }

    private static void resend(Player player, Block block) {
        // Plugins that fire synthetic place events (build-permission checks) may leave the clicked block null.
        if (block != null && block.getWorld().equals(player.getWorld())) {
            player.sendBlockChange(block.getLocation(), block.getBlockData());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        combat.forget(uuid);
        baseKnockbackTick.remove(uuid);
        legacyRegenCounters.remove(uuid);
        deathTick.remove(uuid);
    }

    /**
     * @param item item
     * @return knockback enchantment level of the item
     */
    static int knockbackLevel(ItemStack item) {
        return item == null ? 0 : item.getEnchantmentLevel(Enchantment.KNOCKBACK);
    }
}
