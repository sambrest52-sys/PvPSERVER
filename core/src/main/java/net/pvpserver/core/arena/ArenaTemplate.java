package net.pvpserver.core.arena;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Compact block snapshot ("schematic") of an arena. Blocks are palette indices laid out as
 * {@code index = (y * sizeZ + z) * sizeX + x}; palette entry 0 is always air.
 * <p>
 * File format ({@code .arena}, gzip): magic {@code PVPA}, version int, sizeX/sizeY/sizeZ ints, palette count +
 * block data strings, then one short per block.
 */
public final class ArenaTemplate {

    private static final int MAGIC = 0x50565041; // "PVPA"
    private static final int VERSION = 1;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final String[] palette;
    private final short[] blocks;
    private BlockData[] parsedPalette;
    private int[] solidIndices;

    /**
     * @param sizeX size along x
     * @param sizeY size along y
     * @param sizeZ size along z
     * @param palette block data strings; index 0 must be air
     * @param blocks palette index per block
     */
    public ArenaTemplate(int sizeX, int sizeY, int sizeZ, String[] palette, short[] blocks) {
        if (blocks.length != sizeX * sizeY * sizeZ) {
            throw new IllegalArgumentException("Block array does not match dimensions");
        }
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = palette;
        this.blocks = blocks;
    }

    /**
     * @param x relative x
     * @param y relative y
     * @param z relative z
     * @return array index
     */
    public int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    /**
     * Parses the palette into {@link BlockData}. Must run on the main thread the first time (block data creation
     * touches registries); later calls are free.
     *
     * @return parsed palette
     */
    public BlockData[] parsedPalette() {
        if (parsedPalette == null) {
            BlockData[] parsed = new BlockData[palette.length];
            for (int i = 0; i < palette.length; i++) {
                try {
                    parsed[i] = Bukkit.createBlockData(palette[i]);
                } catch (IllegalArgumentException e) {
                    parsed[i] = Bukkit.createBlockData("minecraft:air");
                }
            }
            parsedPalette = parsed;
        }
        return parsedPalette;
    }

    /** @return indices of all non-air blocks, cached */
    public int[] solidIndices() {
        if (solidIndices == null) {
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < blocks.length; i++) {
                if (blocks[i] != 0) {
                    list.add(i);
                }
            }
            solidIndices = list.stream().mapToInt(Integer::intValue).toArray();
        }
        return solidIndices;
    }

    /**
     * @param index block index
     * @return relative x
     */
    public int xOf(int index) {
        return index % sizeX;
    }

    /**
     * @param index block index
     * @return relative z
     */
    public int zOf(int index) {
        return (index / sizeX) % sizeZ;
    }

    /**
     * @param index block index
     * @return relative y
     */
    public int yOf(int index) {
        return index / (sizeX * sizeZ);
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

    /** @return palette strings */
    public String[] palette() {
        return palette;
    }

    /** @return raw palette indices */
    public short[] blocks() {
        return blocks;
    }

    /**
     * Writes the template to disk (call off the main thread).
     *
     * @param file target file
     * @throws IOException on IO failure
     */
    public void write(File file) throws IOException {
        file.getParentFile().mkdirs();
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(tmp))))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(sizeX);
            out.writeInt(sizeY);
            out.writeInt(sizeZ);
            out.writeInt(palette.length);
            for (String entry : palette) {
                out.writeUTF(entry);
            }
            for (short block : blocks) {
                out.writeShort(block);
            }
        }
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not replace " + file);
        }
        if (!tmp.renameTo(file)) {
            throw new IOException("Could not move " + tmp + " to " + file);
        }
    }

    /**
     * Reads a template (call off the main thread).
     *
     * @param file source
     * @return template
     * @throws IOException on IO failure or bad format
     */
    public static ArenaTemplate read(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("Not an arena template: " + file.getName());
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported template version " + version);
            }
            int sx = in.readInt();
            int sy = in.readInt();
            int sz = in.readInt();
            if (sx <= 0 || sy <= 0 || sz <= 0 || (long) sx * sy * sz > 64_000_000L) {
                throw new IOException("Invalid template dimensions");
            }
            String[] palette = new String[in.readInt()];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = in.readUTF();
            }
            short[] blocks = new short[sx * sy * sz];
            for (int i = 0; i < blocks.length; i++) {
                blocks[i] = in.readShort();
            }
            return new ArenaTemplate(sx, sy, sz, palette, blocks);
        }
    }
}
