package net.pvpserver.core.arena;

import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.config.Reloadable;
import net.pvpserver.core.util.Tasks;
import net.pvpserver.core.world.WorldService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 * Owns arena definitions, templates and the pool of pasted instances in the disposable arena world.
 * <p>
 * Instances live on a grid of slots {@code spacing} blocks apart. Matches {@link #acquire} an instance and
 * {@link #release} it afterwards; released instances are rolled back (journal) and kept idle for reuse, up to
 * {@code max-idle-per-arena}. The arena world is deleted on every start, which doubles as crash cleanup.
 */
public final class ArenaService implements Reloadable {

    private final JavaPlugin plugin;
    private final ConfigFile file;
    private final ConfigurationSection settings;
    private final WorldService worlds;
    private final File templateFolder;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<String, ArenaTemplate> templates = new ConcurrentHashMap<>();
    private final Map<Integer, ArenaInstance> instances = new HashMap<>();
    private final Map<String, Deque<ArenaInstance>> idle = new HashMap<>();
    private final Deque<Request> pending = new ArrayDeque<>();
    private final BitSet usedSlots = new BitSet();
    private final BlockPaster paster;
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private World world;
    private int spacing;
    private int gridWidth;
    private int pasteY;
    private int maxInstances;
    private int maxIdlePerArena;
    private int prewarm;
    private int margin;

    /**
     * @param plugin core plugin
     * @param settings {@code arenas} section of config.yml
     * @param worlds world service
     */
    public ArenaService(JavaPlugin plugin, ConfigurationSection settings, WorldService worlds) {
        this.plugin = plugin;
        this.settings = settings;
        this.worlds = worlds;
        this.file = new ConfigFile(plugin, "arenas.yml");
        this.templateFolder = new File(plugin.getDataFolder(), "arenas");
        this.paster = new BlockPaster(settings.getInt("blocks-per-tick", 20000), settings.getInt("max-millis-per-tick", 8));
    }

    /**
     * Creates the arena world, generates placeholder arenas on first run, loads templates and pre-warms the pool.
     */
    public void start() {
        spacing = Math.max(128, settings.getInt("slot-spacing", 512));
        gridWidth = Math.max(1, settings.getInt("grid-width", 32));
        pasteY = settings.getInt("paste-y", 64);
        maxInstances = Math.max(1, settings.getInt("max-instances", 150));
        maxIdlePerArena = Math.max(0, settings.getInt("max-idle-per-arena", 6));
        prewarm = Math.max(0, settings.getInt("prewarm-per-arena", 1));
        margin = Math.max(0, settings.getInt("instance-margin", 16));
        world = worlds.voidWorld(settings.getString("world", "pvp_arenas"), true);
        if (settings.getBoolean("install-builtin-arenas", true)) {
            ArenaGenerator.installMissing(plugin.getLogger(), templateFolder, file);
        }
        paster.start();
        loadDefinitions();
        loadTemplatesAsync().thenRun(() -> Tasks.sync(() -> {
            prewarm();
            ready.complete(null);
        }));
    }

    /**
     * Completes on the main thread once templates are loaded (gamemodes that paste templates themselves wait on it).
     *
     * @return readiness future
     */
    public CompletableFuture<Void> ready() {
        return ready;
    }

    @Override
    public void reload() {
        file.reload();
        loadDefinitions();
        loadTemplatesAsync();
    }

    private void loadDefinitions() {
        arenas.clear();
        ConfigurationSection root = file.get().getConfigurationSection("arenas");
        if (root == null) {
            return;
        }
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) {
                continue;
            }
            String id = name.toLowerCase(Locale.ROOT);
            Set<String> tags = new HashSet<>();
            s.getStringList("tags").forEach(t -> tags.add(t.toLowerCase(Locale.ROOT)));
            if (tags.isEmpty()) {
                tags.add("standard");
            }
            Material icon = Material.matchMaterial(s.getString("icon", "GRASS_BLOCK"));
            arenas.put(id, new Arena(id, s.getString("display-name", "<white>" + name), icon == null ? Material.GRASS_BLOCK : icon,
                    s.getBoolean("enabled", true), tags, RelativePosition.parse(s.getString("spawn-a")),
                    RelativePosition.parse(s.getString("spawn-b")), RelativePosition.parse(s.getString("spectator")),
                    s.getInt("build-limit", 12), s.getInt("void-y", -6), RelativePosition.parse(s.getString("goal-a")),
                    RelativePosition.parse(s.getString("goal-b")), s.getDouble("goal-radius", 1.6),
                    RelativeBox.parse(s.getString("build-area"))));
        }
        plugin.getLogger().info("Loaded " + arenas.size() + " arena definitions");
    }

    private CompletableFuture<Void> loadTemplatesAsync() {
        List<String> names = new ArrayList<>(arenas.keySet());
        return CompletableFuture.runAsync(() -> {
            for (String name : names) {
                if (!plugin.isEnabled()) {
                    return; // disabled mid-reload (shutdown): nothing left to load into
                }
                File templateFile = new File(templateFolder, name + ".arena");
                if (!templateFile.exists()) {
                    plugin.getLogger().warning("Arena " + name + " has no template file (" + templateFile.getName() + ")");
                    continue;
                }
                try {
                    ArenaTemplate template = ArenaTemplate.read(templateFile);
                    int maxFootprint = Math.max(128, settings.getInt("slot-spacing", 512)) - 2 * Math.max(0, settings.getInt("instance-margin", 16));
                    if (template.sizeX() > maxFootprint || template.sizeZ() > maxFootprint) {
                        plugin.getLogger().severe("Arena " + name + " is " + template.sizeX() + "x" + template.sizeZ()
                                + " blocks, larger than a pool slot allows (" + maxFootprint + "); raise arenas.slot-spacing");
                        continue;
                    }
                    templates.put(name, template);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not read arena template " + name, e);
                }
            }
        }).thenRun(() -> Tasks.sync(() -> {
            // Parse palettes on the main thread once so pastes never do it mid-match.
            templates.values().forEach(ArenaTemplate::parsedPalette);
            plugin.getLogger().info("Loaded " + templates.size() + " arena templates");
        }));
    }

    private void prewarm() {
        for (Arena arena : arenas.values()) {
            if (!usable(arena)) {
                continue;
            }
            for (int i = 0; i < prewarm && instances.size() < maxInstances; i++) {
                ArenaInstance instance = create(arena);
                if (instance == null) {
                    break;
                }
                instance.state(ArenaInstance.State.PASTING);
                paster.pasteInBackground(instance.template(), world, instance.originX(), instance.originY(), instance.originZ())
                        .thenRun(() -> Tasks.sync(() -> becomeIdle(instance)));
            }
        }
    }

    private boolean usable(Arena arena) {
        return arena.enabled() && arena.complete() && templates.containsKey(arena.name());
    }

    /**
     * Borrows an instance of an arena accepted by the filter.
     *
     * @param filter which arenas are acceptable (e.g. kit whitelist)
     * @param preferred preferred arena name or null
     * @return future completing on the main thread when the instance is pasted and ready
     */
    public CompletableFuture<ArenaInstance> acquire(Predicate<Arena> filter, String preferred) {
        List<Arena> candidates = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            if (usable(arena) && filter.test(arena)) {
                candidates.add(arena);
            }
        }
        if (preferred != null) {
            Arena wanted = arenas.get(preferred.toLowerCase(Locale.ROOT));
            if (wanted != null && candidates.contains(wanted)) {
                candidates = List.of(wanted);
            }
        }
        if (candidates.isEmpty()) {
            return CompletableFuture.failedFuture(new NoArenaAvailableException("No arena matches"));
        }
        // Pick the arena uniformly at random, then prefer a pooled copy of it; paste a new copy when none is idle
        // (a few ticks even for large maps). Only when the pool is full does another arena's idle copy stand in.
        List<Arena> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        ArenaInstance pooled = pollIdle(shuffled.get(0));
        if (pooled != null) {
            return CompletableFuture.completedFuture(pooled);
        }
        if (instances.size() >= maxInstances) {
            for (Arena arena : shuffled) {
                ArenaInstance other = pollIdle(arena);
                if (other != null) {
                    return CompletableFuture.completedFuture(other);
                }
            }
        }
        if (instances.size() < maxInstances) {
            ArenaInstance instance = create(shuffled.get(0));
            if (instance != null) {
                CompletableFuture<ArenaInstance> future = new CompletableFuture<>();
                paste(instance).whenComplete((v, error) -> Tasks.sync(() -> {
                    if (error != null) {
                        destroy(instance);
                        future.completeExceptionally(error);
                    } else {
                        instance.state(ArenaInstance.State.IN_USE);
                        future.complete(instance);
                    }
                }));
                return future;
            }
        }
        Request request = new Request(filter, preferred, new CompletableFuture<>());
        pending.add(request);
        return request.future;
    }

    private ArenaInstance pollIdle(Arena arena) {
        Deque<ArenaInstance> queue = idle.get(arena.name());
        if (queue == null || queue.isEmpty()) {
            return null;
        }
        ArenaInstance instance = queue.poll();
        instance.state(ArenaInstance.State.IN_USE);
        return instance;
    }

    /**
     * Regenerates built-in arenas (all when {@code names} is empty) and installs them like editor saves.
     *
     * @param names built-in arena names, or empty for all
     * @return future with the regenerated names (main thread)
     */
    public CompletableFuture<List<String>> regenerateBuiltins(Collection<String> names) {
        List<String> targets = new ArrayList<>();
        for (String name : names.isEmpty() ? net.pvpserver.core.arena.gen.BuiltinArenas.names() : names) {
            String id = name.toLowerCase(Locale.ROOT);
            if (net.pvpserver.core.arena.gen.BuiltinArenas.names().contains(id)) {
                targets.add(id);
            }
        }
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (String name : targets) {
            net.pvpserver.core.arena.gen.GeneratedArena generated = net.pvpserver.core.arena.gen.BuiltinArenas.generate(name);
            saves.add(save(generated.toArena(), generated.template()));
        }
        return CompletableFuture.allOf(saves.toArray(new CompletableFuture[0])).thenApply(v -> targets);
    }

    /**
     * Returns an instance to the pool after rolling back all changes.
     *
     * @param instance instance
     */
    public void release(ArenaInstance instance) {
        if (instance.state() != ArenaInstance.State.IN_USE) {
            return;
        }
        instance.state(ArenaInstance.State.RESETTING);
        clearEntities(instance);
        List<Map.Entry<Long, BlockData>> entries = new ArrayList<>(instance.journalEntries().entrySet());
        instance.clearTracking();
        paster.restore(world, entries).whenComplete((v, error) -> Tasks.sync(() -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Arena reset failed; destroying instance", error);
                destroy(instance);
                return;
            }
            becomeIdle(instance);
        }));
    }

    /**
     * Resets the blocks of an in-use instance without releasing it (between rounds).
     *
     * @param instance instance
     * @return completion future (main thread)
     */
    public CompletableFuture<Void> resetInPlace(ArenaInstance instance) {
        clearEntities(instance);
        List<Map.Entry<Long, BlockData>> entries = new ArrayList<>(instance.journalEntries().entrySet());
        instance.clearTracking();
        CompletableFuture<Void> done = new CompletableFuture<>();
        paster.restore(world, entries).whenComplete((v, error) -> Tasks.sync(() -> done.complete(null)));
        return done;
    }

    private void becomeIdle(ArenaInstance instance) {
        instance.state(ArenaInstance.State.IDLE);
        Arena current = arenas.get(instance.arena().name());
        boolean outdated = current == null || !usable(current) || templates.get(current.name()) != instance.template();
        if (!outdated) {
            Iterator<Request> iterator = pending.iterator();
            while (iterator.hasNext()) {
                Request request = iterator.next();
                if (request.filter.test(instance.arena())
                        && (request.preferred == null || request.preferred.equalsIgnoreCase(instance.arena().name()))) {
                    iterator.remove();
                    instance.state(ArenaInstance.State.IN_USE);
                    request.future.complete(instance);
                    return;
                }
            }
        }
        Deque<ArenaInstance> queue = idle.computeIfAbsent(instance.arena().name(), k -> new ArrayDeque<>());
        if (outdated || queue.size() >= maxIdlePerArena) {
            destroy(instance);
        } else {
            queue.add(instance);
        }
    }

    private ArenaInstance create(Arena arena) {
        ArenaTemplate template = templates.get(arena.name());
        if (template == null) {
            return null;
        }
        int slot = usedSlots.nextClearBit(0);
        int ox = (slot % gridWidth + 1) * spacing;
        int oz = (slot / gridWidth + 1) * spacing;
        int maxHeight = world.getMaxHeight() - template.sizeY() - 16;
        int oy = Math.max(world.getMinHeight() + 16, Math.min(pasteY, maxHeight));
        usedSlots.set(slot);
        ArenaInstance instance = new ArenaInstance(arena, template, world, slot, ox, oy, oz, margin);
        instances.put(slot, instance);
        forEachChunk(instance, (cx, cz) -> world.addPluginChunkTicket(cx, cz, plugin));
        return instance;
    }

    private CompletableFuture<Void> paste(ArenaInstance instance) {
        return paster.paste(instance.template(), world, instance.originX(), instance.originY(), instance.originZ());
    }

    private void destroy(ArenaInstance instance) {
        instance.state(ArenaInstance.State.DESTROYED);
        idle.getOrDefault(instance.arena().name(), new ArrayDeque<>()).remove(instance);
        clearEntities(instance);
        List<Map.Entry<Long, BlockData>> journal = new ArrayList<>(instance.journalEntries().entrySet());
        instance.clearTracking();
        paster.restore(world, journal)
                .thenCompose(v -> paster.clear(instance.template(), world, instance.originX(), instance.originY(), instance.originZ()))
                .whenComplete((v, error) -> Tasks.sync(() -> {
                    forEachChunk(instance, (cx, cz) -> world.removePluginChunkTicket(cx, cz, plugin));
                    instances.remove(instance.slot());
                    usedSlots.clear(instance.slot());
                    servePendingWithNewInstance();
                }));
    }

    private void servePendingWithNewInstance() {
        Iterator<Request> iterator = pending.iterator();
        while (iterator.hasNext() && instances.size() < maxInstances) {
            Request request = iterator.next();
            iterator.remove();
            acquire(request.filter, request.preferred).whenComplete((instance, error) -> {
                if (error != null) {
                    request.future.completeExceptionally(error);
                } else {
                    request.future.complete(instance);
                }
            });
        }
    }

    private void clearEntities(ArenaInstance instance) {
        var b = instance.bounds();
        BoundingBox box = new BoundingBox(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
        for (Entity entity : world.getNearbyEntities(box)) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }

    private void forEachChunk(ArenaInstance instance, ChunkConsumer consumer) {
        int minCx = instance.originX() >> 4;
        int maxCx = (instance.originX() + instance.template().sizeX() - 1) >> 4;
        int minCz = instance.originZ() >> 4;
        int maxCz = (instance.originZ() + instance.template().sizeZ() - 1) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                consumer.accept(cx, cz);
            }
        }
    }

    @FunctionalInterface
    private interface ChunkConsumer {
        void accept(int cx, int cz);
    }

    /**
     * Finds the instance containing a location in O(1) via the slot grid.
     *
     * @param location location
     * @return instance or null
     */
    public ArenaInstance instanceAt(Location location) {
        if (world == null || location.getWorld() != world) {
            return null;
        }
        int gx = Math.floorDiv(location.getBlockX() + spacing / 2, spacing) - 1;
        int gz = Math.floorDiv(location.getBlockZ() + spacing / 2, spacing) - 1;
        if (gx < 0 || gz < 0 || gx >= gridWidth) {
            return null;
        }
        ArenaInstance instance = instances.get(gz * gridWidth + gx);
        return instance != null && instance.contains(location) ? instance : null;
    }

    /**
     * Stores or replaces an arena definition and its template (from the editor), invalidating idle instances.
     *
     * @param arena definition
     * @param template template
     * @return completion (main thread)
     */
    public CompletableFuture<Void> save(Arena arena, ArenaTemplate template) {
        File target = new File(templateFolder, arena.name() + ".arena");
        return CompletableFuture.runAsync(() -> {
            try {
                template.write(target);
            } catch (IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }).thenCompose(v -> Tasks.supplySync(() -> {
            template.parsedPalette();
            templates.put(arena.name(), template);
            arenas.put(arena.name(), arena);
            writeDefinition(arena);
            Deque<ArenaInstance> queue = idle.remove(arena.name());
            if (queue != null) {
                new ArrayList<>(queue).forEach(this::destroy);
            }
            return null;
        }));
    }

    /**
     * Persists a definition to arenas.yml.
     *
     * @param arena arena
     */
    public void writeDefinition(Arena arena) {
        String base = "arenas." + arena.name() + ".";
        var c = file.get();
        c.set(base + "display-name", arena.displayName());
        c.set(base + "icon", arena.icon().name());
        c.set(base + "enabled", arena.enabled());
        c.set(base + "tags", new ArrayList<>(arena.tags()));
        c.set(base + "spawn-a", arena.spawnA() == null ? null : arena.spawnA().serialize());
        c.set(base + "spawn-b", arena.spawnB() == null ? null : arena.spawnB().serialize());
        c.set(base + "spectator", arena.spectator() == null ? null : arena.spectator().serialize());
        c.set(base + "build-limit", arena.buildLimit());
        c.set(base + "void-y", arena.voidY());
        c.set(base + "goal-a", arena.goalA() == null ? null : arena.goalA().serialize());
        c.set(base + "goal-b", arena.goalB() == null ? null : arena.goalB().serialize());
        c.set(base + "goal-radius", arena.goalRadius());
        c.set(base + "build-area", arena.buildArea() == null ? null : arena.buildArea().serialize());
        file.save();
    }

    /**
     * Enables or disables an arena.
     *
     * @param name arena
     * @param enabled state
     * @return whether the arena exists
     */
    public boolean setEnabled(String name, boolean enabled) {
        Arena arena = arenas.get(name.toLowerCase(Locale.ROOT));
        if (arena == null) {
            return false;
        }
        Arena updated = arena.withEnabled(enabled);
        arenas.put(updated.name(), updated);
        writeDefinition(updated);
        return true;
    }

    /**
     * Deletes an arena definition and template file.
     *
     * @param name arena
     * @return whether it existed
     */
    public boolean delete(String name) {
        String id = name.toLowerCase(Locale.ROOT);
        if (arenas.remove(id) == null) {
            return false;
        }
        templates.remove(id);
        file.get().set("arenas." + id, null);
        file.save();
        Deque<ArenaInstance> queue = idle.remove(id);
        if (queue != null) {
            new ArrayList<>(queue).forEach(this::destroy);
        }
        File template = new File(templateFolder, id + ".arena");
        if (template.exists() && !template.delete()) {
            plugin.getLogger().warning("Could not delete " + template);
        }
        return true;
    }

    /** Stops pasting; pending requests fail. Called on disable. */
    public void shutdown() {
        pending.forEach(r -> r.future.completeExceptionally(new NoArenaAvailableException("Shutting down")));
        pending.clear();
        paster.stop();
    }

    /**
     * @param name arena id
     * @return arena or null
     */
    public Arena arena(String name) {
        return name == null ? null : arenas.get(name.toLowerCase(Locale.ROOT));
    }

    /** @return all arena definitions */
    public Collection<Arena> arenas() {
        return arenas.values();
    }

    /**
     * @param name arena id
     * @return template or null
     */
    public ArenaTemplate template(String name) {
        return templates.get(name.toLowerCase(Locale.ROOT));
    }

    /** @return arena world */
    public World world() {
        return world;
    }

    /** @return live instances (any state) */
    public Collection<ArenaInstance> instances() {
        return instances.values();
    }

    /** @return block paster (also used by the editor) */
    public BlockPaster paster() {
        return paster;
    }

    /** @return pending acquire requests */
    public int pendingRequests() {
        return pending.size();
    }

    /** @return idle instance count */
    public int idleCount() {
        return idle.values().stream().mapToInt(Deque::size).sum();
    }

    private record Request(Predicate<Arena> filter, String preferred, CompletableFuture<ArenaInstance> future) {
    }
}
