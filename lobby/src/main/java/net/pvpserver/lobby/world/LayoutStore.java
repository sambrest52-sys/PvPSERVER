package net.pvpserver.lobby.world;

import net.pvpserver.lobby.layout.LayoutParser;
import net.pvpserver.lobby.layout.LobbyLayout;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * layout.yml: where everything in the lobby is. Written when the lobby is generated or imported (the previous file is
 * kept as layout.yml.bak), edited by hand or with /lobby set, re-read on reload.
 */
public final class LayoutStore {

    private static final List<String> HEADER = List.of(
            "PvPLobby layout: where everything in the lobby is. Coordinates are in the lobby world.",
            "Positions are \"x y z\" or \"x y z yaw pitch\". Reload with /lobby reload after editing.",
            "Written when the lobby is generated or imported (the previous file is kept as layout.yml.bak).",
            "In game: /lobby set spawn | /lobby set npc <id> | /lobby set hologram <id> | /lobby set wall",
            "",
            "spawn             where players appear",
            "void-y            players below this height are sent back to spawn",
            "border            world border centre (\"x z\") and size",
            "npcs              NPC positions by id (looks, texts and actions are in npcs.yml)",
            "holograms         floating texts by id (texts in messages.yml displays.<id>; parkour shows best times)",
            "portals           walk-in boxes (from/to corners) running an action (same list as npcs.yml)",
            "launch-pads       plate position and launch velocity \"x y z\" (blocks per tick)",
            "buttons           blocks that run an action when right-clicked",
            "parkour           start plate, checkpoint plates in order, finish plate, fall-y",
            "eggs              hidden egg blocks by id (found by right-clicking)",
            "zones             named circles (\"x z\" centre, radius) announced when entered",
            "emitters          ambient particles by type (types are in config.yml ambient.emitters)",
            "leaderboard-wall  top-left panel position and facing, columns and spacing");

    private final JavaPlugin plugin;
    private final File file;
    private LobbyLayout layout = LobbyLayout.empty();

    /**
     * @param plugin lobby plugin
     */
    public LayoutStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "layout.yml");
    }

    /** @return current layout */
    public LobbyLayout get() {
        return layout;
    }

    /** @return whether layout.yml exists */
    public boolean exists() {
        return file.isFile();
    }

    /** @return layout.yml */
    public File file() {
        return file;
    }

    /**
     * Re-reads layout.yml, logging skipped entries.
     *
     * @return warnings
     */
    public List<String> load() {
        List<String> warnings = new ArrayList<>();
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            if (file.isFile()) {
                yaml.load(file);
            }
        } catch (Exception e) {
            warnings.add("layout.yml is not valid YAML (" + e.getMessage() + "), keeping the previous layout");
            warnings.forEach(w -> plugin.getLogger().warning(w));
            return warnings;
        }
        layout = LayoutParser.read(yaml, warnings);
        warnings.forEach(w -> plugin.getLogger().warning("layout.yml: " + w));
        return warnings;
    }

    /**
     * Saves the layout (keeps edits by commands such as /lobby set).
     *
     * @param newLayout layout
     */
    public void save(LobbyLayout newLayout) {
        layout = newLayout;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(HEADER);
        LayoutParser.write(newLayout, yaml);
        try {
            file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save layout.yml", e);
        }
    }

    /**
     * Replaces layout.yml with a new layout, keeping the old file as layout.yml.bak.
     *
     * @param newLayout layout
     */
    public void replace(LobbyLayout newLayout) {
        if (file.isFile()) {
            try {
                Files.copy(file.toPath(), new File(file.getParentFile(), "layout.yml.bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not back up layout.yml: " + e.getMessage());
            }
        }
        save(newLayout);
    }
}
