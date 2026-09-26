package net.pvpserver.lobby.world;

import net.pvpserver.core.arena.TemplateBuilder;
import net.pvpserver.lobby.layout.BlockPos;
import net.pvpserver.lobby.layout.LobbyLayout;
import net.pvpserver.lobby.layout.Point;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sign tags in an imported lobby become a layout, and the signs themselves disappear (or become plates and eggs).
 */
class TagLayoutTest {

    private final TemplateBuilder b = new TemplateBuilder(40, 20, 40);
    private final TagLayout.Blocks blocks = new TagLayout.Blocks() {
        @Override
        public String get(int x, int y, int z) {
            return b.get(x, y, z);
        }

        @Override
        public void set(int x, int y, int z, String block) {
            b.set(x, y, z, block);
        }
    };

    private TagLayout.SignTag sign(String text, int x, int y, int z, String state) {
        b.set(x, y, z, state);
        return new TagLayout.SignTag(text, x, y, z);
    }

    @Test
    void everyTagKindLandsInTheLayout() {
        b.fill(0, 0, 0, 39, 0, 39, "stone");
        b.set(20, 1, 20, "anvil[facing=north]");
        List<TagLayout.SignTag> tags = List.of(
                sign("spawn", 10, 1, 10, "oak_sign[rotation=8]"),
                sign("npc ranked", 12, 1, 5, "oak_sign[rotation=0]"),
                sign("npc kit editor", 14, 1, 5, "oak_wall_sign[facing=east]"),
                sign("hologram parkour", 5, 1, 30, "oak_sign[rotation=0]"),
                sign("portal ranked", 20, 1, 35, "oak_sign[rotation=0]"),
                sign("portal ffa nodebuff", 25, 1, 35, "oak_sign[rotation=0]"),
                sign("pad 3", 30, 1, 10, "oak_sign[rotation=0]"),
                sign("parkour start", 2, 1, 2, "oak_sign[rotation=0]"),
                sign("checkpoint", 2, 3, 8, "oak_sign[rotation=0]"),
                sign("checkpoint", 2, 2, 5, "oak_sign[rotation=0]"),
                sign("parkour finish", 2, 4, 12, "oak_sign[rotation=0]"),
                sign("egg", 38, 1, 38, "oak_sign[rotation=0]"),
                sign("egg roof", 1, 10, 1, "oak_sign[rotation=0]"),
                sign("button kit editor", 20, 2, 20, "oak_sign[rotation=0]"),
                sign("zone ranked 15", 20, 1, 30, "oak_sign[rotation=0]"),
                sign("particles fountain", 18, 1, 18, "oak_sign[rotation=0]"),
                sign("wall 4", 30, 8, 30, "oak_wall_sign[facing=west]"),
                sign("border 120", 20, 1, 21, "oak_sign[rotation=0]"),
                sign("void", 20, 1, 22, "oak_sign[rotation=0]"),
                sign("dance party", 21, 1, 21, "oak_sign[rotation=0]"));
        TagLayout.Result result = TagLayout.build(blocks, tags, Point.of(0.5, 1, 0.5), -10, null);
        LobbyLayout layout = result.layout();

        assertTrue(result.hasSpawn());
        assertEquals(19, result.used());
        assertEquals(1, result.notes().size(), result.notes().toString());
        assertTrue(result.notes().get(0).contains("dance party"));
        // Sign text faces north (rotation 8): the player placed it looking south, so they spawn looking south.
        assertEquals(new Point(10.5, 1, 10.5, 0, 0), layout.spawn());
        assertEquals(new Point(12.5, 1, 5.5, 0, 0), layout.npcs().get("ranked"), "NPCs face the text side");
        assertEquals(-90f, layout.npcs().get("kit-editor").yaw());
        assertEquals(Point.of(5.5, 2.2, 30.5), layout.holograms().get("parkour"));
        assertEquals("queue-ranked", layout.portals().get(0).action());
        assertEquals("ffa:nodebuff", layout.portals().get(1).action());
        assertEquals(new BlockPos(19, 1, 34), layout.portals().get(0).box().min());
        assertEquals(new BlockPos(21, 4, 36), layout.portals().get(0).box().max());
        LobbyLayout.LaunchPad pad = layout.pads().get(0);
        assertEquals(0, pad.vx(), 1e-9);
        assertEquals(-3, pad.vz(), 1e-9, "text faces south, so the pad pushes north (the way the builder looked)");
        assertEquals(1.5, pad.vy(), 1e-9);
        assertEquals(new BlockPos(2, 1, 2), layout.parkour().start());
        assertEquals(List.of(new BlockPos(2, 2, 5), new BlockPos(2, 3, 8)), layout.parkour().checkpoints(), "nearest-first order");
        assertEquals(new BlockPos(2, 4, 12), layout.parkour().finish());
        assertEquals(-3, layout.parkour().fallY());
        assertEquals(List.of("egg-1", "roof"), layout.eggs().stream().map(LobbyLayout.Egg::id).toList());
        assertEquals(new LobbyLayout.Button(new BlockPos(20, 1, 20), "kit-editor"), layout.buttons().get(0));
        assertEquals(new LobbyLayout.Zone("ranked", 20.5, 30.5, 15), layout.zones().get(0));
        assertEquals("fountain", layout.emitters().get(0).type());
        assertEquals(4, layout.wall().columns());
        assertEquals(90f, layout.wall().at().yaw());
        assertEquals(120, layout.border().size());
        assertEquals(1, layout.voidY());

        // Signs are gone; plates and eggs replace the ones that mark them.
        assertTrue(b.isAir(10, 1, 10));
        assertTrue(b.get(2, 1, 2).contains("light_weighted_pressure_plate"));
        assertTrue(b.get(2, 4, 12).contains("heavy_weighted_pressure_plate"));
        assertTrue(b.get(30, 1, 10).contains("heavy_weighted_pressure_plate"));
        assertEquals("minecraft:dragon_egg", b.get(38, 1, 38));
        assertTrue(b.get(20, 1, 20).contains("anvil"), "the button's block stays");
        assertFalse(b.isAir(21, 1, 21), "unknown tags keep their sign");
    }

    @Test
    void missingPiecesAreReported() {
        TagLayout.Result result = TagLayout.build(blocks, List.of(sign("parkour start", 1, 1, 1, "oak_sign[rotation=0]")),
                Point.of(5.5, 3, 5.5), 0, null);
        assertFalse(result.hasSpawn());
        assertEquals(Point.of(5.5, 3, 5.5), result.layout().spawn());
        assertNull(result.layout().parkour());
        assertEquals(2, result.notes().size(), result.notes().toString());
    }

    @Test
    void actionsFromModeWords() {
        assertEquals("queue-unranked", TagLayout.action("unranked"));
        assertEquals("ffa", TagLayout.action("ffa"));
        assertEquals("kit-editor", TagLayout.action("kit editor"));
        assertEquals("party-create", TagLayout.action("party"));
        assertEquals("stats", TagLayout.action("stats"));
        assertEquals(180f, TagLayout.textYaw("minecraft:oak_sign[rotation=8,waterlogged=false]"));
        assertEquals(-90f, TagLayout.textYaw("minecraft:oak_sign[rotation=12,waterlogged=false]"));
        assertEquals(90f, TagLayout.textYaw("minecraft:spruce_wall_sign[facing=west,waterlogged=false]"));
    }
}
