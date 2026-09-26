package net.pvpserver.core.arena.io;

import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Converts {@code legacy:<id>:<data>} palette entries (from pre-1.13 schematics) to modern block data with Paper's
 * own legacy tables. Main thread only. Paper logs a one-time "Initializing Legacy Material Support" message.
 */
public final class LegacyBlocks {

    private static Map<Integer, Material> byId;

    private LegacyBlocks() {
    }

    /**
     * @return a caching resolver for {@link net.pvpserver.core.arena.TemplateBuilder#remapPalette}
     */
    public static UnaryOperator<String> resolver() {
        Map<String, String> cache = new HashMap<>();
        return entry -> cache.computeIfAbsent(entry, LegacyBlocks::resolve);
    }

    /**
     * @param entry palette entry; entries not starting with {@code legacy:} are returned unchanged
     * @return modern block data string ({@code minecraft:air} for unknown ids)
     */
    @SuppressWarnings("deprecation")
    public static String resolve(String entry) {
        if (!entry.startsWith("legacy:")) {
            return entry;
        }
        String[] parts = entry.split(":");
        try {
            int id = Integer.parseInt(parts[1]);
            int data = Integer.parseInt(parts[2]);
            if (byId == null) {
                Map<Integer, Material> map = new HashMap<>();
                for (Material material : Material.values()) {
                    if (material.isLegacy()) {
                        map.putIfAbsent(material.getId(), material);
                    }
                }
                byId = map;
            }
            Material legacy = byId.get(id);
            return legacy == null ? "minecraft:air" : Bukkit.getUnsafe().fromLegacy(legacy, (byte) data).getAsString();
        } catch (RuntimeException e) {
            return "minecraft:air";
        }
    }
}
