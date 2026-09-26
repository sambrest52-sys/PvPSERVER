package net.pvpserver.lobby.config;

import net.kyori.adventure.bossbar.BossBar;
import net.pvpserver.core.stats.StatField;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Typed, validated view of config.yml. Invalid values fall back to their default and are reported as warnings, so a
 * typo never disables the lobby.
 *
 * @param world lobby world
 * @param legacySpawn 1.1 style spawn for custom worlds
 * @param voidY fallback void height
 * @param voidSound rescue sound
 * @param doubleJump double jump
 * @param pads launch pads
 * @param portals portals
 * @param npcs NPC behaviour
 * @param parkour parkour
 * @param eggs easter eggs
 * @param wall leaderboard wall
 * @param ambient ambient particles
 * @param zones zone announcements
 * @param bossbar tips boss bar
 * @param announcements chat announcements
 * @param welcome join title and sound
 * @param cosmetics lobby cosmetics
 * @param performance limits
 */
public record LobbySettings(WorldSettings world, String legacySpawn, int voidY, String voidSound, DoubleJump doubleJump, Pads pads,
                            Portals portals, Npcs npcs, ParkourSettings parkour, Eggs eggs, WallSettings wall, Ambient ambient,
                            Zones zones, Bossbar bossbar, Announcements announcements, Welcome welcome, Cosmetics cosmetics,
                            Performance performance) {

    private static final Pattern SOUND = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final Pattern WORLD = Pattern.compile("[A-Za-z0-9_./-]+");

    /** How the lobby world is filled. */
    public enum WorldMode { GENERATED, CUSTOM }

    /**
     * @param name world name
     * @param mode mode
     * @param seed generator seed
     * @param floorY floor height
     * @param time fixed time
     * @param border world border on
     * @param tune apply practice game rules
     */
    public record WorldSettings(String name, WorldMode mode, long seed, int floorY, long time, boolean border, boolean tune) {
    }

    /**
     * @param enabled enabled
     * @param permission required permission ("" = none)
     * @param forward forward boost
     * @param up upward boost
     * @param cooldownTicks cooldown
     */
    public record DoubleJump(boolean enabled, String permission, double forward, double up, long cooldownTicks) {
    }

    /**
     * @param enabled enabled
     * @param sound sound
     * @param particle particle
     * @param cooldownTicks per player cooldown
     */
    public record Pads(boolean enabled, String sound, Particle particle, long cooldownTicks) {
    }

    /**
     * @param enabled enabled
     * @param sound sound
     * @param cooldownTicks per player cooldown
     * @param pushBack push out strength
     * @param particles particle curtain
     */
    public record Portals(boolean enabled, String sound, long cooldownTicks, double pushBack, boolean particles) {
    }

    /**
     * @param enabled enabled
     * @param lookAtPlayers heads follow players
     * @param lookRange look range
     * @param updateIntervalTicks hologram refresh
     * @param clickCooldownMillis click cooldown
     */
    public record Npcs(boolean enabled, boolean lookAtPlayers, double lookRange, long updateIntervalTicks, long clickCooldownMillis) {
    }

    /**
     * @param enabled enabled
     * @param leaderboardSize best time entries
     * @param maxMinutes run time limit
     * @param startSound start sound
     * @param checkpointSound checkpoint sound
     * @param finishSound finish sound
     * @param failSound fail sound
     * @param broadcastRecords announce server records
     * @param firstFinishCommands console commands on the first finish
     * @param personalBestCommands console commands on a personal best
     */
    public record ParkourSettings(boolean enabled, int leaderboardSize, int maxMinutes, String startSound, String checkpointSound,
                                  String finishSound, String failSound, boolean broadcastRecords, List<String> firstFinishCommands,
                                  List<String> personalBestCommands) {
    }

    /**
     * @param enabled enabled
     * @param sound sound
     * @param rewardCommands console commands per egg
     * @param completeCommands console commands when all are found
     * @param completePermissions permissions granted to players who found all eggs
     */
    public record Eggs(boolean enabled, String sound, List<String> rewardCommands, List<String> completeCommands,
                       List<String> completePermissions) {
    }

    /**
     * @param enabled enabled
     * @param kits kits in order (empty = global + ranked kits)
     * @param stats statistics to cycle through
     * @param rotateSeconds seconds per statistic
     * @param entries entries per panel
     * @param background panel background ARGB
     * @param icons floating kit icons
     */
    public record WallSettings(boolean enabled, List<String> kits, List<StatField> stats, int rotateSeconds, int entries, int background,
                               boolean icons) {
    }

    /**
     * @param particle particle
     * @param count count
     * @param offsetX spread x
     * @param offsetY spread y
     * @param offsetZ spread z
     * @param speed speed
     * @param color colour for particles that take one (DUST, ENTITY_EFFECT...), may be null
     */
    public record EmitterType(Particle particle, int count, double offsetX, double offsetY, double offsetZ, double speed, Color color) {
    }

    /**
     * @param enabled enabled
     * @param intervalTicks spread emitters over this many ticks
     * @param viewDistance receiver distance
     * @param emitters emitter types by id
     */
    public record Ambient(boolean enabled, int intervalTicks, double viewDistance, Map<String, EmitterType> emitters) {
    }

    /**
     * @param enabled enabled
     * @param sound entry sound
     * @param names zone id to display name
     */
    public record Zones(boolean enabled, String sound, Map<String, String> names) {
    }

    /**
     * @param enabled enabled
     * @param color bar colour
     * @param overlay bar overlay
     * @param secondsPerTip seconds per tip
     * @param tips tips
     */
    public record Bossbar(boolean enabled, BossBar.Color color, BossBar.Overlay overlay, int secondsPerTip, List<String> tips) {
    }

    /**
     * @param enabled enabled
     * @param intervalSeconds interval
     * @param lobbyOnly only lobby players receive them
     * @param messages messages
     */
    public record Announcements(boolean enabled, int intervalSeconds, boolean lobbyOnly, List<String> messages) {
    }

    /**
     * @param titleEnabled show the title
     * @param title title
     * @param subtitle subtitle
     * @param fadeIn fade in ticks
     * @param stay stay ticks
     * @param fadeOut fade out ticks
     * @param sound sound ("" = none)
     * @param volume volume
     * @param pitch pitch
     */
    public record Welcome(boolean titleEnabled, String title, String subtitle, int fadeIn, int stay, int fadeOut, String sound,
                          float volume, float pitch) {
    }

    /**
     * @param trails trails on
     * @param trailIntervalTicks trail period
     * @param joinEffects join effects on
     */
    public record Cosmetics(boolean trails, int trailIntervalTicks, boolean joinEffects) {
    }

    /**
     * @param maxEntities entity cap
     * @param hologramUpdatesPerTick hologram text updates per tick
     */
    public record Performance(int maxEntities, int hologramUpdatesPerTick) {
    }

    /**
     * @param root config.yml root
     * @param warnings receives a line per invalid value
     * @return settings
     */
    public static LobbySettings read(ConfigurationSection root, List<String> warnings) {
        Reader r = new Reader(root, warnings);
        String worldName = r.string("world.name", "pvp_lobby");
        if (!WORLD.matcher(worldName).matches()) {
            warnings.add("world.name: \"" + worldName + "\" is not a valid world name, using pvp_lobby");
            worldName = "pvp_lobby";
        }
        WorldMode mode = r.enumValue("world.mode", WorldMode.class, WorldMode.GENERATED);
        WorldSettings world = new WorldSettings(worldName, mode, root.getLong("world.seed", 1337),
                r.intRange("world.floor-y", 64, -32, 280), r.longRange("world.time", 6000, 0, 24000),
                root.getBoolean("world.border", true), root.getBoolean("world.tune-world", root.getBoolean("tune-world", true)));

        DoubleJump doubleJump = new DoubleJump(root.getBoolean("double-jump.enabled", true), r.string("double-jump.permission", ""),
                r.doubleRange("double-jump.forward", 1.2, 0, 5), r.doubleRange("double-jump.up", 0.9, 0, 5),
                r.longRange("double-jump.cooldown-ticks", 20, 0, 1200));
        Pads pads = new Pads(root.getBoolean("launch-pads.enabled", true), r.sound("launch-pads.sound", "entity.firework_rocket.launch"),
                r.particle("launch-pads.particle", Particle.CLOUD), r.longRange("launch-pads.cooldown-ticks", 15, 0, 1200));
        Portals portals = new Portals(root.getBoolean("portals.enabled", true), r.sound("portals.sound", "block.portal.trigger"),
                r.longRange("portals.cooldown-ticks", 40, 0, 1200), r.doubleRange("portals.push-back", 0.7, 0, 3),
                root.getBoolean("portals.particles", true));
        Npcs npcs = new Npcs(root.getBoolean("npcs.enabled", true), root.getBoolean("npcs.look-at-players", true),
                r.doubleRange("npcs.look-range", 10, 1, 32), r.longRange("npcs.update-interval-ticks", 40, 5, 1200),
                r.longRange("npcs.click-cooldown-ms", 400, 0, 10000));
        ParkourSettings parkour = new ParkourSettings(root.getBoolean("parkour.enabled", true),
                r.intRange("parkour.leaderboard-size", 10, 1, 25), r.intRange("parkour.max-minutes", 30, 1, 240),
                r.sound("parkour.start-sound", "block.note_block.pling"), r.sound("parkour.checkpoint-sound", "entity.experience_orb.pickup"),
                r.sound("parkour.finish-sound", "ui.toast.challenge_complete"), r.sound("parkour.fail-sound", "entity.villager.no"),
                root.getBoolean("parkour.broadcast-records", true), root.getStringList("parkour.rewards.first-finish"),
                root.getStringList("parkour.rewards.personal-best"));
        Eggs eggs = new Eggs(root.getBoolean("eggs.enabled", true), r.sound("eggs.sound", "entity.player.levelup"),
                root.getStringList("eggs.reward-commands"), root.getStringList("eggs.complete.commands"),
                root.getStringList("eggs.complete.permissions"));

        List<StatField> stats = new ArrayList<>();
        for (String name : root.getStringList("leaderboard-wall.stats")) {
            StatField field = StatField.parse(name);
            if (field == null) {
                warnings.add("leaderboard-wall.stats: unknown statistic " + name);
            } else {
                stats.add(field);
            }
        }
        if (stats.isEmpty()) {
            stats.add(StatField.ELO);
        }
        WallSettings wall = new WallSettings(root.getBoolean("leaderboard-wall.enabled", true),
                root.getStringList("leaderboard-wall.kits").stream().map(k -> k.toLowerCase(Locale.ROOT)).toList(), List.copyOf(stats),
                r.intRange("leaderboard-wall.rotate-seconds", 10, 2, 600), r.intRange("leaderboard-wall.entries", 10, 1, 15),
                r.argb("leaderboard-wall.background", 0xB0141420), root.getBoolean("leaderboard-wall.icons", true));

        Map<String, EmitterType> emitters = new LinkedHashMap<>();
        ConfigurationSection emitterSection = root.getConfigurationSection("ambient.emitters");
        if (emitterSection != null) {
            for (String id : emitterSection.getKeys(false)) {
                EmitterType type = r.emitter("ambient.emitters." + id);
                if (type != null) {
                    emitters.put(id.toLowerCase(Locale.ROOT), type);
                }
            }
        }
        Ambient ambient = new Ambient(root.getBoolean("ambient.enabled", true), r.intRange("ambient.interval-ticks", 6, 1, 100),
                r.doubleRange("ambient.view-distance", 32, 4, 128), emitters);

        Map<String, String> zoneNames = new LinkedHashMap<>();
        ConfigurationSection names = root.getConfigurationSection("zones.names");
        if (names != null) {
            for (String id : names.getKeys(false)) {
                zoneNames.put(id.toLowerCase(Locale.ROOT), names.getString(id, id));
            }
        }
        Zones zones = new Zones(root.getBoolean("zones.enabled", true), r.sound("zones.sound", "block.note_block.hat"), zoneNames);

        Bossbar bossbar = new Bossbar(root.getBoolean("bossbar.enabled", true), r.enumValue("bossbar.color", BossBar.Color.class, BossBar.Color.YELLOW),
                r.enumValue("bossbar.overlay", BossBar.Overlay.class, BossBar.Overlay.PROGRESS), r.intRange("bossbar.seconds-per-tip", 12, 2, 600),
                root.getStringList("bossbar.tips"));
        Announcements announcements = new Announcements(root.getBoolean("announcements.enabled", true),
                r.intRange("announcements.interval-seconds", 300, 10, 86400), root.getBoolean("announcements.lobby-only", false),
                root.getStringList("announcements.messages"));
        Welcome welcome = new Welcome(root.getBoolean("welcome.title.enabled", true), r.string("welcome.title.title", ""),
                r.string("welcome.title.subtitle", ""), r.intRange("welcome.title.fade-in", 10, 0, 200), r.intRange("welcome.title.stay", 50, 0, 600),
                r.intRange("welcome.title.fade-out", 15, 0, 200), r.optionalSound("welcome.sound", "entity.player.levelup"),
                (float) r.doubleRange("welcome.sound-volume", 0.6, 0, 10), (float) r.doubleRange("welcome.sound-pitch", 1.4, 0.5, 2));
        Cosmetics cosmetics = new Cosmetics(root.getBoolean("cosmetics.trails.enabled", true), r.intRange("cosmetics.trails.interval-ticks", 3, 1, 40),
                root.getBoolean("cosmetics.join-effects.enabled", true));
        Performance performance = new Performance(r.intRange("performance.max-entities", 120, 0, 1000),
                r.intRange("performance.hologram-updates-per-tick", 8, 1, 200));
        return new LobbySettings(world, r.string("spawn", ""), root.getInt("void-y", 0), r.optionalSound("void.sound", "entity.enderman.teleport"),
                doubleJump, pads, portals, npcs, parkour, eggs, wall, ambient, zones, bossbar, announcements, welcome, cosmetics, performance);
    }

    /** Reads values with range checks, recording warnings. */
    private record Reader(ConfigurationSection root, List<String> warnings) {

        String string(String path, String fallback) {
            String value = root.getString(path);
            return value == null ? fallback : value;
        }

        int intRange(String path, int fallback, int min, int max) {
            if (!root.contains(path)) {
                return fallback;
            }
            if (!root.isInt(path)) {
                warnings.add(path + ": expected a whole number, using " + fallback);
                return fallback;
            }
            int value = root.getInt(path);
            if (value < min || value > max) {
                warnings.add(path + ": " + value + " is outside " + min + ".." + max + ", using " + fallback);
                return fallback;
            }
            return value;
        }

        long longRange(String path, long fallback, long min, long max) {
            if (!root.contains(path)) {
                return fallback;
            }
            if (!root.isInt(path) && !root.isLong(path)) {
                warnings.add(path + ": expected a whole number, using " + fallback);
                return fallback;
            }
            long value = root.getLong(path);
            if (value < min || value > max) {
                warnings.add(path + ": " + value + " is outside " + min + ".." + max + ", using " + fallback);
                return fallback;
            }
            return value;
        }

        double doubleRange(String path, double fallback, double min, double max) {
            if (!root.contains(path)) {
                return fallback;
            }
            if (!root.isDouble(path) && !root.isInt(path)) {
                warnings.add(path + ": expected a number, using " + fallback);
                return fallback;
            }
            double value = root.getDouble(path);
            if (value < min || value > max) {
                warnings.add(path + ": " + value + " is outside " + min + ".." + max + ", using " + fallback);
                return fallback;
            }
            return value;
        }

        <E extends Enum<E>> E enumValue(String path, Class<E> type, E fallback) {
            String value = root.getString(path);
            if (value == null) {
                return fallback;
            }
            try {
                return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException e) {
                warnings.add(path + ": unknown value " + value + ", using " + fallback.name().toLowerCase(Locale.ROOT));
                return fallback;
            }
        }

        String sound(String path, String fallback) {
            String value = root.getString(path, fallback).trim().toLowerCase(Locale.ROOT);
            if (!SOUND.matcher(value).matches()) {
                warnings.add(path + ": \"" + value + "\" is not a sound id, using " + fallback);
                return fallback;
            }
            return value;
        }

        String optionalSound(String path, String fallback) {
            String value = root.getString(path, fallback);
            return value.isBlank() ? "" : sound(path, fallback);
        }

        Particle particle(String path, Particle fallback) {
            String value = root.getString(path);
            if (value == null) {
                return fallback;
            }
            try {
                return Particle.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warnings.add(path + ": unknown particle " + value + ", using " + fallback.name());
                return fallback;
            }
        }

        int argb(String path, int fallback) {
            String value = root.getString(path);
            if (value == null) {
                return fallback;
            }
            String hex = value.trim().replace("#", "");
            try {
                if (hex.length() == 6) {
                    return 0xFF000000 | Integer.parseUnsignedInt(hex, 16);
                }
                if (hex.length() == 8) {
                    return (int) Long.parseLong(hex, 16);
                }
            } catch (NumberFormatException ignored) {
                // reported below
            }
            warnings.add(path + ": \"" + value + "\" is not #RRGGBB or #AARRGGBB");
            return fallback;
        }

        EmitterType emitter(String path) {
            ConfigurationSection section = root.getConfigurationSection(path);
            if (section == null) {
                warnings.add(path + ": expected particle/count/offset/speed");
                return null;
            }
            String name = section.getString("particle", "");
            Particle particle;
            try {
                particle = Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warnings.add(path + ".particle: unknown particle " + name + ", emitter skipped");
                return null;
            }
            Color color = null;
            Class<?> data = particle.getDataType();
            if (data == Particle.DustOptions.class || data == Color.class) {
                int argb = argb(path + ".color", 0xFFFFFFFF);
                color = Color.fromRGB(argb & 0xFFFFFF);
            } else if (data != Void.class) {
                warnings.add(path + ".particle: " + particle.name() + " needs extra data and cannot be used here, emitter skipped");
                return null;
            }
            double[] offset = {0, 0, 0};
            String rawOffset = section.getString("offset", "0 0 0");
            String[] parts = rawOffset.trim().split("[\\s,]+");
            try {
                if (parts.length != 3) {
                    throw new NumberFormatException();
                }
                for (int i = 0; i < 3; i++) {
                    offset[i] = Double.parseDouble(parts[i]);
                }
            } catch (NumberFormatException e) {
                warnings.add(path + ".offset: expected \"x y z\", using 0 0 0");
                offset = new double[]{0, 0, 0};
            }
            int count = section.getInt("count", 1);
            if (count < 0 || count > 100) {
                warnings.add(path + ".count: must be 0-100, using 1");
                count = 1;
            }
            return new EmitterType(particle, count, offset[0], offset[1], offset[2], section.getDouble("speed", 0), color);
        }
    }
}
