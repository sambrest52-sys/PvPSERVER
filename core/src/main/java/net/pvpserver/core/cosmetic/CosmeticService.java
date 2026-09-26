package net.pvpserver.core.cosmetic;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.config.Reloadable;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.profile.CosmeticSelection;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.ProfileService;
import net.pvpserver.core.profile.Setting;
import net.pvpserver.core.util.Tasks;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kill effects, death animations and join messages. Effects are particles/sounds only (never damaging entities)
 * and are shown only to nearby players who enabled cosmetics.
 */
public final class CosmeticService implements Reloadable {

    private final ConfigFile file;
    private final ProfileService profiles;
    private final MessageService messages;
    private final Map<CosmeticType, Map<String, Cosmetic>> cosmetics = new EnumMap<>(CosmeticType.class);

    /**
     * @param plugin core plugin
     * @param profiles profiles
     * @param messages messages
     */
    public CosmeticService(JavaPlugin plugin, ProfileService profiles, MessageService messages) {
        this.file = new ConfigFile(plugin, "cosmetics.yml");
        this.profiles = profiles;
        this.messages = messages;
        reload();
    }

    @Override
    public void reload() {
        file.reload();
        cosmetics.clear();
        for (CosmeticType type : CosmeticType.values()) {
            Map<String, Cosmetic> map = new LinkedHashMap<>();
            ConfigurationSection section = file.get().getConfigurationSection(type.section());
            if (section != null) {
                for (String id : section.getKeys(false)) {
                    ConfigurationSection s = section.getConfigurationSection(id);
                    if (s == null) {
                        continue;
                    }
                    Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
                    String key = id.toLowerCase(Locale.ROOT);
                    map.put(key, new Cosmetic(type, key, s.getString("display", id), icon == null ? Material.PAPER : icon,
                            s.getString("permission"), s.getString("message")));
                }
            }
            cosmetics.put(type, map);
        }
    }

    /**
     * @param type category
     * @return all cosmetics of the category
     */
    public Collection<Cosmetic> all(CosmeticType type) {
        return cosmetics.getOrDefault(type, Map.of()).values();
    }

    /**
     * @param player player
     * @param cosmetic cosmetic
     * @return whether the player may use it
     */
    public boolean unlocked(Player player, Cosmetic cosmetic) {
        return cosmetic.permission() == null || cosmetic.permission().isBlank() || player.hasPermission(cosmetic.permission());
    }

    /**
     * @param player player
     * @param type category
     * @return selected id ("none" when nothing selected)
     */
    public String selected(Player player, CosmeticType type) {
        PlayerProfile profile = profiles.get(player);
        if (profile == null) {
            return "none";
        }
        CosmeticSelection selection = profile.cosmetics();
        return switch (type) {
            case KILL_EFFECT -> selection.killEffect();
            case DEATH_ANIMATION -> selection.deathAnimation();
            case JOIN_MESSAGE -> selection.joinMessage();
            case TRAIL -> selection.trail();
            case JOIN_EFFECT -> selection.joinEffect();
        };
    }

    /**
     * Selects a cosmetic (or clears with "none").
     *
     * @param player player
     * @param type category
     * @param id cosmetic id or "none"
     * @return whether it was selected
     */
    public boolean select(Player player, CosmeticType type, String id) {
        PlayerProfile profile = profiles.get(player);
        if (profile == null) {
            return false;
        }
        if (!id.equals("none")) {
            Cosmetic cosmetic = cosmetics.getOrDefault(type, Map.of()).get(id);
            if (cosmetic == null || !unlocked(player, cosmetic)) {
                return false;
            }
        }
        switch (type) {
            case KILL_EFFECT -> profile.cosmetics().killEffect(id);
            case DEATH_ANIMATION -> profile.cosmetics().deathAnimation(id);
            case JOIN_MESSAGE -> profile.cosmetics().joinMessage(id);
            case TRAIL -> profile.cosmetics().trail(id);
            case JOIN_EFFECT -> profile.cosmetics().joinEffect(id);
        }
        profile.markDirty();
        return true;
    }

    private List<Player> viewers(Location location) {
        List<Player> viewers = new ArrayList<>();
        for (Player player : location.getNearbyPlayers(48)) {
            PlayerProfile profile = profiles.get(player);
            if (profile == null || profile.settings().is(Setting.SHOW_COSMETICS)) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    /**
     * Plays the killer's selected kill effect at the victim location.
     *
     * @param killer killer
     * @param location victim location
     */
    public void playKillEffect(Player killer, Location location) {
        String id = selected(killer, CosmeticType.KILL_EFFECT);
        Cosmetic cosmetic = cosmetics.getOrDefault(CosmeticType.KILL_EFFECT, Map.of()).get(id);
        if (cosmetic == null || !unlocked(killer, cosmetic)) {
            return;
        }
        List<Player> viewers = viewers(location);
        Location at = location.clone().add(0, 1, 0);
        switch (id) {
            case "lightning" -> {
                location.getWorld().strikeLightningEffect(location);
            }
            case "explosion" -> {
                particles(viewers, Particle.EXPLOSION_EMITTER, at, 1, 0);
                sound(viewers, at, "entity.generic.explode");
            }
            case "blood" -> {
                viewers.forEach(v -> v.spawnParticle(Particle.BLOCK, at, 60, 0.3, 0.6, 0.3, 0.1, Material.REDSTONE_BLOCK.createBlockData()));
                sound(viewers, at, "block.stone.break");
            }
            case "flames" -> {
                particles(viewers, Particle.FLAME, at, 60, 0.08);
                sound(viewers, at, "item.firecharge.use");
            }
            case "hearts" -> particles(viewers, Particle.HEART, at, 15, 0.1);
            case "firework" -> {
                particles(viewers, Particle.FIREWORK, at, 80, 0.2);
                sound(viewers, at, "entity.firework_rocket.blast");
            }
            case "souls" -> {
                particles(viewers, Particle.SOUL, at, 40, 0.05);
                sound(viewers, at, "particle.soul_escape");
            }
            case "notes" -> particles(viewers, Particle.NOTE, at.clone().add(0, 1, 0), 20, 1);
            default -> particles(viewers, Particle.CRIT, at, 30, 0.2);
        }
    }

    /**
     * Plays the victim's selected death animation.
     *
     * @param victim victim
     * @param location death location
     */
    public void playDeathAnimation(Player victim, Location location) {
        String id = selected(victim, CosmeticType.DEATH_ANIMATION);
        Cosmetic cosmetic = cosmetics.getOrDefault(CosmeticType.DEATH_ANIMATION, Map.of()).get(id);
        if (cosmetic == null || !unlocked(victim, cosmetic)) {
            return;
        }
        List<Player> viewers = viewers(location);
        Location base = location.clone();
        switch (id) {
            case "smoke" -> particles(viewers, Particle.LARGE_SMOKE, base.clone().add(0, 1, 0), 40, 0.05);
            case "totem" -> {
                particles(viewers, Particle.TOTEM_OF_UNDYING, base.clone().add(0, 1, 0), 80, 0.4);
                sound(viewers, base, "item.totem.use");
            }
            case "spiral" -> animate(20, tick -> {
                double angle = tick * 0.6;
                Location point = base.clone().add(Math.cos(angle) * 0.8, tick * 0.1, Math.sin(angle) * 0.8);
                viewers.forEach(v -> v.spawnParticle(Particle.DUST, point, 3, new Particle.DustOptions(Color.RED, 1.2f)));
            });
            case "ascend" -> animate(20, tick -> {
                Location point = base.clone().add(0, tick * 0.15, 0);
                particles(viewers, Particle.SOUL_FIRE_FLAME, point, 6, 0.01);
            });
            default -> particles(viewers, Particle.POOF, base.clone().add(0, 1, 0), 20, 0.05);
        }
    }

    /**
     * @param player joining player
     * @return formatted join message, or null when none is selected/allowed
     */
    public Component joinMessage(Player player) {
        String id = selected(player, CosmeticType.JOIN_MESSAGE);
        Cosmetic cosmetic = cosmetics.getOrDefault(CosmeticType.JOIN_MESSAGE, Map.of()).get(id);
        if (cosmetic == null || cosmetic.message() == null || !unlocked(player, cosmetic)) {
            return null;
        }
        return messages.parse(cosmetic.message(), MessageService.p("player", player.getName()));
    }

    private static void particles(List<Player> viewers, Particle particle, Location at, int count, double speed) {
        for (Player viewer : viewers) {
            viewer.spawnParticle(particle, at, count, 0.3, 0.5, 0.3, speed);
        }
    }

    private static void sound(List<Player> viewers, Location at, String key) {
        Sound sound = Sound.sound(Key.key(key), Sound.Source.PLAYER, 1f, 1f);
        for (Player viewer : viewers) {
            viewer.playSound(sound, at.getX(), at.getY(), at.getZ());
        }
    }

    private static void animate(int ticks, java.util.function.IntConsumer frame) {
        BukkitTask[] handle = new BukkitTask[1];
        int[] tick = {0};
        handle[0] = Tasks.timer(() -> {
            if (tick[0] >= ticks) {
                handle[0].cancel();
                return;
            }
            frame.accept(tick[0]++);
        }, 0L, 1L);
    }
}
