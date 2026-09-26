package net.pvpserver.lobby.feature;

import net.pvpserver.core.util.Tasks;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Lobby progress kept in plugins/PvPLobby/data.yml: parkour best times and found eggs. Everything is served from
 * memory; the file is written in the background at most every few seconds and synchronously on shutdown.
 */
public final class LobbyData {

    /**
     * A parkour best time.
     *
     * @param uuid player
     * @param name last known name
     * @param millis time
     */
    public record Time(UUID uuid, String name, long millis) {
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Time> times = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> eggs = new ConcurrentHashMap<>();
    private volatile List<Time> top = List.of();
    private volatile boolean dirty;
    private final Object writeLock = new Object();

    /**
     * @param plugin lobby plugin
     */
    public LobbyData(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    /** Starts the background autosave (once). */
    public void startAutosave() {
        Tasks.asyncTimer(this::flushIfDirty, 100L, 100L);
    }

    /** Loads data.yml (small; done on the main thread at startup). */
    public void load() {
        times.clear();
        eggs.clear();
        if (file.isFile()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection parkour = yaml.getConfigurationSection("parkour");
            if (parkour != null) {
                for (String key : parkour.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        times.put(uuid, new Time(uuid, parkour.getString(key + ".name", "?"), parkour.getLong(key + ".millis")));
                    } catch (IllegalArgumentException ignored) {
                        // not a player entry
                    }
                }
            }
            ConfigurationSection found = yaml.getConfigurationSection("eggs");
            if (found != null) {
                for (String key : found.getKeys(false)) {
                    try {
                        Set<String> ids = ConcurrentHashMap.newKeySet();
                        ids.addAll(found.getStringList(key));
                        eggs.put(UUID.fromString(key), ids);
                    } catch (IllegalArgumentException ignored) {
                        // not a player entry
                    }
                }
            }
        }
        rebuildTop();
    }

    private void rebuildTop() {
        List<Time> sorted = new ArrayList<>(times.values());
        sorted.sort(Comparator.comparingLong(Time::millis).thenComparing(Time::name));
        top = Collections.unmodifiableList(sorted);
    }

    /**
     * @param uuid player
     * @return best time or null
     */
    public Time best(UUID uuid) {
        return times.get(uuid);
    }

    /**
     * @param limit entries
     * @return fastest times
     */
    public List<Time> top(int limit) {
        List<Time> current = top;
        return current.subList(0, Math.min(limit, current.size()));
    }

    /**
     * @param uuid player
     * @return 1-based rank, or 0 without a time
     */
    public int rank(UUID uuid) {
        List<Time> current = top;
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).uuid().equals(uuid)) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * Records a finished run.
     *
     * @param uuid player
     * @param name name
     * @param millis time
     * @return the previous best (null for a first finish)
     */
    public Time record(UUID uuid, String name, long millis) {
        Time previous = times.get(uuid);
        if (previous == null || millis < previous.millis()) {
            times.put(uuid, new Time(uuid, name, millis));
            rebuildTop();
            dirty = true;
        } else if (!previous.name().equals(name)) {
            times.put(uuid, new Time(uuid, name, previous.millis()));
            dirty = true;
        }
        return previous;
    }

    /**
     * @param uuid player
     * @return found egg ids (a copy)
     */
    public Set<String> eggs(UUID uuid) {
        Set<String> found = eggs.get(uuid);
        return found == null ? Set.of() : new HashSet<>(found);
    }

    /**
     * @param uuid player
     * @param egg egg id
     * @return whether the egg was new
     */
    public boolean findEgg(UUID uuid, String egg) {
        boolean added = eggs.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(egg);
        if (added) {
            dirty = true;
        }
        return added;
    }

    /**
     * Clears a player's progress (admin reset).
     *
     * @param uuid player
     */
    public void reset(UUID uuid) {
        times.remove(uuid);
        eggs.remove(uuid);
        rebuildTop();
        dirty = true;
    }

    private void flushIfDirty() {
        if (dirty) {
            flush();
        }
    }

    /** Writes data.yml now. */
    public void flush() {
        synchronized (writeLock) {
            dirty = false;
            YamlConfiguration yaml = new YamlConfiguration();
            for (Time time : times.values()) {
                yaml.set("parkour." + time.uuid() + ".name", time.name());
                yaml.set("parkour." + time.uuid() + ".millis", time.millis());
            }
            eggs.forEach((uuid, found) -> yaml.set("eggs." + uuid, new ArrayList<>(found)));
            try {
                file.getParentFile().mkdirs();
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not save data.yml", e);
            }
        }
    }
}
