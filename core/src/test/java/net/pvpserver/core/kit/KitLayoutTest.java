package net.pvpserver.core.kit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitLayoutTest {

    @Test
    void serializeRoundTrips() {
        KitLayout layout = KitLayout.parse("0:3,1:0,5:8");
        assertEquals("0:3,1:0,5:8", layout.serialize());
        assertFalse(layout.isIdentity());
    }

    @Test
    void malformedPairsAreIgnored() {
        KitLayout layout = KitLayout.parse("0:3,x:1,4:99,7:7,:");
        assertEquals("0:3,7:7", layout.serialize());
    }

    @Test
    void emptyLayoutIsIdentity() {
        assertTrue(KitLayout.parse(null).isIdentity());
        assertTrue(KitLayout.parse("").isIdentity());
        assertTrue(KitLayout.parse("2:2").isIdentity());
    }

    @Test
    void fromPositionsMapsOriginToCurrentSlot() {
        int[] origins = new int[36];
        java.util.Arrays.fill(origins, -1);
        origins[0] = 1;   // item from definition slot 1 now in slot 0
        origins[1] = 0;   // swapped
        origins[20] = 8;  // food moved into the inventory
        KitLayout layout = KitLayout.fromPositions(origins);
        assertEquals("0:1,1:0,8:20", layout.serialize());
    }
}
