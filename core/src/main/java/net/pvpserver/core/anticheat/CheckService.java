package net.pvpserver.core.anticheat;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.pvpserver.core.api.event.CheckAlertEvent;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.moderation.StaffService;
import net.pvpserver.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight, alert-only checks (CPS and reach) plus the shared alert pipeline. This is intentionally not an
 * anticheat: it never punishes or cancels, and external anticheats can hook in through {@link CheckAlertEvent}
 * or {@link #flag(Player, String, String)}.
 */
public final class CheckService implements Listener {

    private final MessageService messages;
    private final StaffService staff;
    private final Map<UUID, int[]> clicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastCps = new ConcurrentHashMap<>();
    private final Map<UUID, Double> reachBuffer = new ConcurrentHashMap<>();
    private final Map<String, Integer> violations = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAlert = new ConcurrentHashMap<>();
    private final Set<UUID> muted = ConcurrentHashMap.newKeySet();
    private boolean enabled = true;
    private boolean cpsEnabled = true;
    private int maxCps = 20;
    private boolean reachEnabled = true;
    private double maxReach = 3.1;
    private double pingCompensation = 0.003;
    private long alertCooldownMillis = 5000;

    /**
     * @param messages messages
     * @param staff staff alerts
     */
    public CheckService(MessageService messages, StaffService staff) {
        this.messages = messages;
        this.staff = staff;
        Tasks.timer(this::evaluateCps, 20L, 20L);
    }

    /**
     * @param section {@code anticheat} section of config.yml
     */
    public void configure(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        enabled = section.getBoolean("enabled", true);
        cpsEnabled = section.getBoolean("cps.enabled", true);
        maxCps = section.getInt("cps.max", 20);
        reachEnabled = section.getBoolean("reach.enabled", true);
        maxReach = section.getDouble("reach.max", 3.1);
        pingCompensation = section.getDouble("reach.ping-compensation-per-ms", 0.003);
        alertCooldownMillis = section.getLong("alert-cooldown-ms", 5000);
    }

    /**
     * @param player player
     * @return clicks in the last full second
     */
    public int cps(Player player) {
        return lastCps.getOrDefault(player.getUniqueId(), 0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerArmSwingEvent event) {
        clicks.computeIfAbsent(event.getPlayer().getUniqueId(), k -> new int[1])[0]++;
    }

    private void evaluateCps() {
        for (Map.Entry<UUID, int[]> entry : clicks.entrySet()) {
            int cps = entry.getValue()[0];
            entry.getValue()[0] = 0;
            lastCps.put(entry.getKey(), cps);
            if (enabled && cpsEnabled && cps > maxCps) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null) {
                    flag(player, "CPS", cps + " clicks/s (max " + maxCps + ")");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!enabled || !reachEnabled || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        double distance = distanceToBox(attacker.getEyeLocation(), victim.getBoundingBox());
        double allowed = maxReach + Math.min(1.0, attacker.getPing() * pingCompensation);
        UUID id = attacker.getUniqueId();
        if (distance > allowed) {
            double buffer = reachBuffer.merge(id, 1.0, Double::sum);
            if (buffer >= 3) {
                reachBuffer.put(id, 1.5);
                flag(attacker, "Reach", String.format(Locale.ROOT, "%.2f blocks (allowed %.2f, ping %dms)", distance, allowed, attacker.getPing()));
            }
        } else {
            reachBuffer.computeIfPresent(id, (k, v) -> Math.max(0, v - 0.25));
        }
    }

    private static double distanceToBox(Location eye, BoundingBox box) {
        Vector point = eye.toVector();
        double x = Math.max(box.getMinX(), Math.min(point.getX(), box.getMaxX()));
        double y = Math.max(box.getMinY(), Math.min(point.getY(), box.getMaxY()));
        double z = Math.max(box.getMinZ(), Math.min(point.getZ(), box.getMaxZ()));
        return point.distance(new Vector(x, y, z));
    }

    /**
     * Raises a violation and (rate limited) alerts staff. Safe to call from other plugins on the main thread.
     *
     * @param player flagged player
     * @param check check name
     * @param details details
     */
    public void flag(Player player, String check, String details) {
        String key = player.getUniqueId() + ":" + check;
        int vl = violations.merge(key, 1, Integer::sum);
        long now = System.currentTimeMillis();
        Long last = lastAlert.get(key);
        if (last != null && now - last < alertCooldownMillis) {
            return;
        }
        lastAlert.put(key, now);
        CheckAlertEvent event = new CheckAlertEvent(player, check, details, vl);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        Component alert = messages.get("anticheat.alert", MessageService.p("player", player.getName()), MessageService.p("check", check),
                MessageService.p("details", details), MessageService.p("vl", vl), MessageService.p("ping", player.getPing()))
                .clickEvent(ClickEvent.runCommand("/spectate " + player.getName()))
                .hoverEvent(messages.get("anticheat.alert-hover", MessageService.p("player", player.getName())));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(StaffService.ALERTS) && !muted.contains(online.getUniqueId())) {
                online.sendMessage(alert);
            }
        }
        Bukkit.getConsoleSender().sendMessage(alert);
    }

    /**
     * @param player staff member
     * @return whether alerts are now shown
     */
    public boolean toggleAlerts(Player player) {
        if (muted.remove(player.getUniqueId())) {
            return true;
        }
        muted.add(player.getUniqueId());
        return false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        clicks.remove(id);
        lastCps.remove(id);
        reachBuffer.remove(id);
        String prefix = id + ":";
        violations.keySet().removeIf(k -> k.startsWith(prefix));
        lastAlert.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** @return staff service (for integrations) */
    public StaffService staff() {
        return staff;
    }
}
