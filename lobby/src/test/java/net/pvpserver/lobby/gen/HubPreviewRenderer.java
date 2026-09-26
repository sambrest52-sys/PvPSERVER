package net.pvpserver.lobby.gen;

import net.pvpserver.core.arena.TemplateBuilder;
import net.pvpserver.lobby.layout.BlockPos;
import net.pvpserver.lobby.layout.LobbyLayout;
import net.pvpserver.lobby.layout.Point;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders the generated hub: a labelled top-down map and an isometric view. Written by {@link HubPreviewTest} into
 * {@code lobby/target/previews}; the copies in {@code docs/images} come from there.
 */
public final class HubPreviewRenderer {

    private static final Map<String, Color> COLORS = new LinkedHashMap<>();

    static {
        // Most specific keywords first.
        put("barrier", 0, 0, 0);
        put("weathered_copper", 95, 150, 110);
        put("weathered_cut_copper", 95, 150, 110);
        put("oxidized", 80, 165, 135);
        put("water", 52, 110, 220);
        put("sea_lantern", 205, 235, 235);
        put("glass", 190, 230, 240);
        put("cherry_leaves", 236, 160, 200);
        put("leaves", 60, 125, 50);
        put("azalea", 90, 140, 60);
        put("grass_block", 96, 165, 64);
        put("short_grass", 96, 165, 64);
        put("moss", 90, 130, 50);
        put("gold_block", 245, 205, 60);
        put("iron_block", 215, 215, 215);
        put("diamond_block", 100, 230, 225);
        put("netherite_block", 70, 64, 68);
        put("emerald", 60, 200, 110);
        put("redstone_block", 190, 30, 20);
        put("beacon", 150, 240, 240);
        put("yellow", 245, 195, 45);
        put("orange", 235, 125, 30);
        put("red_nether", 110, 20, 25);
        put("red", 180, 40, 40);
        put("magenta", 190, 70, 180);
        put("pink", 235, 150, 180);
        put("purple", 120, 50, 170);
        put("lime", 120, 200, 50);
        put("cyan", 30, 150, 160);
        put("light_blue", 110, 180, 230);
        put("brown", 110, 75, 45);
        put("white", 235, 235, 235);
        put("purpur", 170, 125, 170);
        put("amethyst", 145, 100, 200);
        put("calcite", 225, 225, 220);
        put("quartz", 236, 230, 222);
        put("prismarine", 80, 160, 145);
        put("crying_obsidian", 60, 20, 90);
        put("obsidian", 30, 20, 45);
        put("gilded", 90, 70, 40);
        put("blackstone", 48, 42, 50);
        put("deepslate", 70, 70, 75);
        put("crimson", 140, 30, 40);
        put("copper", 80, 160, 130);
        put("cherry", 200, 140, 140);
        put("birch", 215, 205, 160);
        put("dark_oak", 70, 50, 30);
        put("spruce", 115, 85, 55);
        put("oak", 165, 130, 80);
        put("bookshelf", 140, 100, 60);
        put("hay", 200, 170, 40);
        put("bricks", 150, 80, 65);
        put("dirt", 125, 90, 60);
        put("andesite", 135, 135, 138);
        put("diorite", 205, 205, 205);
        put("cobble", 115, 115, 115);
        put("tuff", 105, 105, 95);
        put("smooth_stone", 165, 165, 165);
        put("stone", 125, 125, 125);
        put("lantern", 250, 200, 100);
        put("campfire", 240, 150, 50);
        put("end_rod", 250, 245, 230);
    }

    private static void put(String key, int r, int g, int b) {
        COLORS.put(key, new Color(r, g, b));
    }

    private HubPreviewRenderer() {
    }

    static Color colour(String block) {
        String id = block.substring(block.indexOf(':') + 1);
        for (Map.Entry<String, Color> entry : COLORS.entrySet()) {
            if (id.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return new Color(160, 160, 160);
    }

    private static Color shade(Color c, double factor) {
        return new Color((int) Math.max(0, Math.min(255, c.getRed() * factor)), (int) Math.max(0, Math.min(255, c.getGreen() * factor)),
                (int) Math.max(0, Math.min(255, c.getBlue() * factor)));
    }

    private static boolean visible(String block) {
        return !block.endsWith(":air") && !block.contains("barrier");
    }

    /** Top-down map with height shading, labels and markers. */
    static BufferedImage topDown(HubBlueprint hub, int scale) {
        TemplateBuilder b = hub.blocks();
        int[][] height = new int[b.sizeX()][b.sizeZ()];
        BufferedImage image = new BufferedImage(b.sizeX() * scale, b.sizeZ() * scale, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < b.sizeX(); x++) {
            for (int z = 0; z < b.sizeZ(); z++) {
                height[x][z] = -1;
                for (int y = b.sizeY() - 1; y >= 0; y--) {
                    if (visible(b.get(x, y, z))) {
                        height[x][z] = y;
                        break;
                    }
                }
            }
        }
        for (int x = 0; x < b.sizeX(); x++) {
            for (int z = 0; z < b.sizeZ(); z++) {
                Color colour = new Color(14, 18, 30);
                int y = height[x][z];
                if (y >= 0) {
                    Color base = colour(b.get(x, y, z));
                    double light = 0.72 + 0.5 * (y - hub.floorY()) / 60.0;
                    int west = x > 0 ? height[x - 1][z] : y;
                    int north = z > 0 ? height[x][z - 1] : y;
                    light += 0.06 * Math.signum(y - west) + 0.06 * Math.signum(y - north);
                    colour = shade(base, Math.max(0.35, Math.min(1.35, light)));
                }
                for (int dx = 0; dx < scale; dx++) {
                    for (int dz = 0; dz < scale; dz++) {
                        image.setRGB(x * scale + dx, z * scale + dz, colour.getRGB());
                    }
                }
            }
        }
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        LobbyLayout layout = hub.layout();
        g.setStroke(new BasicStroke(Math.max(1, scale / 2f)));
        g.setColor(new Color(120, 255, 140));
        BlockPos previous = null;
        for (BlockPos stone : hub.parkourStones()) {
            if (previous != null) {
                g.drawLine(previous.x() * scale + scale / 2, previous.z() * scale + scale / 2, stone.x() * scale + scale / 2, stone.z() * scale + scale / 2);
            }
            previous = stone;
        }
        for (LobbyLayout.Portal portal : layout.portals()) {
            g.setColor(Color.decode(portal.color()));
            BlockPos min = portal.box().min();
            BlockPos max = portal.box().max();
            g.fillRect(min.x() * scale, min.z() * scale - scale, (max.x() - min.x() + 1) * scale, (max.z() - min.z() + 3) * scale);
        }
        for (LobbyLayout.LaunchPad pad : layout.pads()) {
            dot(g, pad.at().x() + 0.5, pad.at().z() + 0.5, scale, new Color(255, 70, 70), 1.4);
            g.setColor(new Color(255, 120, 120, 170));
            double[][] path = LaunchMath.trajectory(pad.at().x() + 0.5, pad.at().y(), pad.at().z() + 0.5,
                    new double[]{pad.vx(), pad.vy(), pad.vz()}, 60);
            for (int i = 1; i < path.length; i++) {
                if (path[i][1] < pad.at().y() - 2 && path[i][1] < path[i - 1][1] && i > 20) {
                    break;
                }
                g.drawLine((int) (path[i - 1][0] * scale), (int) (path[i - 1][2] * scale), (int) (path[i][0] * scale), (int) (path[i][2] * scale));
            }
        }
        for (LobbyLayout.Egg egg : layout.eggs()) {
            dot(g, egg.at().x() + 0.5, egg.at().z() + 0.5, scale, new Color(255, 215, 0), 1.2);
        }
        for (Point npc : layout.npcs().values()) {
            dot(g, npc.x(), npc.z(), scale, Color.WHITE, 1.3);
        }
        dot(g, layout.spawn().x(), layout.spawn().z(), scale, new Color(80, 170, 255), 2.2);
        try {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, scale * 4)));
            for (LobbyLayout.Zone zone : layout.zones()) {
                String name = switch (zone.id()) {
                    case "plaza" -> "";
                    case "kit-editor" -> "KIT WORKSHOP";
                    case "leaderboards" -> "HALL OF FAME";
                    case "info" -> "INFO";
                    case "party" -> "PARTY LOUNGE";
                    case "cosmetics" -> "COSMETICS";
                    case "parkour" -> "SKY PARKOUR";
                    default -> zone.id().toUpperCase() + (zone.id().equals("ffa") ? " GATE" : " HALL");
                };
                int width = g.getFontMetrics().stringWidth(name);
                int px = (int) (zone.x() * scale) - width / 2;
                int pz = (int) ((zone.z() + (zone.id().equals("parkour") ? -13 : 17)) * scale);
                g.setColor(new Color(0, 0, 0, 170));
                g.drawString(name, px + 2, pz + 2);
                g.setColor(Color.WHITE);
                g.drawString(name, px, pz);
            }
        } catch (RuntimeException | Error noFonts) {
            // headless machine without fonts: markers only
        }
        g.dispose();
        return image;
    }

    private static void dot(Graphics2D g, double x, double z, int scale, Color colour, double size) {
        int r = (int) Math.max(2, scale * size);
        g.setColor(Color.BLACK);
        g.fillOval((int) (x * scale) - r - 1, (int) (z * scale) - r - 1, 2 * r + 2, 2 * r + 2);
        g.setColor(colour);
        g.fillOval((int) (x * scale) - r, (int) (z * scale) - r, 2 * r, 2 * r);
    }

    /** Isometric view from the south-east, drawn back to front. */
    static BufferedImage isometric(HubBlueprint hub, int s, int minY, int crop) {
        TemplateBuilder b = hub.blocks();
        int from = crop;
        int to = b.sizeX() - crop;
        int span = to - from;
        int width = 2 * span * s + 4 * s;
        int height = span * s + (b.sizeY() - minY) * s + 4 * s;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(120, 170, 230), 0, height, new Color(215, 232, 250)));
        g.fillRect(0, 0, width, height);
        int ox = span * s + 2 * s;
        int oy = (b.sizeY() - minY) * s + 2 * s;
        for (int d = 0; d <= 2 * (span - 1); d++) {
            for (int y = minY; y < b.sizeY(); y++) {
                for (int x = Math.max(0, d - span + 1); x <= Math.min(d, span - 1); x++) {
                    int z = d - x;
                    int wx = from + x;
                    int wz = from + z;
                    String block = b.get(wx, y, wz);
                    if (!visible(block)) {
                        continue;
                    }
                    boolean top = !visible(b.get(wx, y + 1, wz)) || b.get(wx, y + 1, wz).contains("water");
                    boolean east = !visible(b.get(wx + 1, y, wz));
                    boolean south = !visible(b.get(wx, y, wz + 1));
                    if (!top && !east && !south) {
                        continue;
                    }
                    Color c = colour(block);
                    int sx = ox + (x - z) * s;
                    int sy = oy + (x + z) * s / 2 - (y - minY) * s;
                    if (south) {
                        g.setColor(shade(c, 0.62));
                        g.fillPolygon(new int[]{sx - s, sx, sx, sx - s}, new int[]{sy, sy + s / 2, sy + s / 2 + s, sy + s}, 4);
                    }
                    if (east) {
                        g.setColor(shade(c, 0.8));
                        g.fillPolygon(new int[]{sx, sx + s, sx + s, sx}, new int[]{sy + s / 2, sy, sy + s, sy + s / 2 + s}, 4);
                    }
                    if (top) {
                        g.setColor(block.contains("water") ? new Color(70, 130, 230) : c);
                        g.fillPolygon(new int[]{sx - s, sx, sx + s, sx}, new int[]{sy, sy - s / 2, sy, sy + s / 2}, 4);
                    }
                }
            }
        }
        g.dispose();
        return image;
    }

    /**
     * Writes the previews.
     *
     * @param folder output folder
     * @param seed seed
     * @return written files
     * @throws IOException on write failure
     */
    public static File[] write(File folder, long seed) throws IOException {
        HubBlueprint hub = HubGenerator.generate(seed);
        folder.mkdirs();
        File map = new File(folder, "lobby-map.png");
        File iso = new File(folder, "lobby-isometric.png");
        ImageIO.write(topDown(hub, 5), "png", map);
        ImageIO.write(isometric(hub, 4, hub.floorY() - 30, 10), "png", iso);
        return new File[]{map, iso};
    }

    /**
     * @param args output folder (default lobby/target/previews)
     * @throws IOException on write failure
     */
    public static void main(String[] args) throws IOException {
        for (File file : write(new File(args.length > 0 ? args[0] : "lobby/target/previews"), 1337)) {
            System.out.println("Wrote " + file.getAbsolutePath());
        }
    }
}
