package net.pvpserver.core.arena.io;

import net.pvpserver.core.arena.TemplateBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads Sponge v2/v3 and legacy MCEdit schematics written byte for byte like WorldEdit does, including sign markers.
 */
class SchematicReaderTest {

    private static final int W = 7;
    private static final int H = 3;
    private static final int L = 5;

    /** 7x3x5 arena: stone floor, a red wool block in the middle, signs [A] and [B] standing on the floor. */
    private static int[] indices(int stone, int wool, int sign) {
        int[] data = new int[W * H * L];
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < L; z++) {
                data[x + z * W] = stone;
            }
        }
        data[3 + 2 * W + W * L] = wool;
        data[1 + 2 * W + W * L] = sign;
        data[5 + 2 * W + W * L] = sign;
        return data;
    }

    private static Map<String, Object> sign(int x, int y, int z, String line) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("messages", List.of("\"" + line + "\"", "\"\"", "\"\"", "\"\""));
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("Pos", new int[]{x, y, z});
        entity.put("Id", "minecraft:oak_sign");
        entity.put("front_text", text);
        return entity;
    }

    private static SchematicReader.Result read(byte[] bytes) throws IOException {
        return SchematicReader.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void readsSpongeV2WithSignMarkers() throws IOException {
        Map<String, Object> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        palette.put("minecraft:stone", 1);
        palette.put("minecraft:red_wool", 2);
        palette.put("minecraft:oak_sign[rotation=0,waterlogged=false]", 3);
        Map<String, Object> schematic = new LinkedHashMap<>();
        schematic.put("Version", 2);
        schematic.put("DataVersion", 4556);
        schematic.put("Width", (short) W);
        schematic.put("Height", (short) H);
        schematic.put("Length", (short) L);
        schematic.put("PaletteMax", 4);
        schematic.put("Palette", palette);
        schematic.put("BlockData", NbtWriter.varints(indices(1, 2, 3)));
        schematic.put("BlockEntities", List.of(sign(1, 1, 2, "[A]"), sign(5, 1, 2, "[ Spawn B ]")));
        SchematicReader.Result result = read(NbtWriter.write("Schematic", schematic));

        TemplateBuilder b = result.blocks();
        assertEquals("Sponge schematic v2", result.format());
        assertEquals("minecraft:stone", b.get(0, 0, 0));
        assertEquals("minecraft:red_wool", b.get(3, 1, 2));
        assertTrue(b.isAir(1, 1, 2), "marker sign removed");
        assertEquals(new SchematicReader.Position(1, 1, 2), result.markers().get(SchematicReader.Marker.SPAWN_A));
        assertEquals(new SchematicReader.Position(5, 1, 2), result.markers().get(SchematicReader.Marker.SPAWN_B));
        assertFalse(result.legacy());
    }

    @Test
    void readsSpongeV3NestedBlocksAndLargePalettes() throws IOException {
        // 200 palette entries force two-byte varints.
        Map<String, Object> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        for (int i = 1; i < 200; i++) {
            palette.put("minecraft:stone_bricks" + (i == 150 ? "" : "_filler_" + i), i);
        }
        int[] data = new int[W * H * L];
        data[0] = 150;
        data[W * H * L - 1] = 199;
        Map<String, Object> blocks = new LinkedHashMap<>();
        blocks.put("Palette", palette);
        blocks.put("Data", NbtWriter.varints(data));
        blocks.put("BlockEntities", List.of(sign(2, 1, 1, "{\"text\":\"[Goal A]\"}")));
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("Version", 3);
        inner.put("DataVersion", 4556);
        inner.put("Width", (short) W);
        inner.put("Height", (short) H);
        inner.put("Length", (short) L);
        inner.put("Blocks", blocks);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Schematic", inner);
        SchematicReader.Result result = read(NbtWriter.write("", root));

        assertEquals("Sponge schematic v3", result.format());
        assertEquals("minecraft:stone_bricks", result.blocks().get(0, 0, 0));
        assertEquals("minecraft:stone_bricks_filler_199", result.blocks().get(W - 1, H - 1, L - 1));
        assertNotNull(result.markers().get(SchematicReader.Marker.GOAL_A));
    }

    @Test
    void readsLegacyMcEditAndResolvesIds() throws IOException {
        int total = W * H * L;
        byte[] ids = new byte[total];
        byte[] data = new byte[total];
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < L; z++) {
                ids[x + z * W] = 1;
            }
        }
        ids[3 + 2 * W + W * L] = 35;
        data[3 + 2 * W + W * L] = 14;
        ids[1 + 2 * W + W * L] = 63;
        // AddBlocks nibble: id 256 + 1 at index 2 (upper id bits), exercising 12-bit ids.
        byte[] add = new byte[(total + 1) / 2];
        ids[2] = 1;
        add[1] = 0x01;
        Map<String, Object> legacySign = new LinkedHashMap<>();
        legacySign.put("id", "Sign");
        legacySign.put("x", 1);
        legacySign.put("y", 1);
        legacySign.put("z", 2);
        legacySign.put("Text1", "{\"text\":\"[a]\"}");
        legacySign.put("Text2", "");
        Map<String, Object> schematic = new LinkedHashMap<>();
        schematic.put("Width", (short) W);
        schematic.put("Height", (short) H);
        schematic.put("Length", (short) L);
        schematic.put("Materials", "Alpha");
        schematic.put("Blocks", ids);
        schematic.put("Data", data);
        schematic.put("AddBlocks", add);
        schematic.put("TileEntities", List.of(legacySign));
        SchematicReader.Result result = read(NbtWriter.write("Schematic", schematic));

        assertTrue(result.legacy());
        TemplateBuilder b = result.blocks();
        assertEquals("legacy:1:0", b.get(0, 0, 0));
        assertEquals("legacy:35:14", b.get(3, 1, 2));
        assertEquals("legacy:257:0", b.get(2, 0, 0), "AddBlocks extend ids past 255");
        assertTrue(b.isAir(1, 1, 2), "legacy sign marker removed");
        assertEquals(new SchematicReader.Position(1, 1, 2), result.markers().get(SchematicReader.Marker.SPAWN_A));

        b.remapPalette(entry -> switch (entry) {
            case "legacy:1:0" -> "minecraft:stone";
            case "legacy:35:14" -> "minecraft:red_wool";
            default -> "minecraft:air";
        });
        assertEquals("minecraft:stone", b.get(0, 0, 0));
        assertEquals("minecraft:red_wool", b.get(3, 1, 2));
        assertTrue(b.isAir(2, 0, 0), "unknown ids become air");
        assertFalse(b.palette().contains("legacy:1:0"));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IOException.class, () -> read(new byte[]{1, 2, 3, 4}));
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("Width", (short) 0);
        empty.put("Version", 2);
        assertThrows(IOException.class, () -> read(NbtWriter.write("Schematic", empty)));
    }

    @Test
    void markerTextVariants() {
        assertEquals(SchematicReader.Marker.SPAWN_A, SchematicReader.marker("{\"text\":\"[A]\"}"));
        assertEquals(SchematicReader.Marker.SPAWN_B, SchematicReader.marker("\"[team b]\""));
        assertEquals(SchematicReader.Marker.SPECTATOR, SchematicReader.marker("[Spectator]"));
        assertEquals(SchematicReader.Marker.GOAL_B, SchematicReader.marker("[goal 2]"));
        assertNull(SchematicReader.marker("[shop]"));
        assertNull(SchematicReader.marker("welcome"));
    }

    @Test
    void guessesSpawnsWhenThereAreNoMarkers() {
        TemplateBuilder b = new TemplateBuilder(30, 6, 11);
        b.fill(0, 0, 0, 29, 0, 10, "minecraft:stone");
        b.fill(14, 1, 0, 15, 4, 10, "minecraft:stone_bricks");
        int[][] spawns = SpawnFinder.guessSpawns(b);
        assertNotNull(spawns);
        assertArrayEquals(new int[]{7, 1, 5}, spawns[0]);
        assertArrayEquals(new int[]{22, 1, 5}, spawns[1]);
        assertNull(SpawnFinder.guessSpawns(new TemplateBuilder(10, 4, 10)), "an empty schematic has nowhere to stand");
    }
}
