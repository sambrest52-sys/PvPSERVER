package net.pvpserver.core.arena.io;

import net.pvpserver.core.arena.TemplateBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads schematic files into a {@link TemplateBuilder}:
 * <ul>
 *   <li>Sponge schematics {@code .schem} versions 1, 2 and 3 (WorldEdit 7+, FAWE, Amulet, ...)</li>
 *   <li>legacy MCEdit/WorldEdit {@code .schematic} files from 1.12 and older (numeric block ids). Their blocks come
 *       back as {@code legacy:<id>:<data>} palette entries, to be resolved with
 *       {@link TemplateBuilder#remapPalette} on the server.</li>
 * </ul>
 * Signs reading {@code [A]}, {@code [B]}, {@code [Spectator]}, {@code [Goal A]} or {@code [Goal B]} are treated as
 * markers: their position is reported and the sign is removed from the template.
 */
public final class SchematicReader {

    /** Markers an arena builder can place as signs. */
    public enum Marker { SPAWN_A, SPAWN_B, SPECTATOR, GOAL_A, GOAL_B }

    /**
     * A marker position relative to the schematic's minimum corner.
     *
     * @param x x
     * @param y y
     * @param z z
     */
    public record Position(int x, int y, int z) {
    }

    /**
     * Read result.
     *
     * @param blocks template contents
     * @param markers marker positions found on signs
     * @param format human readable format name
     * @param legacy whether palette entries still need legacy resolution
     */
    public record Result(TemplateBuilder blocks, Map<Marker, Position> markers, String format, boolean legacy) {
    }

    private static final Pattern TAG = Pattern.compile("\\[\\s*([a-z0-9][a-z0-9 ]*?)\\s*]");
    private static final long MAX_VOLUME = 16_000_000L;

    private SchematicReader() {
    }

    /**
     * @param file schematic file
     * @return result
     * @throws IOException on read failure or unsupported content
     */
    public static Result read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return read(in);
        }
    }

    /**
     * @param in schematic data (gzip or raw NBT)
     * @return result
     * @throws IOException on read failure or unsupported content
     */
    public static Result read(InputStream in) throws IOException {
        Map<String, Object> root = Nbt.read(in);
        Map<String, Object> schematic = Nbt.compound(root, "Schematic");
        if (schematic == null) {
            schematic = root;
        }
        if (schematic.containsKey("Blocks") && schematic.get("Blocks") instanceof byte[]) {
            return readLegacy(schematic);
        }
        int version = Nbt.integer(schematic, "Version", schematic.containsKey("Blocks") ? 3 : 2);
        return version >= 3 ? readSponge3(schematic) : readSponge(schematic, version);
    }

    private static int[] size(Map<String, Object> schematic) throws IOException {
        int width = Nbt.integer(schematic, "Width", 0) & 0xFFFF;
        int height = Nbt.integer(schematic, "Height", 0) & 0xFFFF;
        int length = Nbt.integer(schematic, "Length", 0) & 0xFFFF;
        if (width == 0 || height == 0 || length == 0 || (long) width * height * length > MAX_VOLUME) {
            throw new IOException("Unsupported schematic size " + width + "x" + height + "x" + length);
        }
        return new int[]{width, height, length};
    }

    private static Result readSponge(Map<String, Object> schematic, int version) throws IOException {
        int[] size = size(schematic);
        Map<String, Object> palette = Nbt.compound(schematic, "Palette");
        if (!(schematic.get("BlockData") instanceof byte[] data) || palette == null) {
            throw new IOException("Sponge schematic without Palette/BlockData");
        }
        TemplateBuilder builder = fill(size, palette, data);
        List<Object> entities = Nbt.list(schematic, version == 1 ? "TileEntities" : "BlockEntities");
        if (entities.isEmpty()) {
            entities = Nbt.list(schematic, "TileEntities");
        }
        Map<Marker, Position> markers = markers(builder, entities, false);
        return new Result(builder, markers, "Sponge schematic v" + version, false);
    }

    private static Result readSponge3(Map<String, Object> schematic) throws IOException {
        int[] size = size(schematic);
        Map<String, Object> blocks = Nbt.compound(schematic, "Blocks");
        Map<String, Object> palette = Nbt.compound(blocks, "Palette");
        if (blocks == null || palette == null || !(blocks.get("Data") instanceof byte[] data)) {
            throw new IOException("Sponge v3 schematic without Blocks.Palette/Data");
        }
        TemplateBuilder builder = fill(size, palette, data);
        Map<Marker, Position> markers = markers(builder, Nbt.list(blocks, "BlockEntities"), false);
        return new Result(builder, markers, "Sponge schematic v3", false);
    }

    private static TemplateBuilder fill(int[] size, Map<String, Object> palette, byte[] data) throws IOException {
        String[] byIndex = new String[palette.size()];
        for (Map.Entry<String, Object> entry : palette.entrySet()) {
            int index = ((Number) entry.getValue()).intValue();
            if (index < 0 || index >= byIndex.length) {
                throw new IOException("Palette index out of range: " + index);
            }
            byIndex[index] = entry.getKey();
        }
        TemplateBuilder builder = new TemplateBuilder(size[0], size[1], size[2]);
        int total = size[0] * size[1] * size[2];
        int offset = 0;
        for (int i = 0; i < total; i++) {
            int value = 0;
            int shift = 0;
            while (true) {
                if (offset >= data.length) {
                    throw new IOException("Block data ends early at block " + i);
                }
                int b = data[offset++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
                if (shift > 28) {
                    throw new IOException("Malformed varint in block data");
                }
            }
            if (value < 0 || value >= byIndex.length || byIndex[value] == null) {
                throw new IOException("Unknown palette index " + value);
            }
            String block = byIndex[value];
            if (!block.equals("minecraft:air") && !block.equals("minecraft:cave_air") && !block.equals("minecraft:void_air")
                    && !block.equals("minecraft:structure_void")) {
                int x = i % size[0];
                int z = (i / size[0]) % size[2];
                int y = i / (size[0] * size[2]);
                builder.set(x, y, z, block);
            }
        }
        return builder;
    }

    private static Result readLegacy(Map<String, Object> schematic) throws IOException {
        int[] size = size(schematic);
        byte[] ids = (byte[]) schematic.get("Blocks");
        byte[] data = schematic.get("Data") instanceof byte[] d ? d : new byte[ids.length];
        byte[] add = schematic.get("AddBlocks") instanceof byte[] a ? a : null;
        int total = size[0] * size[1] * size[2];
        if (ids.length < total || data.length < total) {
            throw new IOException("Legacy schematic block arrays are too short");
        }
        TemplateBuilder builder = new TemplateBuilder(size[0], size[1], size[2]);
        for (int i = 0; i < total; i++) {
            int id = ids[i] & 0xFF;
            if (add != null && (i >> 1) < add.length) {
                int nibble = (i & 1) == 0 ? (add[i >> 1] & 0x0F) : ((add[i >> 1] >> 4) & 0x0F);
                id |= nibble << 8;
            }
            if (id == 0) {
                continue;
            }
            int x = i % size[0];
            int z = (i / size[0]) % size[2];
            int y = i / (size[0] * size[2]);
            builder.set(x, y, z, "legacy:" + id + ":" + (data[i] & 0x0F));
        }
        Map<Marker, Position> markers = markers(builder, Nbt.list(schematic, "TileEntities"), true);
        return new Result(builder, markers, "legacy MCEdit schematic", true);
    }

    @SuppressWarnings("unchecked")
    private static Map<Marker, Position> markers(TemplateBuilder builder, List<Object> entities, boolean legacy) {
        Map<Marker, Position> markers = new EnumMap<>(Marker.class);
        for (Object raw : entities) {
            if (!(raw instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> entity = (Map<String, Object>) map;
            String id = String.valueOf(entity.getOrDefault("Id", entity.getOrDefault("id", ""))).toLowerCase(Locale.ROOT);
            if (!id.contains("sign")) {
                continue;
            }
            int[] pos;
            if (entity.get("Pos") instanceof int[] p && p.length == 3) {
                pos = p;
            } else {
                pos = new int[]{Nbt.integer(entity, "x", -1), Nbt.integer(entity, "y", -1), Nbt.integer(entity, "z", -1)};
            }
            List<String> texts = new ArrayList<>();
            collectStrings(entity, texts);
            Marker marker = marker(String.join(" ", texts).toLowerCase(Locale.ROOT));
            if (marker != null && builder.inside(pos[0], pos[1], pos[2])) {
                markers.put(marker, new Position(pos[0], pos[1], pos[2]));
                builder.set(pos[0], pos[1], pos[2], "minecraft:air");
            }
        }
        return markers;
    }

    private static void collectStrings(Object value, List<String> out) {
        if (value instanceof String s) {
            out.add(s);
        } else if (value instanceof Map<?, ?> map) {
            map.values().forEach(v -> collectStrings(v, out));
        } else if (value instanceof List<?> list) {
            list.forEach(v -> collectStrings(v, out));
        }
    }

    /**
     * @param text sign text (any JSON/formatting around the tag is ignored)
     * @return the marker named in square brackets, or null
     */
    static Marker marker(String text) {
        Matcher matcher = TAG.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            switch (matcher.group(1).trim()) {
                case "a", "spawn a", "team a", "spawn 1", "1", "red" -> {
                    return Marker.SPAWN_A;
                }
                case "b", "spawn b", "team b", "spawn 2", "2", "blue" -> {
                    return Marker.SPAWN_B;
                }
                case "spectator", "spec", "spectate" -> {
                    return Marker.SPECTATOR;
                }
                case "goal a", "goal 1", "goal red" -> {
                    return Marker.GOAL_A;
                }
                case "goal b", "goal 2", "goal blue" -> {
                    return Marker.GOAL_B;
                }
                default -> {
                    // not a marker; keep looking
                }
            }
        }
        return null;
    }
}
