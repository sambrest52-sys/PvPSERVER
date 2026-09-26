package net.pvpserver.smoke;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated lobby hub end to end: joining, every NPC, portals, launch pads, the void, parkour, eggs, clickable
 * blocks, the leaderboard wall, live holograms, protection, and the admin tools (reload, set, regenerate, import).
 */
class LobbyScenariosTest extends SmokeTestBase {

    private final List<String> lobbyWarnings = new ArrayList<>();

    @Override
    protected void beforeLoad(File pluginsFolder) throws IOException {
        // Faster timers so rotations and refreshes happen within a test.
        File config = new File(pluginsFolder, "PvPLobby/config.yml");
        config.getParentFile().mkdirs();
        Files.writeString(config.toPath(), """
                leaderboard-wall:
                  rotate-seconds: 2
                npcs:
                  update-interval-ticks: 10
                bossbar:
                  seconds-per-tip: 2
                """);
        server.getLogger().addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                String message = String.valueOf(record.getMessage());
                if (record.getLevel().intValue() >= Level.WARNING.intValue() && message.contains("PvPLobby")) {
                    lobbyWarnings.add(message);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private YamlConfiguration layout() {
        return YamlConfiguration.loadConfiguration(new File(lobby.getDataFolder(), "layout.yml"));
    }

    private World lobbyWorld() {
        return server.getWorld(LOBBY);
    }

    private static double[] numbers(String text) {
        String[] parts = text.trim().split("\\s+");
        double[] values = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Double.parseDouble(parts[i]);
        }
        return values;
    }

    private Location point(String path) {
        String text = layout().getString(path);
        assertNotNull(text, "layout.yml has " + path);
        double[] v = numbers(text);
        Location location = new Location(lobbyWorld(), v[0], v[1], v[2]);
        if (v.length > 3) {
            location.setYaw((float) v[3]);
        }
        return location;
    }

    /** Centre of a block written as "x y z", at the block's bottom (where feet go). */
    private Location feet(String blockText) {
        double[] v = numbers(blockText);
        return new Location(lobbyWorld(), v[0] + 0.5, v[1], v[2] + 0.5);
    }

    private Mannequin npc(String id) {
        Location at = point("npcs." + id);
        return lobbyWorld().getEntitiesByClass(Mannequin.class).stream().filter(Mannequin::isValid)
                .min(Comparator.comparingDouble(m -> m.getLocation().distanceSquared(at)))
                .filter(m -> m.getLocation().distanceSquared(at) < 1).orElseThrow(() -> new AssertionError("no NPC " + id));
    }

    private TextDisplay displayNear(Location at) {
        return lobbyWorld().getEntitiesByClass(TextDisplay.class).stream().filter(TextDisplay::isValid)
                .min(Comparator.comparingDouble(d -> d.getLocation().distanceSquared(at)))
                .filter(d -> d.getLocation().distanceSquared(at) < 0.25).orElseThrow(() -> new AssertionError("no text display at " + at));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static String menu(PlayerMock player) {
        return PracticeServerMock.title(player.getOpenInventory().getTopInventory());
    }

    /** Waits for a chat message containing {@code text}; the failure lists everything the player received. */
    private void awaitMessage(PlayerMock player, String text, long millis) throws InterruptedException {
        List<String> received = new ArrayList<>();
        waitFor(() -> {
            received.addAll(drain(player));
            return received.stream().anyMatch(m -> m.contains(text));
        }, millis);
        assertTrue(received.stream().anyMatch(m -> m.contains(text)), "no \"" + text + "\" in " + received);
    }

    private void click(PlayerMock player, Mannequin npc) throws InterruptedException {
        player.closeInventory();
        server.getPluginManager().callEvent(new PlayerInteractEntityEvent(player, npc, EquipmentSlot.HAND));
        ticks(2);
        Thread.sleep(450); // NPC click cooldown
    }

    private PlayerInteractEvent rightClick(PlayerMock player, Block block) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, player.getInventory().getItemInMainHand(),
                block, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private Block block(String text) {
        double[] v = numbers(text);
        return lobbyWorld().getBlockAt((int) v[0], (int) v[1], (int) v[2]);
    }

    // ------------------------------------------------------------------ scenarios

    @Test
    void joiningLandsOnTheGeneratedHub() throws Exception {
        PracticePlayerMock player = (PracticePlayerMock) join("Newcomer");
        World world = lobbyWorld();
        assertNotNull(world, "the lobby world exists");
        assertEquals(LOBBY, player.getWorld().getName());
        Location spawn = point("spawn");
        assertEquals(spawn.getX(), player.getLocation().getX(), 1e-6);
        assertEquals(spawn.getZ(), player.getLocation().getZ(), 1e-6);
        assertEquals(Material.IRON_BLOCK, player.getLocation().getBlock().getRelative(BlockFace.DOWN).getType(),
                "spawn stands on the crossed-swords medallion");
        assertEquals(Material.WATER, world.getBlockAt(3, 64, 0).getType(), "the fountain holds water");
        assertTrue(new File(new File(server.getWorldContainer(), LOBBY), "pvplobby.yml").exists(), "world marker written");

        // World rules: fixed noon, no weather or mobs, peaceful, a border around the hub.
        assertEquals(Boolean.FALSE, world.getGameRuleValue(GameRules.ADVANCE_TIME), "time stands still");
        assertFalse(world.hasStorm());
        assertEquals(Boolean.FALSE, world.getGameRuleValue(GameRules.SPAWN_MOBS));
        assertEquals(Difficulty.PEACEFUL, world.getDifficulty());
        assertEquals(200, world.getWorldBorder().getSize(), 1e-6);

        // Welcome title and chat, tips bar after a second, NPCs and holograms in place.
        assertTrue(player.lastTitle().contains("PRACTICE") && player.lastTitle().contains("Newcomer"), player.lastTitle());
        assertTrue(drain(player).stream().anyMatch(m -> m.contains("Welcome to Practice")), "chat welcome");
        waitFor(() -> !player.bossBars().isEmpty(), 1500);
        assertEquals(1, player.bossBars().size(), "tips boss bar shown in the lobby");
        String firstTip = plain(player.bossBars().iterator().next().name());
        assertTrue(firstTip.contains("TIP"), firstTip);
        waitFor(() -> !plain(player.bossBars().iterator().next().name()).equals(firstTip), 3500);
        assertNotEquals(firstTip, plain(player.bossBars().iterator().next().name()), "tips rotate");

        assertEquals(9, world.getEntitiesByClass(Mannequin.class).size(), "nine NPCs");
        long texts = world.getEntitiesByClass(TextDisplay.class).size();
        assertTrue(texts >= 9 + 10 + 13, "NPC plates, holograms and wall panels: " + texts);
        assertTrue(world.getEntities().stream().filter(e -> e.getType() != EntityType.PLAYER).count() <= 120, "entity budget");
        assertTrue(lobbyWarnings.isEmpty(), "no lobby warnings while booting: " + lobbyWarnings);

        // Hotbar and scoreboard still work in the new lobby.
        assertEquals(Material.IRON_SWORD, player.getInventory().getItem(0).getType());
        player.setOp(true);
        assertTrue(run(player, "lobby info"));
        assertTrue(drain(player).stream().anyMatch(m -> m.contains("generated hub (seed 1337)")), "/lobby info");
    }

    @Test
    void everyNpcRunsItsAction() throws Exception {
        PlayerMock player = join("NpcFan");
        Map<String, String> menus = new LinkedHashMap<>();
        menus.put("ranked", "Ranked Queue");
        menus.put("unranked", "Unranked Queue");
        menus.put("ffa", "Free For All");
        menus.put("kit-editor", "Kit Editor");
        menus.put("stats", "Stats");
        menus.put("leaderboards", "Leaderboards");
        menus.put("cosmetics", "Cosmetics");
        for (Map.Entry<String, String> entry : menus.entrySet()) {
            click(player, npc(entry.getKey()));
            assertTrue(menu(player).contains(entry.getValue()), entry.getKey() + " NPC opens " + entry.getValue() + ", got " + menu(player));
        }
        player.closeInventory();
        drain(player);
        click(player, npc("info"));
        assertTrue(drain(player).stream().anyMatch(m -> m.contains("WELCOME TO PRACTICE")), "info NPC explains the server");
        click(player, npc("party"));
        assertEquals(Material.GOLDEN_SWORD, player.getInventory().getItem(0).getType(), "party NPC created a party (leader hotbar)");

        // Left clicks work too, and NPCs cannot be hurt.
        player.closeInventory();
        PrePlayerAttackEntityEvent attack = new PrePlayerAttackEntityEvent(player, npc("stats"), true);
        server.getPluginManager().callEvent(attack);
        ticks(2);
        assertTrue(attack.isCancelled(), "hitting an NPC is cancelled");
        assertTrue(menu(player).contains("Stats"), "and runs its action");
        EntityDamageEvent damage = new EntityDamageEvent(npc("ranked"), EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).build(), 5);
        server.getPluginManager().callEvent(damage);
        assertTrue(damage.isCancelled(), "NPCs are invulnerable");
    }

    @Test
    void hologramsShowLiveQueueCounts() throws Exception {
        PlayerMock watcher = join("Watcher");
        PlayerMock queued = join("Queued");
        Location plate = npc("ranked").getLocation().add(0, 2.05, 0);
        waitFor(() -> false, 700);
        assertTrue(plain(displayNear(plate).text()).contains("0 queued"), plain(displayNear(plate).text()));
        assertTrue(run(queued, "queue join nodebuff ranked"));
        await("ranked NPC counts the queued player", () -> plain(displayNear(plate).text()).contains("1 queued"), 3000);
        Location portal = point("holograms.ranked-portal");
        await("ranked portal hologram counts too", () -> plain(displayNear(portal).text()).contains("1 in queue"), 3000);
        assertTrue(run(queued, "queue leave"));
        await("count drops again", () -> plain(displayNear(plate).text()).contains("0 queued"), 3000);
        assertEquals(LOBBY, watcher.getWorld().getName());
    }

    @Test
    void portalsOpenQueuesAndJoinFfa() throws Exception {
        PlayerMock player = join("Walker");
        YamlConfiguration layout = layout();
        double[] from = numbers(layout.getString("portals.ranked.from"));
        Location inFront = new Location(lobbyWorld(), from[0] + 1.5, from[1], from[2] + 3.5);
        player.teleport(inFront);
        move(player, new Location(lobbyWorld(), from[0] + 1.5, from[1], from[2] + 0.5));
        ticks(2);
        assertTrue(menu(player).contains("Ranked Queue"), "walking into the ranked portal opens the ranked queue: " + menu(player));
        Vector push = player.getVelocity();
        assertTrue(push.getZ() > 0.1, "pushed back out of the portal: " + push);
        player.closeInventory();

        double[] ffa = numbers(layout.getString("portals.ffa.from"));
        Location ffaFront = new Location(lobbyWorld(), ffa[0] + 1.5, ffa[1], ffa[2] + 3.5);
        await("the FFA gate drops players straight into FFA", () -> {
            if (!in("pvp_ffa", player)) {
                player.teleport(ffaFront);
                move(player, new Location(lobbyWorld(), ffa[0] + 1.5, ffa[1], ffa[2] + 0.5));
                for (int i = 0; i < 45; i++) {
                    server.getScheduler().performOneTick();
                }
                try {
                    Thread.sleep(2100); // portal cooldown
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return in("pvp_ffa", player);
        }, 15000);
    }

    @Test
    void launchPadsLaunchAndTheVoidRescues() throws Exception {
        PlayerMock player = join("Jumper");
        List<Map<?, ?>> pads = layout().getMapList("launch-pads");
        assertTrue(pads.size() >= 7, "pads in layout.yml");
        for (Map<?, ?> pad : pads) {
            double[] velocity = numbers(String.valueOf(pad.get("velocity")));
            Location plate = feet(String.valueOf(pad.get("at")));
            player.teleport(plate.clone().add(2, 0, 0));
            player.setVelocity(new Vector());
            move(player, plate);
            assertEquals(velocity[1], player.getVelocity().getY(), 1e-6, "pad " + pad.get("at") + " launches upwards");
            assertEquals(velocity[0], player.getVelocity().getX(), 1e-6);
            assertEquals(velocity[2], player.getVelocity().getZ(), 1e-6);
            Thread.sleep(800); // pad cooldown
        }
        Location spawn = point("spawn");
        int voidY = layout().getInt("void-y");
        player.teleport(spawn.clone().add(40, 0, 40));
        move(player, new Location(lobbyWorld(), 50, voidY - 3, 50));
        assertEquals(spawn.getX(), player.getLocation().getX(), 1e-6, "falling into the void returns to spawn");
        assertEquals(spawn.getY(), player.getLocation().getY(), 1e-6);
    }

    @Test
    void parkourRunWithCheckpointsFallsAndBestTimes() throws Exception {
        PlayerMock player = join("Runner");
        YamlConfiguration layout = layout();
        Location start = feet(layout.getString("parkour.start"));
        List<String> checkpoints = layout.getStringList("parkour.checkpoints");
        Location finish = feet(layout.getString("parkour.finish"));
        int fallY = layout.getInt("parkour.fall-y");
        assertEquals(3, checkpoints.size());

        player.teleport(start.clone().add(2, 0, 0));
        drain(player);
        move(player, start);
        assertTrue(drain(player).stream().anyMatch(m -> m.contains("Parkour started")), "start plate starts the run");
        assertEquals(Material.RED_BED, player.getInventory().getItem(5).getType(), "parkour hotbar");
        assertFalse(player.getAllowFlight(), "no double jump during a run");

        // Skipping a checkpoint does nothing; falling returns to the start.
        move(player, feet(checkpoints.get(1)));
        assertTrue(drain(player).stream().noneMatch(m -> m.contains("Checkpoint")), "skipped checkpoint not counted");
        move(player, new Location(lobbyWorld(), start.getX(), fallY - 2, start.getZ()));
        assertEquals(start.getBlockX(), player.getLocation().getBlockX(), "fell back to the start plate");
        assertEquals(start.getBlockZ(), player.getLocation().getBlockZ());

        for (int i = 0; i < checkpoints.size(); i++) {
            move(player, feet(checkpoints.get(i)));
            int number = i + 1;
            assertTrue(drain(player).stream().anyMatch(m -> m.contains("Checkpoint " + number + "/3")), "checkpoint " + number);
        }
        // Falling now returns to the last checkpoint.
        move(player, new Location(lobbyWorld(), start.getX(), fallY - 2, start.getZ()));
        assertEquals(feet(checkpoints.get(2)).getBlockX(), player.getLocation().getBlockX(), "fell back to checkpoint 3");
        Thread.sleep(30);
        move(player, finish);
        List<String> messages = drain(player);
        assertTrue(messages.stream().anyMatch(m -> m.contains("Parkour complete")), "finished: " + messages);
        assertTrue(messages.stream().anyMatch(m -> m.contains("RECORD")), "first time on the server is a record");
        assertEquals(Material.IRON_SWORD, player.getInventory().getItem(0).getType(), "lobby hotbar back");
        assertTrue(player.getAllowFlight(), "double jump back");

        Location board = point("holograms.parkour");
        await("best times board shows the runner", () -> plain(displayNear(board).text()).contains("Runner"), 2000);

        // A second, slower run is not a personal best.
        move(player, start);
        for (String checkpoint : checkpoints) {
            move(player, feet(checkpoint));
        }
        Thread.sleep(300);
        drain(player);
        move(player, finish);
        assertTrue(drain(player).stream().anyMatch(m -> m.contains("Finished in") && m.contains("Your best is")), "slower run");
        player.setOp(true);
        assertTrue(run(player, "lobby parkour top"));
        assertTrue(drain(player).stream().anyMatch(m -> m.contains("#1 Runner")), "/lobby parkour top");
    }

    @Test
    void eggsButtonsAndProtection() throws Exception {
        PlayerMock player = join("Hunter");
        YamlConfiguration layout = layout();
        List<String> eggs = new ArrayList<>(layout.getConfigurationSection("eggs").getKeys(false));
        assertTrue(eggs.size() >= 9);
        drain(player);
        PlayerInteractEvent first = rightClick(player, block(layout.getString("eggs." + eggs.get(0))));
        assertTrue(first.isCancelled(), "the egg does not teleport or get used");
        assertTrue(drain(player).stream().anyMatch(m -> m.contains("You found an egg") && m.contains("1/" + eggs.size())), "first egg");
        rightClick(player, block(layout.getString("eggs." + eggs.get(0))));
        assertTrue(drain(player).stream().noneMatch(m -> m.contains("You found an egg")), "an egg counts once");
        assertFalse(player.hasPermission("pvp.cosmetic.trail.emerald"));
        for (String egg : eggs.subList(1, eggs.size())) {
            rightClick(player, block(layout.getString("eggs." + egg)));
        }
        assertTrue(drain(player).stream().anyMatch(m -> m.contains("You found every egg")), "all eggs");
        assertTrue(player.hasPermission("pvp.cosmetic.trail.emerald"), "finding every egg unlocks the reward trail");
        assertTrue(((PracticePlayerMock) player).lastTitle().contains("EGG HUNTER"));

        // An anvil in the Kit Workshop opens the kit editor, even with the queue item in hand.
        player.getInventory().setHeldItemSlot(0);
        String anvil = layout.getMapList("buttons").stream().filter(b -> "kit-editor".equals(b.get("action")))
                .map(b -> String.valueOf(b.get("at"))).findFirst().orElseThrow();
        rightClick(player, block(anvil));
        ticks(2);
        assertTrue(menu(player).contains("Kit Editor"), "button opens the kit editor, not the held item: " + menu(player));
        player.closeInventory();

        // Protection.
        Block floor = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        BlockBreakEvent breaking = new BlockBreakEvent(floor, player);
        server.getPluginManager().callEvent(breaking);
        assertTrue(breaking.isCancelled(), "no block breaking");
        FoodLevelChangeEvent hunger = new FoodLevelChangeEvent(player, 5, new ItemStack(Material.BREAD));
        server.getPluginManager().callEvent(hunger);
        assertTrue(hunger.isCancelled(), "no hunger");
        Mannequin npc = npc("ranked");
        CreatureSpawnEvent natural = new CreatureSpawnEvent(npc, CreatureSpawnEvent.SpawnReason.NATURAL);
        server.getPluginManager().callEvent(natural);
        assertTrue(natural.isCancelled(), "no natural mob spawns in the lobby world");
    }

    @Test
    void leaderboardWallRotatesThroughStatistics() throws Exception {
        join("Viewer");
        World world = lobbyWorld();
        List<TextDisplay> panels = world.getEntitiesByClass(TextDisplay.class).stream()
                .filter(d -> d.getBillboard() == Display.Billboard.FIXED).toList();
        assertEquals(13, panels.size(), "global plus 12 ranked kits");
        TextDisplay global = panels.stream().filter(d -> plain(d.text()).contains("Global")).findFirst().orElseThrow();
        // The wall cycles ELO -> Wins -> Best Win Streak every 2 s (set in beforeLoad); see all three go by.
        java.util.Set<String> seen = new java.util.HashSet<>();
        await("the wall cycles through its statistics", () -> {
            String text = plain(global.text());
            for (String stat : List.of("Best Win Streak", "Wins", "ELO")) {
                if (text.contains("\n" + stat + "\n") || text.contains("\n" + stat)) {
                    seen.add(stat);
                    break;
                }
            }
            return seen.size() == 3;
        }, 10000);
        assertTrue(panels.stream().anyMatch(d -> plain(d.text()).contains("NoDebuff")), "kit panels");
    }

    @Test
    void adminToolsReloadSetAndRegenerate() throws Exception {
        PlayerMock admin = join("Builder");
        admin.setOp(true);
        PlayerMock guest = join("Guest");

        // /lobby for a normal player is /spawn.
        guest.teleport(point("spawn").add(10, 0, 10));
        assertTrue(run(guest, "lobby"));
        assertEquals(point("spawn").getX(), guest.getLocation().getX(), 1e-6);

        // Move an NPC where the admin stands; layout.yml is updated and the NPC respawns there.
        Location spot = point("spawn").add(3, 0, 0);
        admin.teleport(spot);
        assertTrue(run(admin, "lobby set npc ranked"));
        assertEquals(spot.getX(), npc("ranked").getLocation().getX(), 0.11);
        assertEquals(9, lobbyWorld().getEntitiesByClass(Mannequin.class).size(), "no duplicate NPCs");

        // Pads and eggs can be added in game; the pad gets a visible plate.
        int pads = layout().getMapList("launch-pads").size();
        admin.teleport(spot.clone().add(0, 0, 2));
        assertTrue(run(admin, "lobby pad 2"));
        assertEquals(pads + 1, layout().getMapList("launch-pads").size(), "pad saved to layout.yml");
        assertEquals(Material.HEAVY_WEIGHTED_PRESSURE_PLATE, admin.getLocation().getBlock().getType(), "pad plate placed");
        assertTrue(run(admin, "lobby remove pad"));
        assertEquals(pads, layout().getMapList("launch-pads").size(), "nearest pad removed");

        // Edit npcs.yml and reload: the hologram text changes.
        File npcs = new File(lobby.getDataFolder(), "npcs.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(npcs);
        yaml.set("npcs.ranked.hologram", List.of("<gold>COMPETITIVE", "<white><ranked_queued></white> waiting"));
        yaml.save(npcs);
        drain(admin);
        assertTrue(run(admin, "lobby reload"));
        assertTrue(drain(admin).stream().anyMatch(m -> m.contains("Lobby reloaded")));
        Location plate = npc("ranked").getLocation().add(0, 2.05, 0);
        await("reloaded NPC text", () -> plain(displayNear(plate).text()).contains("COMPETITIVE"), 3000);

        // Something else removing an NPC (/kill) is noticed and repaired.
        npc("stats").remove();
        await("the watchdog respawns removed NPCs", () -> lobbyWorld().getEntitiesByClass(Mannequin.class).stream()
                .filter(Mannequin::isValid).count() == 9, 5000);

        // /pvpadmin reload reloads the lobby too.
        drain(admin);
        assertTrue(run(admin, "pvpadmin reload"));
        ticks(5);
        assertTrue(drain(admin).stream().noneMatch(m -> m.toLowerCase().contains("error")), "reload without errors");
        assertEquals(9, lobbyWorld().getEntitiesByClass(Mannequin.class).size(), "NPCs respawned once after /pvpadmin reload");

        // Regenerate with another seed: the world is rebuilt, layout.yml is backed up and everything respawns.
        File folder = new File(server.getWorldContainer(), LOBBY);
        await("pasted blocks saved", () -> new File(folder, "pvplobby.template").exists(), 5000);
        drain(admin);
        assertTrue(run(admin, "lobby regenerate 42"));
        awaitMessage(admin, "Lobby hub rebuilt", 20000);
        assertTrue(new File(lobby.getDataFolder(), "layout.yml.bak").exists(), "old layout kept");
        assertEquals("42", YamlConfiguration.loadConfiguration(new File(folder, "pvplobby.yml")).getString("detail"));
        assertEquals(9, lobbyWorld().getEntitiesByClass(Mannequin.class).size(), "NPCs respawned once");
        assertTrue(admin.getLocation().getBlock().getRelative(BlockFace.DOWN).getType().isSolid(), "players stand on the new hub");
    }

    /** A 21x5x21 quartz lobby with tagged signs for spawn, an NPC, a portal, a pad, a parkour and an egg. */
    private static byte[] lobbySchematic() throws IOException {
        int w = 21;
        int h = 5;
        int l = 21;
        java.io.ByteArrayOutputStream data = new java.io.ByteArrayOutputStream();
        Map<String, int[]> signs = new LinkedHashMap<>();
        signs.put("[spawn]", new int[]{10, 1, 10});
        signs.put("[npc ranked]", new int[]{10, 1, 4});
        signs.put("[portal unranked]", new int[]{3, 1, 3});
        signs.put("[pad 2]", new int[]{16, 1, 16});
        signs.put("[parkour start]", new int[]{2, 1, 17});
        signs.put("[checkpoint]", new int[]{4, 2, 17});
        signs.put("[parkour finish]", new int[]{6, 3, 17});
        signs.put("[egg attic]", new int[]{18, 1, 2});
        for (int y = 0; y < h; y++) {
            for (int z = 0; z < l; z++) {
                for (int x = 0; x < w; x++) {
                    int value = y == 0 ? 1 : 0;
                    for (int[] p : signs.values()) {
                        if (p[0] == x && p[1] == y && p[2] == z) {
                            value = 2;
                        }
                    }
                    if ((x == 4 && z == 17 && y == 1) || (x == 6 && z == 17 && y <= 2 && y >= 1)) {
                        value = 1;
                    }
                    data.write(value);
                }
            }
        }
        Map<String, Object> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        palette.put("minecraft:quartz_block", 1);
        palette.put("minecraft:oak_sign[rotation=8,waterlogged=false]", 2);
        List<Object> entities = new ArrayList<>();
        for (Map.Entry<String, int[]> sign : signs.entrySet()) {
            Map<String, Object> text = new LinkedHashMap<>();
            text.put("messages", List.of("\"" + sign.getKey() + "\"", "\"\"", "\"\"", "\"\""));
            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("Pos", sign.getValue());
            entity.put("Id", "minecraft:sign");
            entity.put("front_text", text);
            entities.add(entity);
        }
        Map<String, Object> schematic = new LinkedHashMap<>();
        schematic.put("Version", 2);
        schematic.put("DataVersion", 4556);
        schematic.put("Width", (short) w);
        schematic.put("Height", (short) h);
        schematic.put("Length", (short) l);
        schematic.put("PaletteMax", 3);
        schematic.put("Palette", palette);
        schematic.put("BlockData", data.toByteArray());
        schematic.put("BlockEntities", entities);
        return nbt("Schematic", schematic);
    }

    @Test
    void importedSchematicReplacesTheHub() throws Exception {
        PlayerMock admin = join("Importer");
        admin.setOp(true);
        File imports = new File(lobby.getDataFolder(), "imports");
        assertTrue(new File(imports, "README.txt").exists(), "the import folder explains itself");
        Files.write(new File(imports, "tiny_lobby.schem").toPath(), lobbySchematic());
        File folder = new File(server.getWorldContainer(), LOBBY);
        await("pasted blocks saved", () -> new File(folder, "pvplobby.template").exists(), 5000);
        Location oldSpawn = point("spawn");
        String rankedPortal = layout().getString("portals.ranked.from");

        drain(admin);
        assertTrue(run(admin, "lobby imports"));
        assertTrue(drain(admin).stream().anyMatch(m -> m.contains("tiny_lobby.schem")));
        assertTrue(run(admin, "lobby import tiny_lobby"));
        awaitMessage(admin, "Imported tiny_lobby.schem", 20000);

        YamlConfiguration layout = layout();
        Location spawn = point("spawn");
        assertEquals(65, spawn.getY(), 1e-6, "the [spawn] sign lands on world.floor-y + 1");
        assertEquals(0, spawn.getYaw(), 1e-6, "players face the way the builder faced");
        assertEquals(Material.QUARTZ_BLOCK, lobbyWorld().getBlockAt(spawn.getBlockX(), 64, spawn.getBlockZ()).getType(), "imported floor");
        assertEquals(LOBBY, admin.getWorld().getName());
        assertEquals(spawn.getX(), admin.getLocation().getX(), 1e-6, "players are moved to the new spawn");
        assertEquals(1, lobbyWorld().getEntitiesByClass(Mannequin.class).size(), "only the tagged NPC spawns");
        assertEquals("queue-unranked", layout.getString("portals.unranked.action"));
        assertEquals(1, layout.getMapList("launch-pads").size());
        assertEquals(1, layout.getStringList("parkour.checkpoints").size());
        assertTrue(layout.contains("eggs.attic"));
        assertEquals(Material.DRAGON_EGG, block(layout.getString("eggs.attic")).getType(), "egg sign became an egg");
        assertTrue(block(layout.getString("parkour.start")).getType().name().contains("PRESSURE_PLATE"), "start sign became a plate");
        double[] portal = numbers(rankedPortal);
        assertTrue(lobbyWorld().getBlockAt((int) portal[0] - 1, (int) portal[1], (int) portal[2]).getType().isAir(),
                "the generated hub was cleared");
        assertEquals("schematic", YamlConfiguration.loadConfiguration(new File(folder, "pvplobby.yml")).getString("source"));
        assertNotEquals(oldSpawn, spawn);

        // Regenerate brings the hub back.
        drain(admin);
        assertTrue(run(admin, "lobby regenerate"));
        awaitMessage(admin, "Lobby hub rebuilt", 20000);
        assertEquals(9, lobbyWorld().getEntitiesByClass(Mannequin.class).size());
        assertEquals("generated", YamlConfiguration.loadConfiguration(new File(folder, "pvplobby.yml")).getString("source"));
    }

    @Test
    void importedWorldFolderReplacesTheLobbyWorld() throws Exception {
        PlayerMock admin = join("WorldImporter");
        admin.setOp(true);
        File source = new File(new File(lobby.getDataFolder(), "imports"), "MyLobbyWorld");
        new File(source, "region").mkdirs();
        Files.writeString(new File(source, "level.dat").toPath(), "not really nbt; the mock only copies it");
        Files.writeString(new File(source, "uid.dat").toPath(), "copies must get their own uid");
        await("pasted blocks saved", () -> new File(new File(server.getWorldContainer(), LOBBY), "pvplobby.template").exists(), 5000);

        drain(admin);
        assertTrue(run(admin, "lobby import MyLobbyWorld"));
        awaitMessage(admin, "Imported MyLobbyWorld", 20000);
        File copy = new File(server.getWorldContainer(), LOBBY);
        assertTrue(new File(copy, "level.dat").exists(), "world folder copied into the server");
        assertFalse(new File(copy, "uid.dat").exists(), "the copy gets a fresh world UUID");
        assertEquals("world", YamlConfiguration.loadConfiguration(new File(copy, "pvplobby.yml")).getString("source"));
        assertEquals(LOBBY, admin.getWorld().getName(), "players are back in the (new) lobby world");
        assertTrue(lobbyWorld().getEntitiesByClass(Mannequin.class).isEmpty(), "no NPC positions in an untagged world");
        assertTrue(layout().contains("spawn"));
        // Another player joining lands in the imported world.
        PlayerMock late = join("LateJoiner");
        assertEquals(LOBBY, late.getWorld().getName());
    }

    @Test
    void lobbyCosmeticsCanBeChosen() throws Exception {
        PlayerMock player = join("Stylish");
        assertTrue(run(player, "cosmetics"));
        clickSlot(player, 14);
        assertTrue(menu(player).contains("Lobby Trails"), menu(player));
        drain(player);
        clickItem(player, Material.WHITE_WOOL);
        assertTrue(drain(player).stream().anyMatch(m -> m.contains("Selected") && m.contains("Cloud")), "free trail selected");
        player.closeInventory();
        // Walking around with a trail on is harmless (particles are client side).
        Location here = player.getLocation();
        for (int i = 1; i <= 5; i++) {
            move(player, here.clone().add(i, 0, 0));
            ticks(3);
        }
        assertTrue(run(player, "cosmetics"));
        clickSlot(player, 16);
        assertTrue(menu(player).contains("Join Effects"), menu(player));
    }
}
