package net.pvpserver.core.world;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Logger;

/**
 * Creates and tunes gamemode worlds: void terrain, no mobs/weather/daylight/fire spread, no autosave for
 * disposable worlds.
 */
public final class WorldService {

    private final Logger logger;

    /**
     * @param logger logger
     */
    public WorldService(Logger logger) {
        this.logger = logger;
    }

    /**
     * Loads (or creates) a void world and applies the practice game rules.
     *
     * @param name world name
     * @param disposable delete any existing copy first and disable saving (arena instances)
     * @return world
     */
    public World voidWorld(String name, boolean disposable) {
        World existing = Bukkit.getWorld(name);
        if (existing != null && disposable) {
            Bukkit.unloadWorld(existing, false);
            existing = null;
        }
        if (disposable) {
            delete(new File(Bukkit.getWorldContainer(), name));
        }
        World world = existing != null ? existing : new WorldCreator(name)
                .generator(new VoidGenerator())
                .type(WorldType.FLAT)
                .generateStructures(false)
                .environment(World.Environment.NORMAL)
                .createWorld();
        if (world == null) {
            throw new IllegalStateException("Could not create world " + name);
        }
        tune(world, disposable);
        return world;
    }

    /**
     * Applies practice-friendly game rules to any world (lobby/FFA worlds included).
     *
     * @param world world
     * @param disposable whether saving should be disabled
     */
    public void tune(World world, boolean disposable) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.SPAWN_PATROLS, false);
        world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        world.setGameRule(GameRules.SPAWN_WARDENS, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.BLOCK_DROPS, false);
        world.setGameRule(GameRules.ENTITY_DROPS, false);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setGameRule(GameRules.RAIDS, false);
        world.setGameRule(GameRules.SPREAD_VINES, false);
        world.setGameRule(GameRules.WATER_SOURCE_CONVERSION, false);
        world.setGameRule(GameRules.LAVA_SOURCE_CONVERSION, false);
        world.setDifficulty(Difficulty.NORMAL);
        world.setStorm(false);
        world.setThundering(false);
        world.setClearWeatherDuration(Integer.MAX_VALUE);
        world.setTime(6000);
        world.setSpawnFlags(false, false);
        if (disposable) {
            world.setAutoSave(false);
        }
    }

    /**
     * Recursively deletes a world folder.
     *
     * @param folder folder
     */
    public void delete(File folder) {
        if (!folder.exists()) {
            return;
        }
        try {
            Files.walkFileTree(folder.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            logger.info("Deleted stale world folder " + folder.getName());
        } catch (IOException e) {
            logger.warning("Could not fully delete " + folder + ": " + e.getMessage());
        }
    }
}
