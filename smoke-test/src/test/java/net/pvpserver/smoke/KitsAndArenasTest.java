package net.pvpserver.smoke;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boot check for the content: every kit and every arena loads without a warning, every kit hands out the right
 * items, every kit plays on an arena made for it, arena selection varies, and every arena pastes with standable
 * spawns.
 */
class KitsAndArenasTest extends SmokeTestBase {

    private static final List<String> KITS = List.of("nodebuff", "debuff", "gapple", "combo", "builduhc", "classic", "sumo",
            "boxing", "bridge", "soup", "archer", "spleef");

    private final List<LogRecord> log = new CopyOnWriteArrayList<>();
    private Handler handler;

    @Override
    protected void beforeLoad(File pluginsFolder) {
        handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                log.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger.getLogger("").addHandler(handler);
    }

    @Override
    protected void afterShutdown() {
        Logger.getLogger("").removeHandler(handler);
    }

    private List<String> messages(Level atLeast) {
        List<String> out = new ArrayList<>();
        for (LogRecord record : log) {
            if (record.getLevel().intValue() >= atLeast.intValue()) {
                out.add(record.getLevel() + " " + record.getMessage());
            }
        }
        return out;
    }

    private boolean logged(String text) {
        return log.stream().anyMatch(r -> r.getMessage() != null && r.getMessage().contains(text));
    }

    @Test
    void bootLoadsEveryKitAndArenaWithoutWarnings() throws Exception {
        await("FFA arenas pasted", () -> logged("FFA ready with"), 15000);
        await("arena pool pre-warmed", () -> logged("Loaded 21 arena templates"), 5000);
        waitFor(() -> false, 1500);
        assertTrue(logged("Loaded 12 kits"), "all kits loaded");
        assertTrue(logged("Generated 21 built-in arenas"), "all built-in arenas generated");
        assertTrue(logged("Loaded 21 arena definitions"), "all arena definitions loaded");
        assertTrue(logged("FFA ready with 4 arenas"), "every FFA arena found its template");
        assertEquals(List.of(), messages(Level.WARNING), "no warnings or errors while booting");
    }

    // ------------------------------------------------------------------ kit contents

    private PlayerInventory giveKit(PlayerMock admin, String kit) throws InterruptedException {
        assertTrue(run(admin, "pvpadmin kit " + kit));
        ticks(1);
        return admin.getInventory();
    }

    private static int level(ItemStack item, Enchantment enchantment) {
        return item == null ? 0 : item.getEnchantmentLevel(enchantment);
    }

    private static int count(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private static int countPotions(PlayerInventory inventory, Material material, PotionType type) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.getType() == material && item.getItemMeta() instanceof PotionMeta meta
                    && meta.getBasePotionType() == type) {
                total++;
            }
        }
        return total;
    }

    private static void assertArmor(PlayerInventory inventory, String prefix, int protection, int unbreaking) {
        for (ItemStack piece : inventory.getArmorContents()) {
            assertNotNull(piece, "armour piece present");
            assertTrue(piece.getType().name().startsWith(prefix), piece.getType() + " is " + prefix);
            assertEquals(protection, level(piece, Enchantment.PROTECTION), piece.getType() + " protection");
            assertEquals(unbreaking, level(piece, Enchantment.UNBREAKING), piece.getType() + " unbreaking");
        }
    }

    @Test
    void everyKitHandsOutTheRightItems() throws Exception {
        PlayerMock admin = join("KitAdmin");
        admin.setOp(true);

        PlayerInventory inv = giveKit(admin, "nodebuff");
        assertArmor(inv, "DIAMOND_", 4, 3);
        assertEquals(4, level(inv.getBoots(), Enchantment.FEATHER_FALLING));
        assertEquals(Material.DIAMOND_SWORD, inv.getItem(0).getType());
        assertEquals(3, level(inv.getItem(0), Enchantment.SHARPNESS));
        assertEquals(3, level(inv.getItem(0), Enchantment.UNBREAKING));
        assertEquals(0, level(inv.getItem(0), Enchantment.FIRE_ASPECT));
        assertEquals(16, count(inv, Material.ENDER_PEARL));
        for (int slot = 2; slot <= 8; slot++) {
            assertEquals(PotionType.STRONG_HEALING, ((PotionMeta) inv.getItem(slot).getItemMeta()).getBasePotionType(), "hotbar " + slot);
        }
        assertEquals(30, countPotions(inv, Material.SPLASH_POTION, PotionType.STRONG_HEALING), "health potions");
        List<PotionEffect> speed = ((PotionMeta) inv.getItem(17).getItemMeta()).getCustomEffects();
        assertEquals(1, speed.size());
        assertEquals(PotionEffectType.SPEED, speed.get(0).getType());
        assertEquals(1, speed.get(0).getAmplifier(), "Speed II");
        assertEquals(480 * 20, speed.get(0).getDuration(), "8 minutes");
        assertTrue(count(inv, Material.COOKED_BEEF) > 0);

        inv = giveKit(admin, "debuff");
        assertEquals(2, countPotions(inv, Material.SPLASH_POTION, PotionType.SLOWNESS));
        assertEquals(2, countPotions(inv, Material.SPLASH_POTION, PotionType.POISON));
        assertTrue(countPotions(inv, Material.SPLASH_POTION, PotionType.STRONG_HEALING) < 30);

        inv = giveKit(admin, "gapple");
        assertArmor(inv, "DIAMOND_", 4, 3);
        assertEquals(64, count(inv, Material.ENCHANTED_GOLDEN_APPLE));
        assertEquals(5, level(inv.getItem(0), Enchantment.SHARPNESS));

        inv = giveKit(admin, "combo");
        for (ItemStack piece : inv.getArmorContents()) {
            assertTrue(piece.getItemMeta().isUnbreakable(), "combo armour is unbreakable");
        }
        assertTrue(count(inv, Material.ENCHANTED_GOLDEN_APPLE) > 0);

        inv = giveKit(admin, "builduhc");
        Set<String> tiers = new HashSet<>();
        for (ItemStack piece : inv.getArmorContents()) {
            tiers.add(piece.getType().name().split("_")[0]);
        }
        assertEquals(Set.of("DIAMOND", "IRON"), tiers);
        assertEquals(3, level(inv.getItem(2), Enchantment.POWER));
        assertEquals(2, count(inv, Material.WATER_BUCKET));
        assertEquals(2, count(inv, Material.LAVA_BUCKET));
        assertEquals(128, count(inv, Material.COBBLESTONE));
        assertEquals(9, count(inv, Material.GOLDEN_APPLE), "6 golden apples and 3 golden heads");
        assertEquals(64, count(inv, Material.ARROW));

        inv = giveKit(admin, "classic");
        assertArmor(inv, "IRON_", 0, 0);
        assertEquals(Material.IRON_SWORD, inv.getItem(0).getType());
        assertEquals(1, count(inv, Material.BOW));
        assertEquals(1, count(inv, Material.FISHING_ROD));

        inv = giveKit(admin, "sumo");
        assertTrue(inv.isEmpty());
        assertNull(inv.getChestplate());
        inv = giveKit(admin, "boxing");
        assertTrue(inv.isEmpty(), "boxing: fists only");
        assertNull(inv.getChestplate(), "boxing: no armour");
        assertTrue(admin.hasPotionEffect(PotionEffectType.SPEED));

        inv = giveKit(admin, "bridge");
        assertEquals(128, count(inv, Material.WHITE_TERRACOTTA));
        assertEquals(1, count(inv, Material.ARROW));
        assertEquals(Material.LEATHER_CHESTPLATE, inv.getChestplate().getType());

        inv = giveKit(admin, "soup");
        assertEquals(35, count(inv, Material.MUSHROOM_STEW));
        inv = giveKit(admin, "archer");
        assertEquals(1, level(inv.getItem(0), Enchantment.INFINITY));
        assertEquals(Material.BOW, inv.getItem(0).getType());
        inv = giveKit(admin, "spleef");
        assertEquals(Material.DIAMOND_SHOVEL, inv.getItem(0).getType());
        assertEquals(5, level(inv.getItem(0), Enchantment.EFFICIENCY));
    }

    private static List<String> loreOf(PlayerMock player, Material material) {
        var top = player.getOpenInventory().getTopInventory();
        for (ItemStack item : top.getContents()) {
            if (item != null && item.getType() == material && item.getItemMeta().lore() != null) {
                return item.getItemMeta().lore().stream()
                        .map(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line)).toList();
            }
        }
        return List.of();
    }

    @Test
    void menusShowKitDescriptions() throws Exception {
        PlayerMock player = join("MenuReader");
        assertTrue(run(player, "queue unranked"));
        ticks(2);
        assertTrue(loreOf(player, Material.MUSHROOM_STEW).contains("Right-click mushroom stew to heal"), "queue menu: soup description");
        assertTrue(loreOf(player, Material.LEATHER).stream().anyMatch(l -> l.contains("100 hits")), "queue menu: boxing description");
        player.closeInventory();
        assertTrue(run(player, "kiteditor"));
        ticks(2);
        List<String> nodebuff = loreOf(player, Material.SPLASH_POTION);
        assertTrue(nodebuff.contains("The classic potion fight."), "kit editor: nodebuff description " + nodebuff);
        assertTrue(loreOf(player, Material.DIAMOND_SHOVEL).isEmpty(), "spleef is not editable, so it is not listed");
    }

    // ------------------------------------------------------------------ kits on arenas

    /** Arena display names (tags stripped) each kit may be played on, read from the live config files. */
    private Set<String> allowedArenas(String kit) {
        YamlConfiguration kits = YamlConfiguration.loadConfiguration(new File(core.getDataFolder(), "kits.yml"));
        YamlConfiguration arenas = YamlConfiguration.loadConfiguration(new File(core.getDataFolder(), "arenas.yml"));
        List<String> tags = kits.getStringList("kits." + kit + ".arena-tags");
        List<String> blacklist = kits.getStringList("kits." + kit + ".arena-blacklist");
        Set<String> names = new HashSet<>();
        ConfigurationSection root = arenas.getConfigurationSection("arenas");
        for (String name : root.getKeys(false)) {
            if (!blacklist.contains(name) && root.getStringList(name + ".tags").stream().anyMatch(tags::contains)) {
                names.add(root.getString(name + ".display-name").replaceAll("<[^>]+>", ""));
            }
        }
        return names;
    }

    private String playOneMatch(PlayerMock a, PlayerMock b, String kit) throws InterruptedException {
        drain(a);
        assertTrue(run(a, "queue join " + kit + " unranked"));
        assertTrue(run(b, "queue join " + kit + " unranked"));
        awaitArena(a, b);
        ticks(2);
        String arena = null;
        for (String line : drain(a)) {
            if (line.contains("Arena: ")) {
                arena = line.substring(line.indexOf("Arena: ") + 7, line.indexOf('|', line.indexOf("Arena: "))).trim();
            }
        }
        assertNotNull(arena, kit + " match announced its arena");
        assertTrue(run(a, "leave"));
        awaitLobby(a, b);
        return arena;
    }

    @Test
    void everyKitPlaysOnAnArenaMadeForItAndArenasRotate() throws Exception {
        PlayerMock a = join("Rotation1");
        PlayerMock b = join("Rotation2");
        for (String kit : KITS) {
            String arena = playOneMatch(a, b, kit);
            Set<String> allowed = allowedArenas(kit);
            assertTrue(allowed.size() >= 3, kit + " has " + allowed);
            assertTrue(allowed.contains(arena), kit + " was played on " + arena + ", allowed: " + allowed);
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            seen.add(playOneMatch(a, b, "nodebuff"));
        }
        assertTrue(seen.size() >= 2, "random arena selection varies (saw " + seen + ")");
    }

    // ------------------------------------------------------------------ every arena pastes

    @Test
    void everyArenaPastesWithStandableSpawns() throws Exception {
        PlayerMock admin = join("Surveyor");
        admin.setOp(true);
        YamlConfiguration arenas = YamlConfiguration.loadConfiguration(new File(core.getDataFolder(), "arenas.yml"));
        List<String> names = new ArrayList<>(arenas.getConfigurationSection("arenas").getKeys(false));
        assertEquals(21, names.size());
        for (String name : names) {
            org.bukkit.Location before = admin.getLocation().clone();
            assertTrue(run(admin, "arena edit " + name));
            await(name + " pasted into the editor", () -> "pvp_editor".equals(admin.getWorld().getName())
                    && !admin.getLocation().equals(before), 10000);
            var feet = admin.getLocation().getBlock();
            Material ground = feet.getRelative(BlockFace.DOWN).getType();
            assertTrue(ground.isSolid(), name + ": spawn A stands on " + ground);
            assertFalse(feet.getType().isSolid(), name + ": feet in " + feet.getType());
            assertFalse(feet.getRelative(BlockFace.UP).getType().isSolid(), name + ": head space");
            assertTrue(run(admin, "arena cancel"));
        }
        assertEquals(List.of(), messages(Level.WARNING), "no warnings while pasting every arena");
    }
}
