package net.pvpserver.ffa;

import net.pvpserver.core.api.event.PracticeDeathEvent;
import net.pvpserver.core.combat.CombatListener;
import net.pvpserver.core.message.MessageService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * FFA rules: deaths and void, safe zone, spawn protection, building with decay, combat-tag command blocking and
 * combat logging.
 */
public final class FfaListener implements Listener {

    private final PvPFFA plugin;
    private final FfaManager ffa;
    private final BlockDecay decay;

    /**
     * @param plugin FFA plugin
     * @param decay block decay
     */
    public FfaListener(PvPFFA plugin, BlockDecay decay) {
        this.plugin = plugin;
        this.ffa = plugin.ffa();
        this.decay = decay;
    }

    @EventHandler
    public void onDeath(PracticeDeathEvent event) {
        if (ffa.arenaOf(event.getPlayer()) != null) {
            ffa.death(event.getPlayer(), event.killer(), false);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        ffa.quit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        FfaArena arena = ffa.arenaOf(player);
        if (arena == null) {
            return;
        }
        Location to = event.getTo();
        if (to.getY() < arena.minY()) {
            UUID attacker = plugin.api().combat().tags().lastAttacker(player);
            ffa.death(player, attacker == null ? null : plugin.getServer().getPlayer(attacker), false);
            return;
        }
        if (plugin.api().combat().tags().isTagged(player) && arena.inSafeZone(to) && !arena.inSafeZone(event.getFrom())) {
            Location back = event.getFrom().clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            event.setTo(back);
            plugin.messages().actionBar(player, "ffa.safe-zone-tagged");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        FfaArena arena = ffa.arenaOf(victim);
        if (arena == null) {
            return;
        }
        if (ffa.stats(victim).isProtected() || arena.inSafeZone(victim.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = CombatListener.resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        FfaArena attackerArena = ffa.arenaOf(attacker);
        FfaArena victimArena = ffa.arenaOf(victim);
        if (attackerArena == null && victimArena == null) {
            return;
        }
        if (attackerArena != victimArena) {
            event.setCancelled(true);
            return;
        }
        if (attackerArena.inSafeZone(attacker.getLocation())) {
            event.setCancelled(true);
            plugin.messages().actionBar(attacker, "ffa.safe-zone-attack");
            return;
        }
        // Attacking ends your own spawn protection.
        ffa.stats(attacker).protectedUntil(0);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        FfaArena arena = ffa.arenaOf(event.getPlayer());
        if (arena == null) {
            return;
        }
        if (!arena.kit().rules().build() || arena.inSafeZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        decay.track(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        FfaArena arena = ffa.arenaOf(event.getPlayer());
        if (arena == null) {
            return;
        }
        if (!arena.kit().rules().build() || arena.inSafeZone(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        decay.track(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        FfaArena arena = ffa.arenaOf(event.getPlayer());
        if (arena == null) {
            return;
        }
        if (!decay.isPlaced(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        decay.untrack(event.getBlock());
        event.setDropItems(false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (event.getBlock().getWorld() == ffa.world()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (ffa.arenaOf(player) == null || !plugin.api().combat().tags().isTagged(player) || player.hasPermission("pvp.staff")) {
            return;
        }
        String label = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        List<String> blocked = plugin.config().get().getStringList("combat-log.blocked-commands");
        if (blocked.contains(label) || blocked.contains("*")) {
            event.setCancelled(true);
            plugin.messages().send(player, "ffa.command-blocked", MessageService.p("seconds",
                    plugin.api().combat().tags().remaining(player) / 1000 + 1));
        }
    }
}
