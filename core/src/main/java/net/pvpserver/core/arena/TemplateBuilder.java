package net.pvpserver.core.arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mutable helper to build templates block by block (used by the arena generator, region capture and importers).
 * Block data strings are normalised to {@code minecraft:id[props]}; this class has no Bukkit dependency.
 */
public final class TemplateBuilder {

    private static final String AIR = "minecraft:air";

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final short[] blocks;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Short> lookup = new HashMap<>();

    /**
     * @param sizeX size x
     * @param sizeY size y
     * @param sizeZ size z
     */
    public TemplateBuilder(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = new short[sizeX * sizeY * sizeZ];
        id(AIR);
    }

    private short id(String data) {
        Short existing = lookup.get(data);
        if (existing != null) {
            return existing;
        }
        if (palette.size() >= Short.MAX_VALUE) {
            throw new IllegalStateException("Palette too large");
        }
        short id = (short) palette.size();
        palette.add(data);
        lookup.put(data, id);
        return id;
    }

    private static String normalize(String data) {
        return data.contains(":") && data.indexOf(':') < (data.contains("[") ? data.indexOf('[') : data.length())
                ? data : "minecraft:" + data;
    }

    /**
     * @param x relative x
     * @param y relative y
     * @param z relative z
     * @return whether the position is inside the template
     */
    public boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    /**
     * @param x relative x
     * @param y relative y
     * @param z relative z
     * @param data block data string (e.g. {@code minecraft:stone} or {@code stone_stairs[facing=east]})
     */
    public void set(int x, int y, int z, String data) {
        if (!inside(x, y, z)) {
            return;
        }
        String normalized = normalize(data);
        blocks[(y * sizeZ + z) * sizeX + x] = normalized.equals(AIR) ? 0 : id(normalized);
    }

    /**
     * Sets a block only where there is air.
     *
     * @param x relative x
     * @param y relative y
     * @param z relative z
     * @param data block data string
     */
    public void setIfAir(int x, int y, int z, String data) {
        if (inside(x, y, z) && isAir(x, y, z)) {
            set(x, y, z, data);
        }
    }

    /**
     * @param x relative x
     * @param y relative y
     * @param z relative z
     * @return block data string at the position ({@code minecraft:air} outside the template)
     */
    public String get(int x, int y, int z) {
        return inside(x, y, z) ? palette.get(blocks[(y * sizeZ + z) * sizeX + x]) : AIR;
    }

    /**
     * @param x relative x
     * @param y relative y
     * @param z relative z
     * @return whether the position is air (or outside the template)
     */
    public boolean isAir(int x, int y, int z) {
        return !inside(x, y, z) || blocks[(y * sizeZ + z) * sizeX + x] == 0;
    }

    /**
     * @param x relative x
     * @param z relative z
     * @return highest non-air y in the column, or -1 when empty
     */
    public int highest(int x, int z) {
        for (int y = sizeY - 1; y >= 0; y--) {
            if (!isAir(x, y, z)) {
                return y;
            }
        }
        return -1;
    }

    /**
     * Fills a box (inclusive).
     *
     * @param x1 min x
     * @param y1 min y
     * @param z1 min z
     * @param x2 max x
     * @param y2 max y
     * @param z2 max z
     * @param data block data string
     */
    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String data) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    set(x, y, z, data);
                }
            }
        }
    }

    /** @return size x */
    public int sizeX() {
        return sizeX;
    }

    /** @return size y */
    public int sizeY() {
        return sizeY;
    }

    /** @return size z */
    public int sizeZ() {
        return sizeZ;
    }

    /** @return the distinct block data strings used so far (index 0 is air) */
    public List<String> palette() {
        return List.copyOf(palette);
    }

    // ------------------------------------------------------------------ connected shapes

    private static final Set<String> NOT_FULL = Set.of("air", "water", "lava", "fence", "pane", "iron_bars", "wall",
            "slab", "stairs", "torch", "lantern", "chain", "carpet", "snow", "short_grass", "tall_grass", "fern", "flower",
            "poppy", "dandelion", "daisy", "bluet", "cornflower", "tulip", "orchid", "allium", "dead_bush", "roots",
            "fungus", "lily_pad", "end_rod", "button", "sign", "banner", "candle", "rail", "sapling", "mushroom",
            "pressure_plate", "trapdoor", "door", "ladder", "vine", "lightning_rod", "petals", "bush", "sea_pickle",
            "campfire", "bell", "cactus", "bamboo", "barrier");

    private static String blockId(String data) {
        int bracket = data.indexOf('[');
        String id = bracket < 0 ? data : data.substring(0, bracket);
        return id.substring(id.indexOf(':') + 1);
    }

    private static boolean isFence(String id) {
        return id.endsWith("_fence");
    }

    private static boolean isPane(String id) {
        return id.endsWith("_pane") || id.equals("iron_bars");
    }

    private static boolean isWall(String id) {
        return id.endsWith("_wall") && !id.contains("sign") && !id.contains("banner") && !id.contains("torch")
                && !id.contains("head") && !id.contains("skull") && !id.contains("fan");
    }

    private static boolean isFull(String id) {
        for (String part : NOT_FULL) {
            if (id.contains(part)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sets the connection states of fences, glass panes, iron bars and walls from their neighbours. Pastes do not
     * apply physics, so without this every fence would be a lone post. Call once after all blocks are placed.
     */
    public void connectShapes() {
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}}; // north, east, south, west
        String[] names = {"north", "east", "south", "west"};
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    if (isAir(x, y, z)) {
                        continue;
                    }
                    String data = get(x, y, z);
                    String id = blockId(data);
                    boolean fence = isFence(id);
                    boolean pane = isPane(id);
                    boolean wall = isWall(id);
                    if (!fence && !pane && !wall) {
                        continue;
                    }
                    boolean[] connects = new boolean[4];
                    for (int d = 0; d < 4; d++) {
                        String other = blockId(get(x + dirs[d][0], y, z + dirs[d][1]));
                        connects[d] = isFull(other)
                                || (fence && isFence(other))
                                || (pane && isPane(other))
                                || (wall && (isWall(other) || isPane(other) || isFence(other)));
                    }
                    StringBuilder state = new StringBuilder("minecraft:").append(id).append('[');
                    for (int d = 0; d < 4; d++) {
                        if (d > 0) {
                            state.append(',');
                        }
                        state.append(names[d]).append('=');
                        state.append(wall ? (connects[d] ? "low" : "none") : String.valueOf(connects[d]));
                    }
                    if (wall) {
                        boolean straight = (connects[0] && connects[2] && !connects[1] && !connects[3])
                                || (connects[1] && connects[3] && !connects[0] && !connects[2]);
                        state.append(",up=").append(!straight || !isAir(x, y + 1, z));
                    }
                    state.append(']');
                    set(x, y, z, state.toString());
                }
            }
        }
    }

    /** @return built template */
    public ArenaTemplate build() {
        return new ArenaTemplate(sizeX, sizeY, sizeZ, palette.toArray(new String[0]), blocks.clone());
    }
}
