package net.pvpserver.core.arena;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable helper to build templates block by block (used by the placeholder generator and region capture).
 */
public final class TemplateBuilder {

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
        id("minecraft:air");
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

    /**
     * @param x relative x
     * @param y relative y
     * @param z relative z
     * @param data block data string (e.g. {@code minecraft:stone})
     */
    public void set(int x, int y, int z, String data) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            return;
        }
        String normalized = data.contains(":") ? data : "minecraft:" + data;
        blocks[(y * sizeZ + z) * sizeX + x] = normalized.equals("minecraft:air") ? 0 : id(normalized);
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

    /** @return built template */
    public ArenaTemplate build() {
        return new ArenaTemplate(sizeX, sizeY, sizeZ, palette.toArray(new String[0]), blocks);
    }
}
