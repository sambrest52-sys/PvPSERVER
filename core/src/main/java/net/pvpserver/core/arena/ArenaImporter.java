package net.pvpserver.core.arena;

import net.pvpserver.core.arena.io.SchematicReader;
import net.pvpserver.core.arena.io.SpawnFinder;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.util.Tasks;
import net.pvpserver.core.world.VoidGenerator;
import net.pvpserver.core.world.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Imports arenas from {@code plugins/PvPCore/imports/}:
 * <ul>
 *   <li>{@code /arena import <file> [name] [save]}: a {@code .schem} (Sponge v1-3), legacy {@code .schematic} or
 *       {@code .arena} file is pasted into the editor world and opened as an edit session with spawns taken from
 *       marker signs ({@code [A]}, {@code [B]}, {@code [Spectator]}, {@code [Goal A]}, {@code [Goal B]}) or guessed
 *       from the terrain; {@code /arena save} (or the {@code save} flag) puts it into the arena pool.</li>
 *   <li>{@code /arena importworld <folder>}: a world folder is copied into the server and loaded (void for
 *       unexplored chunks) so an arena can be selected and saved with the normal editor.</li>
 * </ul>
 */
public final class ArenaImporter {

    private static final Set<String> EXTENSIONS = Set.of(".schem", ".schematic", ".arena");

    private final JavaPlugin plugin;
    private final ArenaService arenas;
    private final ArenaEditor editor;
    private final WorldService worlds;
    private final MessageService messages;
    private final File folder;

    /**
     * @param plugin core plugin
     * @param arenas arena service
     * @param editor editor
     * @param worlds world service
     * @param messages messages
     */
    public ArenaImporter(JavaPlugin plugin, ArenaService arenas, ArenaEditor editor, WorldService worlds, MessageService messages) {
        this.plugin = plugin;
        this.arenas = arenas;
        this.editor = editor;
        this.worlds = worlds;
        this.messages = messages;
        this.folder = new File(plugin.getDataFolder(), "imports");
        folder.mkdirs();
        File readme = new File(folder, "README.txt");
        if (!readme.exists()) {
            try {
                Files.writeString(readme.toPath(), """
                        Drop arenas to import here, then run the command in game (see the README "Importing arenas"):
                          *.schem       Sponge schematics (WorldEdit 7+, FAWE)      /arena import <file> [name] [save]
                          *.schematic   legacy MCEdit/WorldEdit 1.8-1.12 schematics  /arena import <file> [name] [save]
                          *.arena       PvPCore templates                           /arena import <file> [name] [save]
                          <folder>/     a whole world folder (with level.dat)       /arena importworld <folder>
                        Mark spawns with standing signs reading [A] and [B] (optional: [Spectator], [Goal A], [Goal B]).
                        """, StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not write " + readme + ": " + e.getMessage());
            }
        }
    }

    /** @return the import folder */
    public File folder() {
        return folder;
    }

    /** @return importable file and folder names */
    public List<String> candidates() {
        List<String> names = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                String lower = file.getName().toLowerCase(Locale.ROOT);
                if (file.isDirectory() && new File(file, "level.dat").exists()) {
                    names.add(file.getName() + "/");
                } else if (EXTENSIONS.stream().anyMatch(lower::endsWith)) {
                    names.add(file.getName());
                }
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /**
     * @param sender sender
     */
    public void list(CommandSender sender) {
        List<String> names = candidates();
        messages.send(sender, names.isEmpty() ? "arena.import-empty" : "arena.import-list",
                MessageService.p("files", String.join(", ", names)));
    }

    private File resolve(String name) {
        if (name.contains("..") || name.contains("/") || name.contains("\\") || name.contains(File.separator)) {
            return null;
        }
        File exact = new File(folder, name);
        if (exact.isFile()) {
            return exact;
        }
        for (String extension : EXTENSIONS) {
            File candidate = new File(folder, name + extension);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    private static String baseName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String id = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        return id.length() > 32 ? id.substring(0, 32) : id;
    }

    /**
     * Imports a schematic into the editor.
     *
     * @param player admin
     * @param fileName file in the import folder (extension optional)
     * @param requestedName arena name or null for the file name
     * @param save save immediately when spawns were found
     */
    public void importSchematic(Player player, String fileName, String requestedName, boolean save) {
        File file = resolve(fileName);
        if (file == null) {
            messages.send(player, "arena.import-not-found", MessageService.p("file", fileName));
            return;
        }
        String name = requestedName == null ? baseName(file) : requestedName.toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]{1,32}")) {
            messages.send(player, "arena.invalid-name");
            return;
        }
        if (arenas.arena(name) != null) {
            messages.send(player, "arena.exists", MessageService.p("arena", name));
            return;
        }
        messages.send(player, "arena.import-reading", MessageService.p("file", file.getName()));
        CompletableFuture.supplyAsync(() -> {
            try {
                if (file.getName().toLowerCase(Locale.ROOT).endsWith(".arena")) {
                    return new SchematicReader.Result(TemplateBuilder.from(ArenaTemplate.read(file)), Map.of(), "PvPCore template", false, List.of());
                }
                return SchematicReader.read(file);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((result, error) -> Tasks.sync(() -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                messages.send(player, "arena.import-failed", MessageService.p("file", file.getName()), MessageService.p("reason", String.valueOf(cause.getMessage())));
                return;
            }
            if (!player.isOnline()) {
                return;
            }
            open(player, name, result, save);
        }));
    }

    private void open(Player player, String name, SchematicReader.Result result, boolean save) {
        TemplateBuilder blocks = result.blocks();
        if (result.legacy()) {
            blocks.remapPalette(net.pvpserver.core.arena.io.LegacyBlocks.resolver());
        }
        Map<SchematicReader.Marker, SchematicReader.Position> markers = result.markers();
        RelativePosition spawnA = marker(markers.get(SchematicReader.Marker.SPAWN_A));
        RelativePosition spawnB = marker(markers.get(SchematicReader.Marker.SPAWN_B));
        boolean guessed = false;
        if (spawnA == null || spawnB == null) {
            int[][] spawns = SpawnFinder.guessSpawns(blocks);
            if (spawns != null) {
                spawnA = spawnA != null ? spawnA : new RelativePosition(spawns[0][0] + 0.5, spawns[0][1], spawns[0][2] + 0.5, 0f, 0f);
                spawnB = spawnB != null ? spawnB : new RelativePosition(spawns[1][0] + 0.5, spawns[1][1], spawns[1][2] + 0.5, 0f, 0f);
                guessed = true;
            }
        }
        if (spawnA != null && spawnB != null) {
            // Face each other.
            float yawA = (float) Math.toDegrees(Math.atan2(-(spawnB.x() - spawnA.x()), spawnB.z() - spawnA.z()));
            spawnA = new RelativePosition(spawnA.x(), spawnA.y(), spawnA.z(), yawA, 0f);
            spawnB = new RelativePosition(spawnB.x(), spawnB.y(), spawnB.z(), yawA + 180f, 0f);
        }
        ArenaTemplate template = blocks.build();
        Arena definition = new Arena(name, "<white>" + name, Material.PAPER, true, Set.of("standard"), spawnA, spawnB,
                marker(markers.get(SchematicReader.Marker.SPECTATOR)), template.sizeY() + 6, -6,
                marker(markers.get(SchematicReader.Marker.GOAL_A)), marker(markers.get(SchematicReader.Marker.GOAL_B)), 1.6, null);
        boolean spawnsFound = spawnA != null && spawnB != null;
        boolean fromMarkers = spawnsFound && !guessed;
        editor.open(player, definition, template, session -> {
            messages.send(player, "arena.imported", MessageService.p("arena", name), MessageService.p("format", result.format()),
                    MessageService.p("size", template.sizeX() + "x" + template.sizeY() + "x" + template.sizeZ()),
                    MessageService.p("spawns", !spawnsFound ? "not found" : fromMarkers ? "from [A]/[B] signs" : "guessed from the terrain"));
            if (!spawnsFound) {
                messages.send(player, "arena.imported-no-spawns");
            } else if (save) {
                editor.save(player);
            }
        });
    }

    private static RelativePosition marker(SchematicReader.Position position) {
        return position == null ? null : new RelativePosition(position.x() + 0.5, position.y(), position.z() + 0.5, 0f, 0f);
    }

    /**
     * Copies (if needed) and loads a world folder from the import folder, then teleports the admin there.
     *
     * @param player admin
     * @param folderName world folder in the import folder
     */
    public void importWorld(Player player, String folderName) {
        if (folderName.contains("..") || folderName.contains("/") || folderName.contains("\\")) {
            messages.send(player, "arena.import-not-found", MessageService.p("file", folderName));
            return;
        }
        File source = new File(folder, folderName);
        if (!new File(source, "level.dat").exists()) {
            messages.send(player, source.isDirectory() ? "arena.world-invalid" : "arena.import-not-found",
                    MessageService.p("file", folderName));
            return;
        }
        String worldName = "import_" + folderName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        World loaded = Bukkit.getWorld(worldName);
        if (loaded != null) {
            teleportInto(player, loaded);
            return;
        }
        File target = new File(Bukkit.getWorldContainer(), worldName);
        messages.send(player, "arena.world-loading", MessageService.p("world", worldName));
        CompletableFuture.runAsync(() -> {
            if (target.exists()) {
                return;
            }
            try {
                copyWorld(source.toPath(), target.toPath());
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }).whenComplete((v, error) -> Tasks.sync(() -> {
            if (error != null) {
                messages.send(player, "arena.import-failed", MessageService.p("file", folderName),
                        MessageService.p("reason", String.valueOf(error.getCause() == null ? error.getMessage() : error.getCause().getMessage())));
                return;
            }
            World world = new WorldCreator(worldName).generator(new VoidGenerator()).generateStructures(false).createWorld();
            if (world == null) {
                messages.send(player, "arena.import-failed", MessageService.p("file", folderName), MessageService.p("reason", "the world did not load"));
                return;
            }
            worlds.tune(world, true);
            if (player.isOnline()) {
                teleportInto(player, world);
            }
        }));
    }

    private void teleportInto(Player player, World world) {
        player.teleport(world.getSpawnLocation());
        messages.send(player, "arena.world-loaded", MessageService.p("world", world.getName()));
    }

    /** Copies a world folder, skipping the lock and the UUID file (a copy must get its own world UUID). */
    private static void copyWorld(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                if (!name.equals("session.lock") && !name.equals("uid.dat")) {
                    Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
