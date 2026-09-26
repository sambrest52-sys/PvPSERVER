package net.pvpserver.core.kit;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the bundled kits.yml: structure, unique icons, known rule keys and the gameplay of every kit.
 * The live parse (enchantment and potion registries, item meta) is covered by the smoke suite.
 */
class KitsYmlTest {

    private static YamlConfiguration kits;
    private static YamlConfiguration kb;

    @BeforeAll
    static void load() {
        kits = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(KitsYmlTest.class.getClassLoader().getResourceAsStream("kits.yml")), StandardCharsets.UTF_8));
        kb = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(KitsYmlTest.class.getClassLoader().getResourceAsStream("kb.yml")), StandardCharsets.UTF_8));
    }

    private static ConfigurationSection kit(String id) {
        ConfigurationSection section = kits.getConfigurationSection("kits." + id);
        assertNotNull(section, "kit " + id);
        return section;
    }

    private static ConfigurationSection rules(String id) {
        ConfigurationSection rules = kit(id).getConfigurationSection("rules");
        assertNotNull(rules, id + " rules");
        return rules;
    }

    /** @return item maps by slot (explicit items only) */
    private static Map<Integer, Map<?, ?>> items(String id) {
        Map<Integer, Map<?, ?>> bySlot = new HashMap<>();
        for (Map<?, ?> item : kit(id).getMapList("items")) {
            int slot = ((Number) item.get("slot")).intValue();
            assertTrue(slot >= 0 && slot < 36, id + " slot " + slot);
            assertNull(bySlot.put(slot, item), id + " uses slot " + slot + " twice");
        }
        return bySlot;
    }

    private static List<Map<?, ?>> itemsOf(String id, String material) {
        return items(id).values().stream().filter(i -> material.equals(i.get("material"))).toList();
    }

    private static int count(String id, String material) {
        int total = 0;
        for (Map<?, ?> item : itemsOf(id, material)) {
            total += item.get("amount") instanceof Number n ? n.intValue() : 1;
        }
        return total;
    }

    private static List<String> enchants(Map<?, ?> item) {
        Object enchants = item.get("enchants");
        List<String> list = new ArrayList<>();
        if (enchants instanceof List<?> l) {
            l.forEach(e -> list.add(String.valueOf(e)));
        }
        return list;
    }

    private static List<String> armorEnchants(String id, String piece) {
        ConfigurationSection section = kit(id).getConfigurationSection("armor." + piece);
        assertNotNull(section, id + " " + piece);
        return section.getStringList("enchants");
    }

    private static String armorMaterial(String id, String piece) {
        return kit(id).getString("armor." + piece + ".material");
    }

    @Test
    void isVersionedAndHasTheFullKitRoster() {
        assertTrue(kits.getInt("config-version") >= 2);
        assertEquals(Set.of("nodebuff", "debuff", "gapple", "combo", "builduhc", "classic", "sumo", "boxing", "bridge",
                "soup", "archer", "spleef"), kits.getConfigurationSection("kits").getKeys(false));
    }

    @Test
    void everyKitHasDisplayInfoUniqueIconAndOrder() {
        Set<String> icons = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (String id : kits.getConfigurationSection("kits").getKeys(false)) {
            ConfigurationSection kit = kit(id);
            assertNotNull(kit.getString("display-name"), id);
            assertFalse(kit.getStringList("description").isEmpty(), id + " has a description");
            String icon = kit.getString("icon.material");
            assertNotNull(Material.matchMaterial(icon), id + " icon " + icon);
            assertTrue(icons.add(icon + "/" + kit.getString("icon.potion", "")), id + " icon is unique");
            assertTrue(orders.add(kit.getInt("order")), id + " order is unique");
            assertFalse(kit.getStringList("arena-tags").isEmpty(), id + " has arena tags");
        }
    }

    @Test
    void rulesAndMaterialsAreValid() {
        Set<String> armorPieces = Set.of("helmet", "chestplate", "leggings", "boots");
        for (String id : kits.getConfigurationSection("kits").getKeys(false)) {
            ConfigurationSection rules = kit(id).getConfigurationSection("rules");
            if (rules != null) {
                for (String key : rules.getKeys(false)) {
                    assertTrue(KitRules.KEYS.contains(key), id + ": unknown rule " + key);
                }
            }
            for (Map<?, ?> item : kit(id).getMapList("items")) {
                assertNotNull(Material.matchMaterial(String.valueOf(item.get("material"))), id + " item " + item.get("material"));
            }
            ConfigurationSection armor = kit(id).getConfigurationSection("armor");
            if (armor != null) {
                for (String piece : armor.getKeys(false)) {
                    assertTrue(armorPieces.contains(piece), id + " armor " + piece);
                    assertNotNull(Material.matchMaterial(armor.getString(piece + ".material")), id + " " + piece);
                }
            }
            String fill = kit(id).getString("fill.material");
            if (fill != null) {
                assertNotNull(Material.matchMaterial(fill), id + " fill");
            }
        }
    }

    @Test
    void noDebuff() {
        for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) {
            assertTrue(armorMaterial("nodebuff", piece).startsWith("DIAMOND_"));
            assertTrue(armorEnchants("nodebuff", piece).containsAll(List.of("protection:4", "unbreaking:3")), piece);
        }
        Map<?, ?> sword = items("nodebuff").get(0);
        assertEquals("DIAMOND_SWORD", sword.get("material"));
        assertEquals(List.of("sharpness:3", "unbreaking:3"), enchants(sword));
        assertEquals(16, count("nodebuff", "ENDER_PEARL"));
        // Every other hotbar slot is left for the fill: splash Instant Health II.
        for (int slot = 2; slot <= 8; slot++) {
            assertNull(items("nodebuff").get(slot), "hotbar slot " + slot + " is a health potion");
        }
        assertEquals("SPLASH_POTION", kit("nodebuff").getString("fill.material"));
        assertEquals("strong_healing", kit("nodebuff").getString("fill.potion"));
        List<Map<?, ?>> speed = itemsOf("nodebuff", "POTION");
        assertEquals(3, speed.size());
        speed.forEach(p -> assertEquals(List.of("speed:1:480"), p.get("effects"), "Speed II for 8 minutes"));
        assertTrue(count("nodebuff", "COOKED_BEEF") > 0, "some food");
        assertFalse(rules("nodebuff").getBoolean("hunger"));
        assertEquals("NONE", rules("nodebuff").getString("regen"));
        assertEquals(30, healthPotions("nodebuff"));
    }

    private static int healthPotions(String id) {
        return 36 - items(id).size();
    }

    @Test
    void debuffAddsSlownessAndPoisonWithFewerHealthPotions() {
        List<String> debuffs = itemsOf("debuff", "SPLASH_POTION").stream().map(i -> String.valueOf(i.get("potion"))).toList();
        assertTrue(debuffs.contains("slowness") && debuffs.contains("poison"), debuffs.toString());
        assertTrue(healthPotions("debuff") < healthPotions("nodebuff"));
        assertEquals("strong_healing", kit("debuff").getString("fill.potion"));
        assertEquals(armorEnchants("nodebuff", "chestplate"), armorEnchants("debuff", "chestplate"));
        assertFalse(rules("debuff").getBoolean("hunger"));
        assertEquals("NONE", rules("debuff").getString("regen"));
    }

    @Test
    void gapple() {
        for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) {
            assertTrue(armorMaterial("gapple", piece).startsWith("DIAMOND_"));
        }
        assertEquals("DIAMOND_SWORD", items("gapple").get(0).get("material"));
        assertTrue(count("gapple", "ENCHANTED_GOLDEN_APPLE") >= 32);
        assertEquals("NONE", rules("gapple").getString("regen"));
    }

    @Test
    void comboHasNoHitDelayAndJugglingKnockback() {
        for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) {
            assertNotNull(armorMaterial("combo", piece));
        }
        assertTrue(count("combo", "ENCHANTED_GOLDEN_APPLE") > 0);
        assertEquals(0, rules("combo").getInt("hit-delay"));
        assertEquals("combo", kit("combo").getString("knockback"));
        double comboVertical = kb.getDouble("profiles.combo.vertical");
        assertTrue(comboVertical > kb.getDouble("profiles.default.vertical"), "higher vertical knockback than default");
        assertTrue(kb.getDouble("profiles.combo.horizontal") < kb.getDouble("profiles.default.horizontal"));
        assertTrue(kb.getDouble("profiles.combo.vertical-limit") >= comboVertical);
    }

    @Test
    void sumoAndBoxingHaveEmptyInventories() {
        for (String id : List.of("sumo", "boxing")) {
            assertTrue(kit(id).getMapList("items").isEmpty(), id + " has no items");
            assertNull(kit(id).getConfigurationSection("armor"), id + " has no armour");
            assertTrue(rules(id).getBoolean("no-damage"));
            assertFalse(rules(id).getBoolean("hunger"));
            assertFalse(rules(id).getBoolean("health-display"), id + " hides health");
        }
        assertTrue(rules("sumo").getBoolean("sumo"));
        assertEquals(List.of("sumo"), kit("sumo").getStringList("arena-tags"));
        assertTrue(rules("boxing").getBoolean("boxing"));
        assertEquals(100, rules("boxing").getInt("boxing-hits"));
        assertEquals(List.of("boxing"), kit("boxing").getStringList("arena-tags"));
    }

    @Test
    void buildUhc() {
        Set<String> armor = new HashSet<>();
        for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) {
            armor.add(armorMaterial("builduhc", piece).split("_")[0]);
        }
        assertEquals(Set.of("IRON", "DIAMOND"), armor, "iron/diamond mix");
        assertTrue(count("builduhc", "BOW") == 1 && count("builduhc", "ARROW") >= 32);
        assertTrue(count("builduhc", "WATER_BUCKET") >= 1 && count("builduhc", "LAVA_BUCKET") >= 1);
        assertTrue(count("builduhc", "COBBLESTONE") >= 64);
        assertTrue(count("builduhc", "GOLDEN_APPLE") >= 6);
        assertTrue(rules("builduhc").getBoolean("build"));
        assertTrue(rules("builduhc").getBoolean("break-placed-only"), "only placed blocks break; the map resets");
        assertEquals(List.of("build"), kit("builduhc").getStringList("arena-tags"));
    }

    @Test
    void classic() {
        for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) {
            assertTrue(armorMaterial("classic", piece).startsWith("IRON_"));
        }
        assertEquals("IRON_SWORD", items("classic").get(0).get("material"));
        assertEquals(1, count("classic", "BOW"));
        assertEquals(1, count("classic", "FISHING_ROD"));
        assertTrue(rules("classic").getBoolean("old-combat"));
        assertEquals("LEGACY", rules("classic").getString("regen"));
    }

    @Test
    void bridge() {
        ConfigurationSection rules = rules("bridge");
        assertTrue(rules.getBoolean("bridge") && rules.getBoolean("build") && rules.getBoolean("break-placed-only"));
        assertEquals(5, rules.getInt("bridge-goals"));
        assertTrue(rules.getDouble("arrow-regen") > 0);
        assertFalse(rules.getBoolean("fall-damage"));
        assertEquals(128, count("bridge", "WHITE_TERRACOTTA"), "team-coloured blocks");
        assertEquals(List.of("bridge"), kit("bridge").getStringList("arena-tags"));
    }

    @Test
    void extraKits() {
        assertTrue(rules("soup").getBoolean("soup"));
        assertEquals("MUSHROOM_STEW", kit("soup").getString("fill.material"));
        assertEquals("NONE", rules("soup").getString("regen"));
        assertTrue(enchants(items("archer").get(0)).contains("infinity:1"));
        assertEquals("BOW", items("archer").get(0).get("material"));
        assertEquals(List.of("SNOW_BLOCK"), rules("spleef").getStringList("breakable"));
        assertTrue(rules("spleef").getBoolean("no-damage"));
        assertEquals(List.of("spleef"), kit("spleef").getStringList("arena-tags"));
    }
}
