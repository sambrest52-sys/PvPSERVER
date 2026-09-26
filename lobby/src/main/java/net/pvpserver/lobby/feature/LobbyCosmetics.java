package net.pvpserver.lobby.feature;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.pvpserver.core.cosmetic.CosmeticType;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.lobby.PvPLobby;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lobby-only cosmetics: particle trails behind walking players and a burst when a player joins. Selected in the
 * cosmetics menu like kill effects; trails only show in the lobby.
 */
public final class LobbyCosmetics {

    private final PvPLobby plugin;
    private final Map<UUID, Location> lastSpot = new HashMap<>();
    private long tick;

    /**
     * @param plugin lobby plugin
     */
    public LobbyCosmetics(PvPLobby plugin) {
        this.plugin = plugin;
    }

    /** Called every tick; trails run every {@code cosmetics.trails.interval-ticks}. */
    public void tick() {
        if (!plugin.settings().cosmetics().trails() || tick++ % plugin.settings().cosmetics().trailIntervalTicks() != 0) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.api().states().is(player, PlayerState.LOBBY, PlayerState.QUEUE) || !player.getWorld().equals(plugin.lobbyWorld().world())) {
                continue;
            }
            String trail = plugin.api().cosmetics().selected(player, CosmeticType.TRAIL);
            if (trail == null || trail.isEmpty() || trail.equals("none")) {
                continue;
            }
            Location at = player.getLocation();
            Location last = lastSpot.put(player.getUniqueId(), at);
            if (last == null || !last.getWorld().equals(at.getWorld()) || last.distanceSquared(at) < 0.04) {
                continue;
            }
            trail(player, trail, at.clone().add(0, 0.15, 0));
        }
    }

    private void trail(Player player, String trail, Location at) {
        switch (trail) {
            case "cloud" -> at.getWorld().spawnParticle(Particle.CLOUD, at, 2, 0.15, 0.02, 0.15, 0.01);
            case "hearts" -> at.getWorld().spawnParticle(Particle.HEART, at.add(0, 0.3, 0), 1, 0.2, 0.1, 0.2, 0);
            case "flames" -> at.getWorld().spawnParticle(Particle.FLAME, at, 3, 0.12, 0.02, 0.12, 0.01);
            case "notes" -> at.getWorld().spawnParticle(Particle.NOTE, at.add(0, 0.4, 0), 1, 0.2, 0.1, 0.2, 1);
            case "magic" -> at.getWorld().spawnParticle(Particle.WITCH, at, 4, 0.15, 0.05, 0.15, 0.02);
            case "emerald" -> at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 3, 0.2, 0.05, 0.2, 0);
            case "snow" -> at.getWorld().spawnParticle(Particle.SNOWFLAKE, at, 3, 0.15, 0.05, 0.15, 0.01);
            case "rainbow" -> {
                float hue = (tick % 60) / 60f;
                java.awt.Color rgb = java.awt.Color.getHSBColor(hue, 0.85f, 1f);
                at.getWorld().spawnParticle(Particle.DUST, at, 3, 0.15, 0.05, 0.15, 0,
                        new Particle.DustOptions(Color.fromRGB(rgb.getRed(), rgb.getGreen(), rgb.getBlue()), 1.1f));
            }
            default -> {
                // unknown id: nothing
            }
        }
    }

    /**
     * Plays the player's join effect where they spawn.
     *
     * @param player player who joined
     */
    public void joinEffect(Player player) {
        if (!plugin.settings().cosmetics().joinEffects()) {
            return;
        }
        String effect = plugin.api().cosmetics().selected(player, CosmeticType.JOIN_EFFECT);
        if (effect == null || effect.isEmpty() || effect.equals("none")) {
            return;
        }
        Location at = player.getLocation().add(0, 1, 0);
        switch (effect) {
            case "firework" -> {
                at.getWorld().spawnParticle(Particle.FIREWORK, at, 60, 0.4, 0.8, 0.4, 0.15);
                at.getWorld().playSound(Sound.sound(Key.key("entity.firework_rocket.twinkle"), Sound.Source.MASTER, 1f, 1f), at.getX(), at.getY(), at.getZ());
            }
            case "lightning" -> {
                at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 80, 0.3, 1.5, 0.3, 0.2);
                at.getWorld().playSound(Sound.sound(Key.key("entity.lightning_bolt.thunder"), Sound.Source.MASTER, 0.4f, 1.6f), at.getX(), at.getY(), at.getZ());
            }
            case "totem" -> {
                at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 70, 0.4, 0.8, 0.4, 0.4);
                at.getWorld().playSound(Sound.sound(Key.key("item.totem.use"), Sound.Source.MASTER, 0.4f, 1.4f), at.getX(), at.getY(), at.getZ());
            }
            case "portal" -> at.getWorld().spawnParticle(Particle.PORTAL, at, 120, 0.5, 1, 0.5, 0.6);
            case "enchant" -> at.getWorld().spawnParticle(Particle.ENCHANT, at.add(0, 1, 0), 120, 0.6, 0.6, 0.6, 1);
            default -> {
                // unknown id: nothing
            }
        }
    }

    /**
     * @param player player leaving
     */
    public void forget(Player player) {
        lastSpot.remove(player.getUniqueId());
    }
}
