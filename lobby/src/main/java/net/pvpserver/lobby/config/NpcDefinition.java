package net.pvpserver.lobby.config;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One NPC from npcs.yml.
 *
 * @param id id (matches layout.yml npcs.&lt;id&gt;)
 * @param action click action
 * @param hologram hologram lines (MiniMessage with live placeholders)
 * @param skin skin spec: "", "name:&lt;player&gt;" or "texture:&lt;value&gt;[;&lt;signature&gt;]"
 * @param glowing outline glow
 * @param equipment worn and held items
 */
public record NpcDefinition(String id, String action, List<String> hologram, String skin, boolean glowing,
                            Map<EquipmentSlot, Item> equipment) {

    /**
     * An equipment piece.
     *
     * @param material material
     * @param color leather dye colour or null
     */
    public record Item(Material material, Color color) {
    }

    private static final Map<String, EquipmentSlot> SLOTS = Map.of("helmet", EquipmentSlot.HEAD, "chestplate", EquipmentSlot.CHEST,
            "leggings", EquipmentSlot.LEGS, "boots", EquipmentSlot.FEET, "main-hand", EquipmentSlot.HAND, "off-hand", EquipmentSlot.OFF_HAND);

    /**
     * @param root npcs.yml root
     * @param warnings receives a line per invalid value
     * @return definitions by id, in file order
     */
    public static Map<String, NpcDefinition> read(ConfigurationSection root, List<String> warnings) {
        Map<String, NpcDefinition> definitions = new LinkedHashMap<>();
        ConfigurationSection npcs = root.getConfigurationSection("npcs");
        if (npcs == null) {
            return definitions;
        }
        for (String rawId : npcs.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            ConfigurationSection section = npcs.getConfigurationSection(rawId);
            if (section == null) {
                warnings.add("npcs." + rawId + ": expected a section");
                continue;
            }
            String action = section.getString("action", "").trim();
            if (action.isEmpty()) {
                warnings.add("npcs." + rawId + ".action: missing, NPC skipped");
                continue;
            }
            String skin = section.getString("skin", "").trim();
            if (!skin.isEmpty() && !skin.startsWith("name:") && !skin.startsWith("texture:")) {
                warnings.add("npcs." + rawId + ".skin: use \"name:<player>\" or \"texture:<value>\", using the default skin");
                skin = "";
            }
            Map<EquipmentSlot, Item> equipment = new EnumMap<>(EquipmentSlot.class);
            ConfigurationSection gear = section.getConfigurationSection("equipment");
            if (gear != null) {
                for (String key : gear.getKeys(false)) {
                    EquipmentSlot slot = SLOTS.get(key.toLowerCase(Locale.ROOT));
                    if (slot == null) {
                        warnings.add("npcs." + rawId + ".equipment." + key + ": unknown slot (helmet, chestplate, leggings, boots, main-hand, off-hand)");
                        continue;
                    }
                    Item item = item(gear.getString(key, ""));
                    if (item == null) {
                        warnings.add("npcs." + rawId + ".equipment." + key + ": unknown item " + gear.getString(key));
                    } else {
                        equipment.put(slot, item);
                    }
                }
            }
            List<String> hologram = new ArrayList<>(section.getStringList("hologram"));
            definitions.put(id, new NpcDefinition(id, action, List.copyOf(hologram), skin, section.getBoolean("glowing", false), equipment));
        }
        return definitions;
    }

    /**
     * @param spec {@code MATERIAL} or {@code LEATHER_PIECE #RRGGBB}
     * @return item or null when unknown
     */
    static Item item(String spec) {
        String[] parts = spec.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return null;
        }
        Material material = Material.matchMaterial(parts[0]);
        // Only the name is checked here (isItem() needs a running server); blocks without an item form are skipped
        // when the NPC spawns.
        if (material == null || material.isLegacy() || material.name().endsWith("AIR")) {
            return null;
        }
        Color color = null;
        if (parts.length > 1) {
            try {
                color = Color.fromRGB(Integer.parseInt(parts[1].replace("#", ""), 16) & 0xFFFFFF);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new Item(material, color);
    }
}
