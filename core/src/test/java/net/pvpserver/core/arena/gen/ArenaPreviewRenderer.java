package net.pvpserver.core.arena.gen;

import net.pvpserver.core.arena.TemplateBuilder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders top-down, height-shaded previews of every built-in arena into one contact sheet
 * ({@code core/target/arena-previews.png}). Run with {@code mvn -pl core test-compile exec:java} or from an IDE;
 * it is a developer tool, not a test.
 */
public final class ArenaPreviewRenderer {

    private static final Map<String, Color> COLORS = new LinkedHashMap<>();

    static {
        // Most specific keywords first.
        COLORS.put("red_", new Color(160, 40, 36));
        COLORS.put("blue_", new Color(44, 72, 170));
        COLORS.put("orange_", new Color(175, 95, 40));
        COLORS.put("yellow_", new Color(200, 170, 50));
        COLORS.put("magenta", new Color(170, 60, 160));
        COLORS.put("pink", new Color(220, 140, 170));
        COLORS.put("light_blue", new Color(140, 190, 230));
        COLORS.put("light_gray", new Color(150, 150, 150));
        COLORS.put("white_", new Color(225, 225, 225));
        COLORS.put("black_", new Color(30, 30, 30));
        COLORS.put("gray_", new Color(90, 90, 90));
        COLORS.put("brown_", new Color(100, 70, 45));
        COLORS.put("water", new Color(50, 90, 200));
        COLORS.put("lava", new Color(230, 90, 20));
        COLORS.put("lily", new Color(40, 130, 40));
        COLORS.put("ice", new Color(160, 190, 240));
        COLORS.put("snow", new Color(245, 250, 255));
        COLORS.put("leaves", new Color(45, 110, 40));
        COLORS.put("grass", new Color(90, 160, 60));
        COLORS.put("fern", new Color(70, 140, 60));
        COLORS.put("moss", new Color(90, 130, 50));
        COLORS.put("podzol", new Color(110, 80, 40));
        COLORS.put("dirt", new Color(125, 90, 60));
        COLORS.put("red_sand", new Color(190, 100, 40));
        COLORS.put("sand", new Color(220, 205, 150));
        COLORS.put("terracotta", new Color(160, 90, 65));
        COLORS.put("quartz", new Color(235, 230, 225));
        COLORS.put("prismarine", new Color(80, 160, 150));
        COLORS.put("sea_lantern", new Color(200, 235, 235));
        COLORS.put("glowstone", new Color(240, 210, 120));
        COLORS.put("shroomlight", new Color(240, 150, 70));
        COLORS.put("lantern", new Color(250, 200, 100));
        COLORS.put("nylium", new Color(130, 30, 40));
        COLORS.put("warped", new Color(40, 130, 130));
        COLORS.put("nether", new Color(80, 25, 30));
        COLORS.put("blackstone", new Color(45, 40, 45));
        COLORS.put("basalt", new Color(80, 80, 85));
        COLORS.put("obsidian", new Color(40, 20, 60));
        COLORS.put("log", new Color(95, 70, 40));
        COLORS.put("planks", new Color(160, 125, 80));
        COLORS.put("spruce", new Color(110, 80, 50));
        COLORS.put("wool", new Color(200, 200, 200));
        COLORS.put("concrete", new Color(120, 120, 120));
        COLORS.put("chain", new Color(70, 70, 80));
        COLORS.put("iron", new Color(200, 200, 200));
        COLORS.put("sandstone", new Color(215, 195, 135));
        COLORS.put("brick", new Color(120, 120, 115));
        COLORS.put("cobble", new Color(115, 115, 115));
        COLORS.put("andesite", new Color(130, 130, 130));
        COLORS.put("stone", new Color(125, 125, 125));
        COLORS.put("tuff", new Color(105, 105, 95));
        COLORS.put("mud", new Color(60, 55, 55));
    }

    private ArenaPreviewRenderer() {
    }

    private static Color colour(String block) {
        String id = block.substring(block.indexOf(':') + 1);
        for (Map.Entry<String, Color> entry : COLORS.entrySet()) {
            if (id.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return new Color(170, 170, 170);
    }

    /** Renders one arena top-down: the highest non-barrier block of each column, shaded by height. */
    static BufferedImage render(GeneratedArena arena, int scale) {
        TemplateBuilder b = arena.blocks();
        BufferedImage image = new BufferedImage(b.sizeX() * scale, b.sizeZ() * scale, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < b.sizeX(); x++) {
            for (int z = 0; z < b.sizeZ(); z++) {
                Color colour = new Color(12, 14, 22);
                for (int y = b.sizeY() - 1; y >= 0; y--) {
                    String block = b.get(x, y, z);
                    if (!b.isAir(x, y, z) && !block.contains("barrier")) {
                        Color base = colour(block);
                        double shade = 0.55 + 0.45 * y / Math.max(1, b.sizeY() - 1);
                        colour = new Color((int) Math.min(255, base.getRed() * shade + 20), (int) Math.min(255, base.getGreen() * shade + 20),
                                (int) Math.min(255, base.getBlue() * shade + 20));
                        break;
                    }
                }
                for (int dx = 0; dx < scale; dx++) {
                    for (int dz = 0; dz < scale; dz++) {
                        image.setRGB(x * scale + dx, z * scale + dz, colour.getRGB());
                    }
                }
            }
        }
        for (var spawn : List.of(arena.spawnA(), arena.spawnB())) {
            int px = (int) (spawn.x() * scale);
            int pz = (int) (spawn.z() * scale);
            for (int dx = -scale; dx <= scale; dx++) {
                for (int dz = -scale; dz <= scale; dz++) {
                    int ix = px + dx;
                    int iz = pz + dz;
                    if (ix >= 0 && iz >= 0 && ix < image.getWidth() && iz < image.getHeight()) {
                        image.setRGB(ix, iz, spawn == arena.spawnA() ? 0xFF3030 : 0x3060FF);
                    }
                }
            }
        }
        return image;
    }

    /**
     * Writes the contact sheet.
     *
     * @param args optional output path
     * @throws IOException on write failure
     */
    public static void main(String[] args) throws IOException {
        File out = new File(args.length > 0 ? args[0] : "core/target/arena-previews.png");
        List<GeneratedArena> arenas = new ArrayList<>();
        for (String name : BuiltinArenas.names()) {
            arenas.add(BuiltinArenas.generate(name));
        }
        int cell = 250;
        int columns = 6;
        int rows = (arenas.size() + columns - 1) / columns;
        BufferedImage sheet = new BufferedImage(columns * cell, rows * (cell + 24), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(24, 26, 32));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int i = 0; i < arenas.size(); i++) {
            GeneratedArena arena = arenas.get(i);
            int longest = Math.max(arena.blocks().sizeX(), arena.blocks().sizeZ());
            int scale = Math.max(1, (cell - 10) / longest);
            BufferedImage image = render(arena, scale);
            int cx = (i % columns) * cell;
            int cy = (i / columns) * (cell + 24);
            g.drawImage(image, cx + (cell - image.getWidth()) / 2, cy + 24 + (cell - image.getHeight()) / 2, null);
            try {
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                g.drawString(arena.name() + "  " + arena.tags(), cx + 6, cy + 17);
            } catch (RuntimeException | Error noFonts) {
                // headless machine without fonts: images only
            }
        }
        g.dispose();
        out.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", out);
        System.out.println("Wrote " + out.getAbsolutePath());
    }
}
