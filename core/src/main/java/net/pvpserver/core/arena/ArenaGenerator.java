package net.pvpserver.core.arena;

import net.pvpserver.core.arena.gen.BuiltinArenas;
import net.pvpserver.core.arena.gen.GeneratedArena;
import net.pvpserver.core.config.ConfigFile;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Installs the built-in arena set (see {@link BuiltinArenas}) into the template folder and arenas.yml.
 * <p>
 * {@code arenas/generated.yml} records which built-ins were installed, so an arena an admin deleted is not recreated
 * on the next start, while arenas added in a plugin update are. Arenas an admin created with the same name are never
 * overwritten. Servers upgrading from the first version get their four placeholder arenas disabled (not deleted).
 */
final class ArenaGenerator {

    static final String MARKER = "generated.yml";
    private static final Set<String> V1_PLACEHOLDERS = Set.of("classic", "nether", "sumo", "bridge");

    private ArenaGenerator() {
    }

    /**
     * Generates every built-in arena that was never installed. Runs on start before definitions load.
     *
     * @param logger logger
     * @param folder template folder
     * @param arenasFile arenas.yml
     * @return number of arenas generated
     */
    static int installMissing(Logger logger, File folder, ConfigFile arenasFile) {
        folder.mkdirs();
        File markerFile = new File(folder, MARKER);
        boolean firstRun = !markerFile.exists();
        YamlConfiguration marker = YamlConfiguration.loadConfiguration(markerFile);
        Set<String> installed = new LinkedHashSet<>(marker.getStringList("installed"));
        if (firstRun) {
            disableV1Placeholders(logger, folder, arenasFile);
        }
        long start = System.currentTimeMillis();
        List<String> generated = new ArrayList<>();
        for (String name : BuiltinArenas.names()) {
            if (installed.contains(name)) {
                continue;
            }
            installed.add(name);
            if (arenasFile.get().isConfigurationSection("arenas." + name)) {
                logger.info("Built-in arena '" + name + "' skipped: an arena with that name already exists");
                continue;
            }
            try {
                write(BuiltinArenas.generate(name), folder, arenasFile);
                generated.add(name);
            } catch (IOException | RuntimeException e) {
                logger.severe("Could not generate arena " + name + ": " + e.getMessage());
            }
        }
        if (!generated.isEmpty()) {
            arenasFile.save();
            logger.info("Generated " + generated.size() + " built-in arenas in " + (System.currentTimeMillis() - start) + " ms: "
                    + String.join(", ", generated));
        }
        marker.set("generator-version", BuiltinArenas.VERSION);
        marker.set("installed", new ArrayList<>(installed));
        try {
            marker.save(markerFile);
        } catch (IOException e) {
            logger.warning("Could not write " + markerFile + ": " + e.getMessage());
        }
        return generated.size();
    }

    /**
     * Writes one generated arena's template file and definition (does not save arenas.yml).
     */
    static void write(GeneratedArena generated, File folder, ConfigFile arenasFile) throws IOException {
        generated.template().write(new File(folder, generated.name() + ".arena"));
        Arena arena = generated.toArena();
        String base = "arenas." + arena.name() + ".";
        var c = arenasFile.get();
        c.set("arenas." + arena.name(), null);
        c.set(base + "display-name", arena.displayName());
        c.set(base + "icon", arena.icon().name());
        c.set(base + "enabled", true);
        c.set(base + "tags", new ArrayList<>(generated.tags()));
        c.set(base + "spawn-a", arena.spawnA().serialize());
        c.set(base + "spawn-b", arena.spawnB().serialize());
        c.set(base + "spectator", arena.spectator() == null ? null : arena.spectator().serialize());
        c.set(base + "build-limit", arena.buildLimit());
        c.set(base + "void-y", arena.voidY());
        if (arena.goalA() != null) {
            c.set(base + "goal-a", arena.goalA().serialize());
            c.set(base + "goal-b", arena.goalB().serialize());
            c.set(base + "goal-radius", arena.goalRadius());
        }
        if (arena.buildArea() != null) {
            c.set(base + "build-area", arena.buildArea().serialize());
        }
    }

    private static void disableV1Placeholders(Logger logger, File folder, ConfigFile arenasFile) {
        List<String> disabled = new ArrayList<>();
        for (String name : V1_PLACEHOLDERS) {
            String path = "arenas." + name;
            if (arenasFile.get().isConfigurationSection(path) && new File(folder, name + ".arena").exists()
                    && arenasFile.get().getBoolean(path + ".enabled", true)) {
                arenasFile.get().set(path + ".enabled", false);
                disabled.add(name);
            }
        }
        if (!disabled.isEmpty()) {
            arenasFile.save();
            logger.warning("Disabled the old placeholder arenas " + disabled + " in favour of the new built-in set. "
                    + "Re-enable them with /arena enable <name> or remove them with /arena delete <name>.");
        }
    }
}
