package net.pvpserver.lobby.hologram;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.stats.LeaderboardEntry;
import net.pvpserver.core.stats.LeaderboardService;
import net.pvpserver.core.stats.StatField;
import net.pvpserver.core.util.LocationUtil;
import net.pvpserver.core.util.Tasks;
import net.pvpserver.lobby.PvPLobby;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Leaderboard holograms built from {@link TextDisplay} entities (no NPC or hologram plugin needed). Entities are
 * non-persistent and re-created when missing, so they never duplicate across restarts.
 */
public final class HologramService {

    private final PvPLobby plugin;
    private final ConfigFile file;
    private final NamespacedKey key;
    private final Map<String, Hologram> holograms = new LinkedHashMap<>();

    /**
     * @param plugin lobby plugin
     */
    public HologramService(PvPLobby plugin) {
        this.plugin = plugin;
        this.file = new ConfigFile(plugin, "holograms.yml");
        this.key = new NamespacedKey(plugin, "leaderboard_hologram");
    }

    /** Loads definitions, removes stale entities and spawns holograms; starts the watchdog. */
    public void start() {
        reload();
        plugin.api().leaderboards().onRefresh(this::updateAll);
        Tasks.timer(this::ensureSpawned, 200L, 200L);
    }

    /** Reloads holograms.yml and respawns everything. */
    public void reload() {
        file.reload();
        despawnAll();
        holograms.clear();
        ConfigurationSection root = file.get().getConfigurationSection("holograms");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                StatField field = StatField.parse(s.getString("stat", "ELO"));
                holograms.put(id, new Hologram(id, s.getString("location"), s.getString("kit", LeaderboardService.GLOBAL),
                        field == null ? StatField.ELO : field));
            }
        }
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    entity.remove();
                }
            }
        }
        ensureSpawned();
    }

    /**
     * Creates a hologram at a location.
     *
     * @param id id
     * @param location location
     * @param kit kit id or "global"
     * @param field statistic
     */
    public void create(String id, Location location, String kit, StatField field) {
        String base = "holograms." + id + ".";
        file.get().set(base + "location", LocationUtil.serialize(location));
        file.get().set(base + "kit", kit);
        file.get().set(base + "stat", field.name());
        file.save();
        reload();
    }

    /**
     * @param id id
     * @return whether it existed
     */
    public boolean delete(String id) {
        if (!holograms.containsKey(id)) {
            return false;
        }
        file.get().set("holograms." + id, null);
        file.save();
        reload();
        return true;
    }

    /** @return hologram ids */
    public Collection<String> ids() {
        return holograms.keySet();
    }

    private void ensureSpawned() {
        for (Hologram hologram : holograms.values()) {
            if (hologram.entity != null && hologram.entity.isValid()) {
                continue;
            }
            Location location = LocationUtil.deserialize(hologram.location);
            if (location == null || location.getWorld() == null || !location.isChunkLoaded()) {
                continue;
            }
            hologram.entity = location.getWorld().spawn(location, TextDisplay.class, display -> {
                display.setPersistent(false);
                display.setBillboard(Display.Billboard.CENTER);
                display.setLineWidth(400);
                display.setShadowed(true);
                display.setSeeThrough(false);
                display.getPersistentDataContainer().set(key, PersistentDataType.STRING, hologram.id);
            });
            render(hologram);
        }
    }

    private void updateAll() {
        holograms.values().forEach(this::render);
    }

    private void render(Hologram hologram) {
        if (hologram.entity == null || !hologram.entity.isValid()) {
            return;
        }
        MessageService messages = plugin.messages();
        String kitId = hologram.kit.equalsIgnoreCase(LeaderboardService.GLOBAL) ? null : hologram.kit.toLowerCase(Locale.ROOT);
        Component kitName = kitId == null ? messages.parse("<primary>Global")
                : plugin.api().kits().get(kitId).map(Kit::name).orElse(Component.text(hologram.kit));
        Component text = messages.get("hologram.title", MessageService.c("kit", kitName), MessageService.p("stat", hologram.field.displayName()));
        List<LeaderboardEntry> entries = plugin.api().leaderboards().top(kitId, hologram.field);
        int position = 1;
        for (LeaderboardEntry entry : entries) {
            text = text.append(Component.newline()).append(messages.get("hologram.line", MessageService.p("position", position++),
                    MessageService.p("player", entry.name()), MessageService.p("value", entry.value())));
        }
        if (entries.isEmpty()) {
            text = text.append(Component.newline()).append(messages.get("hologram.empty"));
        }
        text = text.append(Component.newline()).append(messages.get("hologram.footer"));
        hologram.entity.text(text);
    }

    /** Removes spawned entities (disable/reload). */
    public void despawnAll() {
        for (Hologram hologram : holograms.values()) {
            if (hologram.entity != null) {
                hologram.entity.remove();
                hologram.entity = null;
            }
        }
    }

    private static final class Hologram {
        final String id;
        final String location;
        final String kit;
        final StatField field;
        TextDisplay entity;

        Hologram(String id, String location, String kit, StatField field) {
            this.id = id;
            this.location = location;
            this.kit = kit;
            this.field = field;
        }
    }
}
