package net.pvpserver.lobby.world;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.pvpserver.core.arena.TemplateBuilder;
import net.pvpserver.core.arena.io.LegacyBlocks;
import net.pvpserver.core.arena.io.SchematicReader;
import net.pvpserver.core.arena.io.SpawnFinder;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.util.Tasks;
import net.pvpserver.lobby.PvPLobby;
import net.pvpserver.lobby.layout.LobbyLayout;
import net.pvpserver.lobby.layout.Point;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /lobby import: schematics and world folders from plugins/PvPLobby/imports, with positions read from [tag] signs.
 */
public final class LobbyImporter {

    private static final Set<String> EXTENSIONS = Set.of(".schem", ".schematic");
    private static final Pattern TAG = Pattern.compile("\\[\\s*([a-z0-9][a-z0-9 ]*?)\\s*]");
    private static final int SCAN_RADIUS_CHUNKS = 10;

    private final PvPLobby plugin;
    private final LobbyWorld lobbyWorld;
    private final File folder;

    /**
     * @param plugin lobby plugin
     * @param lobbyWorld lobby world
     */
    public LobbyImporter(PvPLobby plugin, LobbyWorld lobbyWorld) {
        this.plugin = plugin;
        this.lobbyWorld = lobbyWorld;
        this.folder = new File(plugin.getDataFolder(), "imports");
        folder.mkdirs();
        if (!new File(folder, "README.txt").exists()) {
            plugin.saveResource("imports/README.txt", false);
        }
    }

    /** @return import folder */
    public File folder() {
        return folder;
    }

    /** @return schematic files and world folders that can be imported */
    public List<String> candidates() {
        List<String> names = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) {
            return names;
        }
        for (File file : files) {
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (file.isDirectory() && new File(file, "level.dat").isFile()) {
                names.add(file.getName());
            } else if (EXTENSIONS.stream().anyMatch(lower::endsWith)) {
                names.add(file.getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private File resolve(String name) {
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            return null;
        }
        File exact = new File(folder, name);
        if (exact.exists()) {
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

    /**
     * Imports a schematic or world folder, reporting progress to the sender.
     *
     * @param sender command sender
     * @param name file or folder name (extension optional)
     */
    public void importLobby(CommandSender sender, String name) {
        MessageService m = plugin.messages();
        File file = resolve(name);
        if (file == null || (file.isDirectory() && !new File(file, "level.dat").isFile())) {
            m.send(sender, "lobby.import.not-found", MessageService.p("file", name), MessageService.p("folder", "plugins/PvPLobby/imports"));
            return;
        }
        if (lobbyWorld.rebuilding()) {
            m.send(sender, "lobby.busy");
            return;
        }
        m.send(sender, "lobby.import.reading", MessageService.p("file", file.getName()));
        if (file.isDirectory()) {
            List<String> notes = new ArrayList<>();
            lobbyWorld.importWorld(file, world -> readWorldTags(world, notes))
                    .whenComplete((v, error) -> Tasks.sync(() -> report(sender, file.getName(), error, notes)));
            return;
        }
        Tasks.async(() -> {
            SchematicReader.Result read;
            try {
                read = SchematicReader.read(file);
            } catch (IOException | RuntimeException e) {
                Tasks.sync(() -> m.send(sender, "lobby.import.failed", MessageService.p("file", file.getName()),
                        MessageService.p("reason", String.valueOf(e.getMessage()))));
                return;
            }
            SchematicReader.Result result = read;
            Tasks.sync(() -> importSchematic(sender, file, result));
        });
    }

    private void importSchematic(CommandSender sender, File file, SchematicReader.Result result) {
        TemplateBuilder b = result.blocks();
        if (result.legacy()) {
            b.remapPalette(LegacyBlocks.resolver());
        }
        List<TagLayout.SignTag> tags = new ArrayList<>();
        for (SchematicReader.Tag tag : result.tags()) {
            SchematicReader.Position p = tag.position();
            if (!b.isAir(p.x(), p.y(), p.z())) {
                tags.add(new TagLayout.SignTag(tag.text(), p.x(), p.y(), p.z()));
            }
        }
        int cx = b.sizeX() / 2;
        int cz = b.sizeZ() / 2;
        int[] feet = SpawnFinder.near(b, cx, cz, Math.max(b.sizeX(), b.sizeZ()) / 2);
        Point fallback = feet == null ? Point.of(cx + 0.5, b.sizeY(), cz + 0.5) : Point.of(feet[0] + 0.5, feet[1], feet[2] + 0.5);
        int lowest = lowestBlock(b);
        LobbyLayout.Border border = new LobbyLayout.Border(b.sizeX() / 2.0, b.sizeZ() / 2.0, Math.max(b.sizeX(), b.sizeZ()) + 32);
        TagLayout.Result tagged = TagLayout.build(new TagLayout.Blocks() {
            @Override
            public String get(int x, int y, int z) {
                return b.get(x, y, z);
            }

            @Override
            public void set(int x, int y, int z, String block) {
                b.set(x, y, z, block);
            }
        }, tags, fallback, lowest - 6, border);

        World world = lobbyWorld.world();
        int floorY = plugin.settings().world().floorY();
        int oy = floorY + 1 - (int) Math.floor(tagged.layout().spawn().y());
        oy = Math.max(world.getMinHeight(), Math.min(oy, world.getMaxHeight() - b.sizeY()));
        int[] origin = {-b.sizeX() / 2, oy, -b.sizeZ() / 2};
        LobbyLayout layout = tagged.layout().translate(origin[0], origin[1], origin[2]).rounded();
        LobbyMarker marker = new LobbyMarker("schematic", file.getName(), 0, floorY, origin);
        List<String> notes = new ArrayList<>(tagged.notes());
        notes.add(0, result.format() + ", " + b.sizeX() + "x" + b.sizeY() + "x" + b.sizeZ() + ", " + tagged.used() + " tags");
        lobbyWorld.importTemplate(b.build(), layout, origin, marker)
                .whenComplete((v, error) -> Tasks.sync(() -> report(sender, file.getName(), error, notes)));
    }

    private static int lowestBlock(TemplateBuilder b) {
        for (int y = 0; y < b.sizeY(); y++) {
            for (int x = 0; x < b.sizeX(); x++) {
                for (int z = 0; z < b.sizeZ(); z++) {
                    if (!b.isAir(x, y, z)) {
                        return y;
                    }
                }
            }
        }
        return 0;
    }

    /** Reads [tag] signs in the chunks around the spawn of an imported world. */
    private LobbyLayout readWorldTags(World world, List<String> notes) {
        List<TagLayout.SignTag> tags = new ArrayList<>();
        int centerX = world.getSpawnLocation().getBlockX() >> 4;
        int centerZ = world.getSpawnLocation().getBlockZ() >> 4;
        try {
            for (int cx = centerX - SCAN_RADIUS_CHUNKS; cx <= centerX + SCAN_RADIUS_CHUNKS; cx++) {
                for (int cz = centerZ - SCAN_RADIUS_CHUNKS; cz <= centerZ + SCAN_RADIUS_CHUNKS; cz++) {
                    Chunk chunk = world.getChunkAt(cx, cz);
                    for (BlockState state : chunk.getTileEntities()) {
                        if (state instanceof Sign sign) {
                            String text = (plain(sign, Side.FRONT) + " " + plain(sign, Side.BACK)).toLowerCase(Locale.ROOT);
                            Matcher matcher = TAG.matcher(text);
                            while (matcher.find()) {
                                tags.add(new TagLayout.SignTag(matcher.group(1).trim(), state.getX(), state.getY(), state.getZ()));
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            notes.add("could not read signs in this world (" + e.getClass().getSimpleName() + "); set positions with /lobby set");
        }
        Point spawn = Point.from(world.getSpawnLocation().add(0.5, 0, 0.5));
        TagLayout.Result result = TagLayout.build(new TagLayout.Blocks() {
            @Override
            public String get(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getBlockData().getAsString();
            }

            @Override
            public void set(int x, int y, int z, String block) {
                world.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(block), false);
            }
        }, tags, spawn, world.getMinHeight() + 2, null);
        notes.addAll(result.notes());
        notes.add(0, "world folder, " + result.used() + " tags");
        return result.layout().rounded();
    }

    private static String plain(Sign sign, Side side) {
        StringBuilder text = new StringBuilder();
        for (Component line : sign.getSide(side).lines()) {
            text.append(PlainTextComponentSerializer.plainText().serialize(line)).append(' ');
        }
        return text.toString();
    }

    private void report(CommandSender sender, String file, Throwable error, List<String> notes) {
        MessageService m = plugin.messages();
        if (error != null) {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            m.send(sender, "lobby.import.failed", MessageService.p("file", file), MessageService.p("reason", String.valueOf(cause.getMessage())));
            return;
        }
        LobbyLayout layout = lobbyWorld.layout();
        m.send(sender, "lobby.import.done", MessageService.p("file", file));
        for (String note : notes) {
            m.send(sender, "lobby.import.note", MessageService.p("note", note));
        }
        List<String> missing = new ArrayList<>();
        for (String id : plugin.npcDefinitions().keySet()) {
            if (!layout.npcs().containsKey(id)) {
                missing.add(id);
            }
        }
        if (!missing.isEmpty()) {
            m.send(sender, "lobby.import.missing-npcs", MessageService.p("npcs", String.join(", ", missing)));
        }
    }
}
