package net.pvpserver.lobby.world;

import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.arena.ArenaTemplate;
import net.pvpserver.core.arena.BlockPaster;
import net.pvpserver.core.util.LocationUtil;
import net.pvpserver.core.util.Tasks;
import net.pvpserver.core.world.VoidGenerator;
import net.pvpserver.lobby.config.LobbySettings;
import net.pvpserver.lobby.gen.HubBlueprint;
import net.pvpserver.lobby.gen.HubGenerator;
import net.pvpserver.lobby.layout.LobbyLayout;
import net.pvpserver.lobby.layout.Point;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * The lobby world: a dedicated void world that holds the generated hub (or an imported map), with fixed time, no
 * weather or mobs, a world border and the layout's spawn. Rebuilding (regenerate or import) clears exactly the blocks
 * that were pasted before, using the template saved next to the world's marker.
 */
public final class LobbyWorld {

    /** Bumped when the generator changes enough that existing lobbies should be rebuilt. */
    public static final int GENERATOR_VERSION = 1;

    private final JavaPlugin plugin;
    private final PracticeApi api;
    private final LayoutStore layouts;
    private final List<Runnable> listeners = new ArrayList<>();
    private LobbySettings settings;
    private World world;
    private LobbyMarker marker;
    private volatile boolean rebuilding;
    private volatile CompletableFuture<Void> templateWrite = CompletableFuture.completedFuture(null);

    /**
     * @param plugin lobby plugin
     * @param api practice api
     * @param layouts layout.yml
     */
    public LobbyWorld(JavaPlugin plugin, PracticeApi api, LayoutStore layouts) {
        this.plugin = plugin;
        this.api = api;
        this.layouts = layouts;
    }

    /**
     * @param listener run on the main thread after the layout or the world changed (features respawn)
     */
    public void onChange(Runnable listener) {
        listeners.add(listener);
    }

    /** @return lobby world */
    public World world() {
        return world;
    }

    /** @return current layout */
    public LobbyLayout layout() {
        return layouts.get();
    }

    /** @return layout.yml */
    public LayoutStore layouts() {
        return layouts;
    }

    /** @return marker of the current world, or null for custom worlds */
    public LobbyMarker marker() {
        return marker;
    }

    /** @return whether a regenerate or import is running */
    public boolean rebuilding() {
        return rebuilding;
    }

    /** @return spawn location */
    public Location spawn() {
        return layouts.get().spawn().at(world);
    }

    /** @return players below this height are rescued */
    public int voidY() {
        LobbyLayout layout = layouts.get();
        return marker == null && layout.voidY() == 0 ? settings.voidY() : layout.voidY();
    }

    /**
     * Loads or creates the lobby world. A world PvPLobby has not built before gets the generated hub (unless it
     * already existed, in which case it is used as it is).
     *
     * @param newSettings settings
     */
    public void start(LobbySettings newSettings) {
        this.settings = newSettings;
        LobbySettings.WorldSettings ws = newSettings.world();
        File folder = new File(Bukkit.getWorldContainer(), ws.name());
        boolean existed = Bukkit.getWorld(ws.name()) != null || new File(folder, "level.dat").isFile();
        if (ws.mode() == LobbySettings.WorldMode.CUSTOM) {
            world = Bukkit.getWorld(ws.name());
            if (world == null) {
                world = new WorldCreator(ws.name()).createWorld();
            }
            if (world == null) {
                throw new IllegalStateException("Could not load the lobby world " + ws.name());
            }
            marker = LobbyMarker.read(folder);
            loadOrCreateLayout(customSpawn());
        } else {
            world = api.worlds().voidWorld(ws.name(), false);
            folder = folder(world);
            marker = LobbyMarker.read(folder);
            if (marker == null && existed) {
                plugin.getLogger().warning("The world " + ws.name() + " already exists but was not built by PvPLobby; using it as it is. "
                        + "Run /lobby regenerate to build the default hub there, or set world.mode: custom to silence this.");
                loadOrCreateLayout(Point.from(world.getSpawnLocation()));
            } else if (marker == null) {
                long started = System.currentTimeMillis();
                HubBlueprint hub = HubGenerator.generate(ws.seed());
                int[] origin = hub.origin(ws.floorY());
                LobbyMarker generated = new LobbyMarker("generated", String.valueOf(ws.seed()), GENERATOR_VERSION, ws.floorY(), origin);
                installNow(hub.blocks().build(), hub.worldLayout(ws.floorY()), origin, generated);
                plugin.getLogger().info("Built the lobby hub in " + ws.name() + " (seed " + ws.seed() + ") in "
                        + (System.currentTimeMillis() - started) + " ms");
            } else {
                Point fallback = Point.from(world.getSpawnLocation());
                if (!layouts.exists() && marker.generated()) {
                    layouts.replace(HubGenerator.generate(parseSeed(marker.detail(), ws.seed())).worldLayout(marker.floorY()));
                }
                loadOrCreateLayout(fallback);
                if (marker.generated() && (marker.generator() < GENERATOR_VERSION || !marker.detail().equals(String.valueOf(ws.seed()))
                        || marker.floorY() != ws.floorY())) {
                    plugin.getLogger().info("The lobby was built with a different seed, height or generator version than config.yml asks "
                            + "for. Run /lobby regenerate to rebuild it (layout.yml is backed up first).");
                }
            }
        }
        applyWorldSettings();
    }

    /**
     * Applies new settings without rebuilding (reload).
     *
     * @param newSettings settings
     */
    public void reload(LobbySettings newSettings) {
        this.settings = newSettings;
        layouts.load();
        applyWorldSettings();
    }

    private static long parseSeed(String text, long fallback) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void loadOrCreateLayout(Point fallbackSpawn) {
        if (layouts.exists()) {
            layouts.load();
        } else {
            layouts.replace(LobbyLayout.empty().withSpawn(fallbackSpawn));
        }
    }

    private Point customSpawn() {
        Location legacy = LocationUtil.deserialize(settings.legacySpawn());
        if (legacy != null && legacy.getWorld() != null && legacy.getWorld().equals(world)) {
            return Point.from(legacy);
        }
        return Point.from(world.getSpawnLocation().add(0.5, 0, 0.5));
    }

    /** The world's folder (Paper keeps every world in the world container under its name). */
    private static File folder(World world) {
        return new File(Bukkit.getWorldContainer(), world.getName());
    }

    /** Fixed time, no weather, peaceful, world border and spawn from the layout. */
    public void applyWorldSettings() {
        if (settings.world().tune()) {
            api.worlds().tune(world, false);
        }
        world.setTime(settings.world().time());
        world.setStorm(false);
        world.setThundering(false);
        world.setDifficulty(Difficulty.PEACEFUL);
        LobbyLayout layout = layouts.get();
        Location spawn = spawn();
        world.setSpawnLocation(spawn);
        LobbyLayout.Border border = layout.border();
        if (settings.world().border() && border != null) {
            WorldBorder worldBorder = world.getWorldBorder();
            worldBorder.setCenter(border.centerX(), border.centerZ());
            worldBorder.setSize(border.size());
            worldBorder.setWarningDistance(0);
        }
    }

    // ------------------------------------------------------------------ rebuilding

    /**
     * Rebuilds the procedural hub with a seed, replacing whatever the lobby holds now (generated or imported).
     *
     * @param seed seed
     * @return completion
     */
    public CompletableFuture<Void> regenerate(long seed) {
        if (settings.world().mode() == LobbySettings.WorldMode.CUSTOM) {
            return CompletableFuture.failedFuture(new IllegalStateException("world.mode is custom"));
        }
        if (!begin()) {
            return CompletableFuture.failedFuture(new IllegalStateException("the lobby is already being rebuilt"));
        }
        int floorY = settings.world().floorY();
        File folder = folder(world);
        return CompletableFuture.supplyAsync(() -> HubGenerator.generate(seed))
                .thenCompose(hub -> {
                    int[] origin = hub.origin(floorY);
                    LobbyMarker next = new LobbyMarker("generated", String.valueOf(seed), GENERATOR_VERSION, floorY, origin);
                    return install(hub.blocks().build(), hub.worldLayout(floorY), origin, next, previous(folder));
                })
                .whenComplete((v, error) -> finish(error));
    }

    /**
     * Pastes an imported template in place of the current lobby.
     *
     * @param template blocks
     * @param layout layout in world coordinates
     * @param origin paste origin
     * @param next marker describing the import
     * @return completion
     */
    public CompletableFuture<Void> importTemplate(ArenaTemplate template, LobbyLayout layout, int[] origin, LobbyMarker next) {
        if (settings.world().mode() == LobbySettings.WorldMode.CUSTOM) {
            return CompletableFuture.failedFuture(new IllegalStateException("world.mode is custom"));
        }
        if (!begin()) {
            return CompletableFuture.failedFuture(new IllegalStateException("the lobby is already being rebuilt"));
        }
        File folder = folder(world);
        return CompletableFuture.supplyAsync(() -> previous(folder))
                .thenCompose(previous -> install(template, layout, origin, next, previous))
                .whenComplete((v, error) -> finish(error));
    }

    /**
     * Replaces the lobby world with a copy of a world folder, then reads its sign tags.
     *
     * @param source world folder (with level.dat)
     * @param tagReader reads sign tags from the loaded world and returns its layout
     * @return completion
     */
    public CompletableFuture<Void> importWorld(File source, java.util.function.Function<World, LobbyLayout> tagReader) {
        if (settings.world().mode() == LobbySettings.WorldMode.CUSTOM) {
            return CompletableFuture.failedFuture(new IllegalStateException("world.mode is custom"));
        }
        World fallback = Bukkit.getWorlds().get(0);
        if (fallback.equals(world)) {
            return CompletableFuture.failedFuture(new IllegalStateException("the lobby world is the server's main world"));
        }
        if (!begin()) {
            return CompletableFuture.failedFuture(new IllegalStateException("the lobby is already being rebuilt"));
        }
        String name = world.getName();
        List<Player> moved = new ArrayList<>(world.getPlayers());
        moved.forEach(player -> player.teleport(fallback.getSpawnLocation()));
        if (!Bukkit.unloadWorld(world, false)) {
            moved.forEach(player -> player.teleport(spawn()));
            rebuilding = false;
            return CompletableFuture.failedFuture(new IllegalStateException("the lobby world could not be unloaded"));
        }
        File target = new File(Bukkit.getWorldContainer(), name);
        CompletableFuture<Void> done = new CompletableFuture<>();
        Tasks.async(() -> {
            try {
                api.worlds().delete(target);
                copyWorld(source.toPath(), target.toPath());
                new LobbyMarker("world", source.getName(), 0, settings.world().floorY(), null).write(target);
            } catch (IOException e) {
                Tasks.sync(() -> done.completeExceptionally(e));
                return;
            }
            Tasks.sync(() -> {
                try {
                    world = new WorldCreator(name).generator(new VoidGenerator()).generateStructures(false).createWorld();
                    if (world == null) {
                        throw new IllegalStateException("the copied world did not load");
                    }
                    marker = LobbyMarker.read(folder(world));
                    layouts.replace(tagReader.apply(world));
                    applyWorldSettings();
                    done.complete(null);
                } catch (RuntimeException e) {
                    done.completeExceptionally(e);
                }
            });
        });
        return done.whenComplete((v, error) -> {
            if (error != null) {
                recover(name);
            }
            moved.stream().filter(Player::isOnline).forEach(player -> player.teleport(spawn()));
            finish(error);
        });
    }

    /** After a failed world import: load whatever is left, rebuilding the default hub if nothing usable is. */
    private void recover(String name) {
        world = api.worlds().voidWorld(name, false);
        marker = LobbyMarker.read(folder(world));
        if (marker == null) {
            LobbySettings.WorldSettings ws = settings.world();
            HubBlueprint hub = HubGenerator.generate(ws.seed());
            int[] origin = hub.origin(ws.floorY());
            installNow(hub.blocks().build(), hub.worldLayout(ws.floorY()), origin,
                    new LobbyMarker("generated", String.valueOf(ws.seed()), GENERATOR_VERSION, ws.floorY(), origin));
        } else {
            applyWorldSettings();
        }
    }

    private boolean begin() {
        if (rebuilding) {
            return false;
        }
        rebuilding = true;
        for (Player player : world.getPlayers()) {
            player.setAllowFlight(true);
            player.setFlying(true);
        }
        return true;
    }

    private void finish(Throwable error) {
        Tasks.sync(() -> {
            rebuilding = false;
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Rebuilding the lobby failed", error);
            }
            listeners.forEach(Runnable::run);
        });
    }

    /** Previously pasted blocks and where, or null. Reads from disk: call off the main thread. */
    private Previous previous(File folder) {
        // The last paste may still be saving its blocks.
        templateWrite.join();
        LobbyMarker current = LobbyMarker.read(folder);
        File file = new File(folder, LobbyMarker.TEMPLATE);
        if (current == null || current.origin() == null || !file.isFile()) {
            return null;
        }
        try {
            return new Previous(ArenaTemplate.read(file), current.origin());
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read the previous lobby blocks (" + e.getMessage() + "); pasting over them");
            return null;
        }
    }

    private record Previous(ArenaTemplate template, int[] origin) {
    }

    private CompletableFuture<Void> install(ArenaTemplate template, LobbyLayout layout, int[] origin, LobbyMarker next, Previous previous) {
        CompletableFuture<Void> done = new CompletableFuture<>();
        Tasks.sync(() -> {
            BlockPaster paster = api.arenas().paster();
            CompletableFuture<Void> cleared = previous == null ? CompletableFuture.completedFuture(null)
                    : paster.clear(previous.template(), world, previous.origin()[0], previous.origin()[1], previous.origin()[2]);
            cleared.thenCompose(v -> paster.paste(template, world, origin[0], origin[1], origin[2]))
                    .whenComplete((v, error) -> Tasks.sync(() -> {
                        if (error != null) {
                            done.completeExceptionally(error);
                            return;
                        }
                        commit(template, layout, next);
                        for (Player player : world.getPlayers()) {
                            player.teleport(spawn());
                            player.setFlying(false);
                        }
                        done.complete(null);
                    }));
        });
        return done;
    }

    /** Synchronous install for the first start (no players yet). */
    private void installNow(ArenaTemplate template, LobbyLayout layout, int[] origin, LobbyMarker next) {
        BlockData[] palette = template.parsedPalette();
        short[] blocks = template.blocks();
        for (int index : template.solidIndices()) {
            world.getBlockAt(origin[0] + template.xOf(index), origin[1] + template.yOf(index), origin[2] + template.zOf(index))
                    .setBlockData(palette[blocks[index]], false);
        }
        commit(template, layout, next);
    }

    private void commit(ArenaTemplate template, LobbyLayout layout, LobbyMarker next) {
        File folder = folder(world);
        marker = next;
        layouts.replace(layout);
        applyWorldSettings();
        try {
            next.write(folder);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write " + LobbyMarker.FILE, e);
        }
        CompletableFuture<Void> written = new CompletableFuture<>();
        templateWrite = written;
        Tasks.async(() -> {
            try {
                template.write(new File(folder, LobbyMarker.TEMPLATE));
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not save the lobby blocks", e);
            } finally {
                written.complete(null);
            }
        });
    }

    /** Copies a world folder, skipping the lock and the UUID file (a copy must get its own world UUID). */
    static void copyWorld(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                if (!name.equals("uid.dat") && !name.equals("session.lock")) {
                    Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Runs an action for every player in the lobby world.
     *
     * @param action action
     */
    public void forPlayers(Consumer<Player> action) {
        world.getPlayers().forEach(action);
    }
}
