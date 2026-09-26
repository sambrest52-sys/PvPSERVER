package net.pvpserver.core.arena;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.util.Cuboid;
import net.pvpserver.core.util.Tasks;
import net.pvpserver.core.world.WorldService;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * In-game arena authoring: selection wand, edit sessions and capture of a region into a template.
 */
public final class ArenaEditor implements Listener {

    private static final long MAX_VOLUME = 8_000_000L;

    private final JavaPlugin plugin;
    private final ArenaService arenas;
    private final WorldService worlds;
    private final MessageService messages;
    private final NamespacedKey wandKey;
    private final Map<UUID, ArenaEditSession> sessions = new HashMap<>();
    private final String editorWorldName;
    private World editorWorld;

    /**
     * @param plugin core plugin
     * @param arenas arena service
     * @param worlds world service
     * @param messages messages
     * @param editorWorldName name of the persistent editor world
     */
    public ArenaEditor(JavaPlugin plugin, ArenaService arenas, WorldService worlds, MessageService messages, String editorWorldName) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.worlds = worlds;
        this.messages = messages;
        this.wandKey = new NamespacedKey(plugin, "arena_wand");
        this.editorWorldName = editorWorldName;
    }

    private World editorWorld() {
        if (editorWorld == null) {
            editorWorld = worlds.voidWorld(editorWorldName, false);
        }
        return editorWorld;
    }

    /**
     * @param player admin
     * @return current session or null
     */
    public ArenaEditSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /**
     * Starts a blank session for a new arena.
     *
     * @param player admin
     * @param name arena name
     */
    public void create(Player player, String name) {
        String id = name.toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]{1,32}")) {
            messages.send(player, "arena.invalid-name");
            return;
        }
        if (arenas.arena(id) != null) {
            messages.send(player, "arena.exists", MessageService.p("arena", id));
            return;
        }
        sessions.put(player.getUniqueId(), new ArenaEditSession(id));
        giveWand(player);
        messages.send(player, "arena.session-created", MessageService.p("arena", id));
    }

    /**
     * Pastes an existing arena into the editor world and opens a session with its data.
     *
     * @param player admin
     * @param name arena name
     */
    public void edit(Player player, String name) {
        Arena arena = arenas.arena(name);
        ArenaTemplate template = arena == null ? null : arenas.template(arena.name());
        if (arena == null || template == null) {
            messages.send(player, "arena.not-found", MessageService.p("arena", name));
            return;
        }
        open(player, arena, template, session -> messages.send(player, "arena.editing", MessageService.p("arena", arena.name())));
    }

    /**
     * Pastes a template into the editor world and opens an edit session pre-filled from a definition (whose
     * positions are relative to the template). Used by {@code /arena edit} and by imports.
     *
     * @param player admin
     * @param arena definition (spawns may be null)
     * @param template blocks
     * @param onOpened called on the main thread once the paste is done and the admin was teleported
     */
    void open(Player player, Arena arena, ArenaTemplate template, java.util.function.Consumer<ArenaEditSession> onOpened) {
        World world = editorWorld();
        int[] slot = editorSlot(arena.name(), template);
        int ox = slot[0] * 512;
        int oy = 64;
        int oz = 0;
        messages.send(player, "arena.pasting", MessageService.p("arena", arena.name()));
        // Clear what the previous edit of this arena left in its slot (the editor world persists), then paste.
        arenas.paster().clearBox(world, ox, oy, oz, slot[1], slot[2], slot[3])
                .thenCompose(v -> arenas.paster().paste(template, world, ox, oy, oz)).thenRun(() -> Tasks.sync(() -> {
            ArenaEditSession session = new ArenaEditSession(arena.name());
            session.pos1 = new Location(world, ox, oy, oz);
            session.pos2 = new Location(world, ox + template.sizeX() - 1, oy + template.sizeY() - 1, oz + template.sizeZ() - 1);
            session.spawnA = arena.spawnA() == null ? null : arena.spawnA().toLocation(world, ox, oy, oz);
            session.spawnB = arena.spawnB() == null ? null : arena.spawnB().toLocation(world, ox, oy, oz);
            session.spectator = arena.spectator() == null ? null : arena.spectator().toLocation(world, ox, oy, oz);
            session.goalA = arena.goalA() == null ? null : arena.goalA().toLocation(world, ox, oy, oz);
            session.goalB = arena.goalB() == null ? null : arena.goalB().toLocation(world, ox, oy, oz);
            session.buildLimitY = oy + arena.buildLimit();
            session.voidY = oy + arena.voidY();
            session.goalRadius = arena.goalRadius();
            session.buildArea = arena.buildArea();
            session.displayName = arena.displayName();
            session.icon = arena.icon();
            session.tags = new HashSet<>(arena.tags());
            session.enabled = arena.enabled();
            sessions.put(player.getUniqueId(), session);
            giveWand(player);
            Location tp = session.spawnA != null ? session.spawnA : session.pos1.clone().add(template.sizeX() / 2.0, template.sizeY(), template.sizeZ() / 2.0);
            player.teleport(tp);
            onOpened.accept(session);
        }));
    }

    /**
     * Returns the editor-world slot of an arena and the size of what was pasted there last time, then records the new
     * size. Slots are stable per arena name (stored in {@code arenas/editor-slots.yml}) so edits never overlap.
     *
     * @return {slot, previous sizeX, previous sizeY, previous sizeZ}
     */
    private int[] editorSlot(String name, ArenaTemplate template) {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "arenas/editor-slots.yml");
        org.bukkit.configuration.file.YamlConfiguration slots = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        int slot;
        int[] previous = {0, 0, 0};
        if (slots.isConfigurationSection("slots." + name)) {
            slot = slots.getInt("slots." + name + ".index");
            previous = new int[]{slots.getInt("slots." + name + ".x"), slots.getInt("slots." + name + ".y"), slots.getInt("slots." + name + ".z")};
        } else {
            slot = slots.getInt("next", 0);
            slots.set("next", slot + 1);
        }
        slots.set("slots." + name + ".index", slot);
        slots.set("slots." + name + ".x", template.sizeX());
        slots.set("slots." + name + ".y", template.sizeY());
        slots.set("slots." + name + ".z", template.sizeZ());
        try {
            slots.save(file);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not save " + file + ": " + e.getMessage());
        }
        return new int[]{slot, previous[0], previous[1], previous[2]};
    }

    /**
     * Captures the session region into a template and stores the arena.
     *
     * @param player admin
     */
    public void save(Player player) {
        ArenaEditSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            messages.send(player, "arena.no-session");
            return;
        }
        if (session.pos1 == null || session.pos2 == null || session.spawnA == null || session.spawnB == null) {
            messages.send(player, "arena.incomplete");
            return;
        }
        if (session.pos1.getWorld() != session.pos2.getWorld()) {
            messages.send(player, "arena.different-worlds");
            return;
        }
        Cuboid region = Cuboid.of(session.pos1, session.pos2);
        if (region.volume() > MAX_VOLUME) {
            messages.send(player, "arena.too-large", MessageService.p("volume", region.volume()));
            return;
        }
        World world = session.pos1.getWorld();
        int minX = region.minX();
        int minY = region.minY();
        int minZ = region.minZ();
        if (session.buildArea1 != null && session.buildArea2 != null) {
            session.buildArea = RelativeBox.of(session.buildArea1.getBlockX() - minX, session.buildArea1.getBlockY() - minY,
                    session.buildArea1.getBlockZ() - minZ, session.buildArea2.getBlockX() - minX,
                    session.buildArea2.getBlockY() - minY, session.buildArea2.getBlockZ() - minZ);
        }
        Arena arena = new Arena(session.name, session.displayName, session.icon, session.enabled, Set.copyOf(session.tags),
                RelativePosition.of(session.spawnA, minX, minY, minZ), RelativePosition.of(session.spawnB, minX, minY, minZ),
                session.spectator == null ? null : RelativePosition.of(session.spectator, minX, minY, minZ),
                (session.buildLimitY == null ? region.maxY() + 6 : session.buildLimitY) - minY,
                (session.voidY == null ? minY - 6 : session.voidY) - minY,
                session.goalA == null ? null : RelativePosition.of(session.goalA, minX, minY, minZ),
                session.goalB == null ? null : RelativePosition.of(session.goalB, minX, minY, minZ),
                session.goalRadius, session.buildArea);
        messages.send(player, "arena.saving", MessageService.p("arena", session.name), MessageService.p("volume", region.volume()));
        capture(world, region)
                .thenCompose(template -> arenas.save(arena, template))
                .whenComplete((v, error) -> Tasks.sync(() -> {
                    if (error != null) {
                        plugin.getLogger().severe("Arena save failed: " + error.getMessage());
                        messages.send(player, "arena.save-failed");
                    } else {
                        messages.send(player, "arena.saved", MessageService.p("arena", session.name));
                    }
                }));
    }

    /**
     * Snapshots the region's chunks on the main thread and builds the template asynchronously.
     *
     * @param world world
     * @param region region
     * @return template future
     */
    public CompletableFuture<ArenaTemplate> capture(World world, Cuboid region) {
        Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
        for (int cx = region.minX() >> 4; cx <= region.maxX() >> 4; cx++) {
            for (int cz = region.minZ() >> 4; cz <= region.maxZ() >> 4; cz++) {
                snapshots.put(((long) cx << 32) | (cz & 0xFFFFFFFFL), world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false));
            }
        }
        return CompletableFuture.supplyAsync(() -> {
            TemplateBuilder builder = new TemplateBuilder(region.sizeX(), region.sizeY(), region.sizeZ());
            for (int x = region.minX(); x <= region.maxX(); x++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    ChunkSnapshot snapshot = snapshots.get(((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL));
                    for (int y = region.minY(); y <= region.maxY(); y++) {
                        Material type = snapshot.getBlockType(x & 15, y, z & 15);
                        if (type.isAir()) {
                            continue;
                        }
                        builder.set(x - region.minX(), y - region.minY(), z - region.minZ(),
                                snapshot.getBlockData(x & 15, y, z & 15).getAsString());
                    }
                }
            }
            return builder.build();
        });
    }

    /**
     * @param player admin
     */
    public void giveWand(Player player) {
        ItemStack wand = ItemBuilder.of(Material.BLAZE_ROD)
                .name(Component.text("Arena Wand", NamedTextColor.GOLD))
                .lore(java.util.List.of(Component.text("Left click: position 1", NamedTextColor.GRAY),
                        Component.text("Right click: position 2", NamedTextColor.GRAY)))
                .tag(wandKey, "1").build();
        player.getInventory().addItem(wand);
    }

    /**
     * Ends the session without saving.
     *
     * @param player admin
     */
    public void cancel(Player player) {
        if (sessions.remove(player.getUniqueId()) != null) {
            messages.send(player, "arena.session-cancelled");
        } else {
            messages.send(player, "arena.no-session");
        }
    }

    @EventHandler
    public void onWand(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.STRING)) {
            return;
        }
        ArenaEditSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        Location location = event.getClickedBlock().getLocation();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            session.pos1 = location;
            messages.send(event.getPlayer(), "arena.pos-set", MessageService.p("pos", "1"), MessageService.p("location", format(location)));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            session.pos2 = location;
            messages.send(event.getPlayer(), "arena.pos-set", MessageService.p("pos", "2"), MessageService.p("location", format(location)));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    /**
     * @param location location
     * @return {@code x, y, z}
     */
    static String format(Location location) {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }
}
