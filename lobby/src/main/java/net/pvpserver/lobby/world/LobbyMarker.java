package net.pvpserver.lobby.world;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

/**
 * pvplobby.yml inside the lobby world folder: what built the world (generator and seed, or which import) and where
 * its blocks were pasted, so a regenerate or import can clear exactly what is there.
 *
 * @param source generated, schematic or world
 * @param detail seed for generated lobbies, file or folder name for imports
 * @param generator generator version (generated lobbies)
 * @param floorY floor height used
 * @param origin paste origin (null for world imports)
 */
public record LobbyMarker(String source, String detail, int generator, int floorY, int[] origin) {

    /** File name inside the world folder. */
    public static final String FILE = "pvplobby.yml";
    /** Blocks that were pasted, used to clear them before the next paste. */
    public static final String TEMPLATE = "pvplobby.template";

    /**
     * @param worldFolder world folder
     * @return marker or null when the world was not made by PvPLobby
     */
    public static LobbyMarker read(File worldFolder) {
        File file = new File(worldFolder, FILE);
        if (!file.isFile()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int[] origin = null;
        if (yaml.isList("origin")) {
            var list = yaml.getIntegerList("origin");
            if (list.size() == 3) {
                origin = new int[]{list.get(0), list.get(1), list.get(2)};
            }
        }
        return new LobbyMarker(yaml.getString("source", "generated"), yaml.getString("detail", ""), yaml.getInt("generator", 0),
                yaml.getInt("floor-y", 64), origin);
    }

    /**
     * @param worldFolder world folder
     * @throws IOException on write failure
     */
    public void write(File worldFolder) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(java.util.List.of("Written by PvPLobby. Deleting this file makes the lobby world count as a custom world."));
        yaml.set("source", source);
        yaml.set("detail", detail);
        yaml.set("generator", generator);
        yaml.set("floor-y", floorY);
        if (origin != null) {
            yaml.set("origin", java.util.List.of(origin[0], origin[1], origin[2]));
        }
        yaml.set("written", Instant.now().toString());
        worldFolder.mkdirs();
        yaml.save(new File(worldFolder, FILE));
    }

    /** @return true for the procedural hub */
    public boolean generated() {
        return source.equals("generated");
    }
}
