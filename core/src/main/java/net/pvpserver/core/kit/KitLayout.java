package net.pvpserver.core.kit;

import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A player's rearrangement of a kit's 36 storage slots, stored as {@code "from:to,from:to"} where {@code from} is the
 * slot in the kit definition and {@code to} the slot the player moved it to. Robust against kit edits: unknown
 * mappings are ignored and unplaced items fall back to their default slot or the first free slot.
 */
public final class KitLayout {

    private final Map<Integer, Integer> mapping;

    /**
     * @param mapping definition slot → player slot
     */
    public KitLayout(Map<Integer, Integer> mapping) {
        this.mapping = new LinkedHashMap<>(mapping);
    }

    /**
     * @param raw serialised layout (null/blank = identity)
     * @return layout
     */
    public static KitLayout parse(String raw) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        if (raw != null && !raw.isBlank()) {
            for (String pair : raw.split(",")) {
                String[] parts = pair.split(":");
                if (parts.length == 2) {
                    try {
                        int from = Integer.parseInt(parts[0].trim());
                        int to = Integer.parseInt(parts[1].trim());
                        if (from >= 0 && from < 36 && to >= 0 && to < 36) {
                            map.put(from, to);
                        }
                    } catch (NumberFormatException ignored) {
                        // skip malformed pair
                    }
                }
            }
        }
        return new KitLayout(map);
    }

    /** @return serialised form */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        mapping.forEach((from, to) -> {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(from).append(':').append(to);
        });
        return sb.toString();
    }

    /**
     * Arranges kit contents according to this layout.
     *
     * @param definition 36 definition slots (nullable entries)
     * @return new 36 slot array (items cloned)
     */
    public ItemStack[] apply(ItemStack[] definition) {
        ItemStack[] result = new ItemStack[36];
        boolean[] placed = new boolean[36];
        for (Map.Entry<Integer, Integer> entry : mapping.entrySet()) {
            int from = entry.getKey();
            int to = entry.getValue();
            if (from < definition.length && definition[from] != null && result[to] == null) {
                result[to] = definition[from].clone();
                placed[from] = true;
            }
        }
        for (int from = 0; from < Math.min(36, definition.length); from++) {
            if (definition[from] == null || placed[from]) {
                continue;
            }
            int target = result[from] == null ? from : firstFree(result);
            if (target >= 0) {
                result[target] = definition[from].clone();
            }
        }
        return result;
    }

    private static int firstFree(ItemStack[] slots) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Builds a layout from where tagged kit items currently are.
     *
     * @param originSlotsBySlot for each current slot (0-35), the definition slot of the item there or -1
     * @return layout
     */
    public static KitLayout fromPositions(int[] originSlotsBySlot) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int slot = 0; slot < originSlotsBySlot.length && slot < 36; slot++) {
            int origin = originSlotsBySlot[slot];
            if (origin >= 0 && origin < 36 && !map.containsKey(origin)) {
                map.put(origin, slot);
            }
        }
        return new KitLayout(new java.util.TreeMap<>(map));
    }

    /** @return whether the layout changes nothing */
    public boolean isIdentity() {
        return mapping.entrySet().stream().allMatch(e -> e.getKey().equals(e.getValue()));
    }
}
