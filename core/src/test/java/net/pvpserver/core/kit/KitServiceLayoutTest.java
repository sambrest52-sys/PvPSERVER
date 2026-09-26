package net.pvpserver.core.kit;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Saved layouts only apply to the kit version they were made for.
 */
class KitServiceLayoutTest {

    private static Kit kit(String hash) {
        return new Kit("nodebuff", "NoDebuff", null, true, true, true, false, true, 1, "", Set.of("standard"), Set.of(),
                Set.of(), KitRules.DEFAULT, new ItemStack[36], new ItemStack[4], null, List.of(), List.of(), hash);
    }

    @Test
    void layoutForTheCurrentKitVersionApplies() {
        assertEquals("0:1,1:0", KitService.storedLayout(kit("abc123"), "abc123|0:1,1:0").serialize());
    }

    @Test
    void layoutForAnOlderKitVersionIsIgnored() {
        assertTrue(KitService.storedLayout(kit("abc123"), "ffff00|0:1,1:0").isIdentity());
    }

    @Test
    void layoutsSavedBeforeFingerprintsAreIgnored() {
        assertTrue(KitService.storedLayout(kit("abc123"), "0:1,1:0").isIdentity());
        assertTrue(KitService.storedLayout(kit("abc123"), null).isIdentity());
    }

    @Test
    void emptySlotsHashTheSame() {
        assertEquals(Kit.layoutHash(new ItemStack[36]), Kit.layoutHash(new ItemStack[36]));
    }
}
