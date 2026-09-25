package net.pvpserver.core.arena;

import net.pvpserver.core.config.ConfigFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Generates placeholder arenas on first start so the server is playable out of the box:
 * {@code classic} and {@code nether} (standard + build), {@code sumo} (sumo) and {@code bridge} (bridge).
 */
final class ArenaGenerator {

    private ArenaGenerator() {
    }

    static void generateDefaults(Logger logger, File folder, ConfigFile arenasFile) {
        folder.mkdirs();
        try {
            flat("classic", "<green>Classic", "GRASS_BLOCK", "minecraft:grass_block[snowy=false]", "minecraft:dirt",
                    "minecraft:stone_bricks", "minecraft:white_stained_glass", "minecraft:oak_log[axis=y]", folder, arenasFile);
            flat("nether", "<red>Nether", "NETHERRACK", "minecraft:netherrack", "minecraft:netherrack",
                    "minecraft:nether_bricks", "minecraft:red_stained_glass", "minecraft:glowstone", folder, arenasFile);
            sumo(folder, arenasFile);
            bridge(folder, arenasFile);
            arenasFile.save();
            logger.info("Generated 4 placeholder arenas (classic, nether, sumo, bridge)");
        } catch (IOException e) {
            logger.severe("Could not generate placeholder arenas: " + e.getMessage());
        }
    }

    private static void flat(String name, String display, String icon, String top, String under, String border,
                             String wall, String pillar, File folder, ConfigFile file) throws IOException {
        int size = 41;
        TemplateBuilder b = new TemplateBuilder(size, 16, size);
        b.fill(0, 0, 0, size - 1, 0, size - 1, "minecraft:bedrock");
        b.fill(0, 1, 0, size - 1, 1, size - 1, under);
        b.fill(0, 2, 0, size - 1, 2, size - 1, top);
        // Border ring on the floor and glass walls around the edge.
        b.fill(0, 2, 0, size - 1, 2, 0, border);
        b.fill(0, 2, size - 1, size - 1, 2, size - 1, border);
        b.fill(0, 2, 0, 0, 2, size - 1, border);
        b.fill(size - 1, 2, 0, size - 1, 2, size - 1, border);
        b.fill(0, 3, 0, size - 1, 11, 0, wall);
        b.fill(0, 3, size - 1, size - 1, 11, size - 1, wall);
        b.fill(0, 3, 0, 0, 11, size - 1, wall);
        b.fill(size - 1, 3, 0, size - 1, 11, size - 1, wall);
        // Four pillars for cover.
        int[][] pillars = {{12, 12}, {28, 12}, {12, 28}, {28, 28}};
        for (int[] p : pillars) {
            b.fill(p[0], 3, p[1], p[0], 6, p[1], pillar);
        }
        b.build().write(new File(folder, name + ".arena"));
        define(file, name, display, icon, List.of("standard", "build"),
                "6.5,3,20.5,-90,0", "34.5,3,20.5,90,0", "20.5,9,20.5,0,60", 10, -6, null, null);
    }

    private static void sumo(File folder, ConfigFile file) throws IOException {
        int size = 21;
        TemplateBuilder b = new TemplateBuilder(size, 10, size);
        int c = size / 2;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                double d = Math.sqrt((x - c) * (x - c) + (z - c) * (z - c));
                if (d <= 8.5) {
                    b.set(x, 3, z, "minecraft:stone");
                    b.set(x, 4, z, d > 7.5 ? "minecraft:white_wool" : "minecraft:smooth_stone");
                }
            }
        }
        b.build().write(new File(folder, "sumo.arena"));
        define(file, "sumo", "<yellow>Sumo", "LEAD", List.of("sumo"),
                "7.5,5,10.5,-90,0", "13.5,5,10.5,90,0", "10.5,10,3.5,0,45", 5, 1, null, null);
    }

    private static void bridge(File folder, ConfigFile file) throws IOException {
        int sx = 15;
        int sy = 24;
        int sz = 61;
        TemplateBuilder b = new TemplateBuilder(sx, sy, sz);
        // Islands: team A (red) at the low z end, team B (blue) at the high z end.
        b.fill(1, 6, 0, 13, 8, 12, "minecraft:red_terracotta");
        b.fill(1, 6, 48, 13, 8, 60, "minecraft:blue_terracotta");
        // Goals: 3x3 holes two blocks deep at the back of each island.
        b.fill(6, 7, 1, 8, 8, 3, "minecraft:air");
        b.fill(6, 6, 1, 8, 6, 3, "minecraft:black_concrete");
        b.fill(6, 7, 57, 8, 8, 59, "minecraft:air");
        b.fill(6, 6, 57, 8, 6, 59, "minecraft:black_concrete");
        // Three-wide centre bridge.
        b.fill(6, 8, 13, 8, 8, 47, "minecraft:white_terracotta");
        b.fill(7, 8, 29, 7, 8, 31, "minecraft:light_gray_terracotta");
        b.build().write(new File(folder, "bridge.arena"));
        define(file, "bridge", "<aqua>Bridge", "BLUE_TERRACOTTA", List.of("bridge"),
                "7.5,9,9.5,0,0", "7.5,9,51.5,180,0", "12.5,16,30.5,90,45", 14, 1, "7.5,7,2.5,0,0", "7.5,7,58.5,0,0");
    }

    private static void define(ConfigFile file, String name, String display, String icon, List<String> tags, String spawnA,
                               String spawnB, String spectator, int buildLimit, int voidY, String goalA, String goalB) {
        String base = "arenas." + name + ".";
        file.get().set(base + "display-name", display);
        file.get().set(base + "icon", icon);
        file.get().set(base + "enabled", true);
        file.get().set(base + "tags", tags);
        file.get().set(base + "spawn-a", spawnA);
        file.get().set(base + "spawn-b", spawnB);
        file.get().set(base + "spectator", spectator);
        file.get().set(base + "build-limit", buildLimit);
        file.get().set(base + "void-y", voidY);
        if (goalA != null) {
            file.get().set(base + "goal-a", goalA);
            file.get().set(base + "goal-b", goalB);
            file.get().set(base + "goal-radius", 1.6);
        }
    }
}
