package net.pvpserver.duels.match;

import net.pvpserver.core.api.event.PracticeDeathEvent;
import net.pvpserver.core.arena.ArenaInstance;
import net.pvpserver.core.combat.CombatListener;
import net.pvpserver.core.kit.KitRules;
import net.pvpserver.duels.PvPDuels;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * Enforces match rules: freeze during countdown, invulnerability outside the fight, friendly fire, build limits,
 * hit/combo tracking, boxing hits, sumo water, bridge goals, void falls, pearl bounds and forfeits on quit.
 */
public final class MatchListener implements Listener {

    private final PvPDuels plugin;
    private final MatchManager matches;

    /**
     * @param plugin duels plugin
     */
    public MatchListener(PvPDuels plugin) {
        this.plugin = plugin;
        this.matches = plugin.matches();
    }

    private Match matchOf(Player player) {
        return matches.matchOf(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        matches.forfeit(event.getPlayer(), true);
    }

    @EventHandler
    public void onDeath(PracticeDeathEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match != null) {
            matches.handleDeath(match, event.getPlayer(), event.killer(), event.cause());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Match match = matchOf(player);
        if (match == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (match.state() == MatchState.COUNTDOWN) {
            if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
                Location back = from.clone();
                back.setYaw(to.getYaw());
                back.setPitch(to.getPitch());
                event.setTo(back);
            }
            return;
        }
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        ArenaInstance arena = match.arena();
        MatchParticipant participant = match.participant(player.getUniqueId());
        if (arena == null || participant == null) {
            return;
        }
        if (!participant.alive()) {
            // Eliminated players spectating the rest of the round stay inside the arena.
            if (!arena.contains(to)) {
                event.setTo(arena.spectatorSpawn());
            }
            return;
        }
        if (match.state() != MatchState.FIGHTING) {
            return;
        }
        KitRules rules = match.kit().rules();
        boolean fell = to.getY() < arena.voidY();
        boolean water = rules.sumo() && isWater(to.getBlock());
        if (fell || water) {
            matches.handleDeath(match, player, killerOf(player), null);
            return;
        }
        if (rules.bridge()) {
            MatchTeam team = participant.team();
            var enemyGoal = team.index() % 2 == 0 ? arena.arena().goalB() : arena.arena().goalA();
            var ownGoal = team.index() % 2 == 0 ? arena.arena().goalA() : arena.arena().goalB();
            if (arena.inGoal(to, enemyGoal)) {
                matches.score(match, player, true);
            } else if (arena.inGoal(to, ownGoal)) {
                // Jumping into your own goal just sends you back.
                player.teleport(team.index() % 2 == 0 ? arena.spawnA() : arena.spawnB());
            }
        }
    }

    private Player killerOf(Player victim) {
        var id = plugin.api().combat().tags().lastAttacker(victim);
        return id == null ? null : plugin.getServer().getPlayer(id);
    }

    private static boolean isWater(Block block) {
        Material type = block.getType();
        return type == Material.WATER || type == Material.BUBBLE_COLUMN
                || (block.getBlockData() instanceof org.bukkit.block.data.Waterlogged w && w.isWaterlogged());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Match match = matchOf(player);
        if (match == null) {
            return;
        }
        MatchParticipant participant = match.participant(player.getUniqueId());
        if (match.state() != MatchState.FIGHTING || participant == null || !participant.alive()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = CombatListener.resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        Match attackerMatch = matchOf(attacker);
        Match victimMatch = matchOf(victim);
        if (attackerMatch == null && victimMatch == null) {
            return;
        }
        if (attackerMatch != victimMatch) {
            event.setCancelled(true);
            return;
        }
        MatchParticipant attackerP = attackerMatch.participant(attacker.getUniqueId());
        if (attackerP == null || !attackerP.alive() || attackerMatch.state() != MatchState.FIGHTING) {
            event.setCancelled(true);
            return;
        }
        if (attacker != victim && attackerMatch.teammates(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitMonitor(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) {
            return;
        }
        Match match = matchOf(attacker);
        if (match == null || match.state() != MatchState.FIGHTING || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        MatchParticipant attackerP = match.participant(attacker.getUniqueId());
        MatchParticipant victimP = match.participant(victim.getUniqueId());
        if (attackerP == null || victimP == null) {
            return;
        }
        attackerP.hit();
        victimP.breakCombo();
        if (match.kit().rules().boxing()) {
            matches.score(match, attacker, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        if (!(potion.getShooter() instanceof Player thrower)) {
            return;
        }
        Match match = matchOf(thrower);
        if (match == null) {
            return;
        }
        MatchParticipant participant = match.participant(thrower.getUniqueId());
        boolean healing = potion.getEffects().stream().anyMatch(effect -> effect.getType().equals(PotionEffectType.INSTANT_HEALTH));
        if (participant != null && healing) {
            participant.potion(event.getIntensity(thrower) < 0.5);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match == null) {
            return;
        }
        ArenaInstance arena = match.arena();
        Block block = event.getBlockPlaced();
        if (!match.kit().rules().build() || match.state() != MatchState.FIGHTING || arena == null
                || !arena.canBuildAt(block.getX(), block.getY(), block.getZ())) {
            event.setCancelled(true);
            if (arena != null && match.kit().rules().build() && block.getY() > arena.originY() + arena.arena().buildLimit()) {
                plugin.messages().actionBar(event.getPlayer(), "match.build-limit");
            }
            return;
        }
        if (match.kit().rules().bridge() && nearGoal(arena, block.getLocation())) {
            event.setCancelled(true);
        }
    }

    private static boolean nearGoal(ArenaInstance arena, Location location) {
        for (var goal : new net.pvpserver.core.arena.RelativePosition[]{arena.arena().goalA(), arena.arena().goalB()}) {
            if (goal != null) {
                Location centre = arena.at(goal);
                double dx = location.getX() + 0.5 - centre.getX();
                double dz = location.getZ() + 0.5 - centre.getZ();
                if (dx * dx + dz * dz <= (arena.arena().goalRadius() + 1.5) * (arena.arena().goalRadius() + 1.5)
                        && location.getY() >= centre.getY() - 3) {
                    return true;
                }
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match == null) {
            return;
        }
        ArenaInstance arena = match.arena();
        KitRules rules = match.kit().rules();
        Block block = event.getBlock();
        boolean allowed = match.state() == MatchState.FIGHTING && arena != null && arena.contains(block.getLocation())
                && rules.canBreak(block.getType(), arena.isPlaced(block));
        if (!allowed) {
            event.setCancelled(true);
        } else {
            event.setDropItems(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        Match match = matchOf(event.getPlayer());
        if (match == null) {
            return;
        }
        ArenaInstance arena = match.arena();
        Block block = event.getBlock();
        if (!match.kit().rules().build() || match.state() != MatchState.FIGHTING || arena == null
                || !arena.canBuildAt(block.getX(), block.getY(), block.getZ())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPearl(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        Match match = matchOf(event.getPlayer());
        if (match != null && match.arena() != null && !match.arena().contains(event.getTo())) {
            event.setCancelled(true);
        }
    }
}
