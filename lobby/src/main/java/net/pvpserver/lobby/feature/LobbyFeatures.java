package net.pvpserver.lobby.feature;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.util.Tasks;
import net.pvpserver.lobby.PvPLobby;
import net.pvpserver.lobby.config.LobbySettings;
import net.pvpserver.lobby.config.NpcDefinition;
import net.pvpserver.lobby.layout.Point;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns every interactive part of the lobby and its timers: one task every tick (display updates, ambient particles,
 * trails, parkour timer), one every second (wall rotation, tips, announcements), and the hologram refresh, which
 * takes a count snapshot on the main thread and renders texts off it.
 */
public final class LobbyFeatures {

    private final PvPLobby plugin;
    private final Displays displays;
    private final LobbyData data;
    private final LobbyActions actions;
    private final NpcService npcs;
    private final TriggerService triggers;
    private final ParkourService parkour;
    private final EggService eggs;
    private final LeaderboardWall wall;
    private final AmbientService ambient;
    private final AnnouncementService announcements;
    private final LobbyCosmetics cosmetics;
    private final Map<String, String> templates = new LinkedHashMap<>();
    private volatile boolean refreshing;
    private long ticks;
    private float spin;

    /**
     * @param plugin lobby plugin
     */
    public LobbyFeatures(PvPLobby plugin) {
        this.plugin = plugin;
        this.displays = new Displays(plugin);
        this.data = new LobbyData(plugin);
        this.actions = new LobbyActions(plugin);
        this.npcs = new NpcService(plugin, displays);
        this.triggers = new TriggerService(plugin);
        this.parkour = new ParkourService(plugin, data, displays);
        this.eggs = new EggService(plugin, data);
        this.wall = new LeaderboardWall(plugin, displays);
        this.ambient = new AmbientService(plugin);
        this.announcements = new AnnouncementService(plugin);
        this.cosmetics = new LobbyCosmetics(plugin);
    }

    /** Loads progress, registers listeners, spawns everything and starts the timers. */
    public void start() {
        data.load();
        data.startAutosave();
        plugin.getServer().getPluginManager().registerEvents(npcs, plugin);
        plugin.getServer().getPluginManager().registerEvents(triggers, plugin);
        plugin.api().leaderboards().onRefresh(wall::refresh);
        announcements.reload();
        spawnAll();
        Tasks.timer(this::tick, 1L, 1L);
        Tasks.timer(this::second, 20L, 20L);
    }

    /** Respawns every entity and rebuilds every lookup from the current layout and settings. */
    public void spawnAll() {
        World world = plugin.lobbyWorld().world();
        LobbySettings.Performance performance = plugin.settings().performance();
        parkour.cancelAll();
        npcs.despawnAll();
        displays.clear(world);
        displays.limits(performance.maxEntities(), performance.hologramUpdatesPerTick());
        npcs.spawnAll();
        spawnHolograms(world);
        parkour.spawnBoard();
        wall.spawn();
        triggers.rebuild();
        ambient.rebuild();
        if (displays.refused() > 0) {
            plugin.getLogger().warning(displays.refused() + " lobby entities were not spawned: performance.max-entities ("
                    + performance.maxEntities() + ") reached");
        }
        refreshLive();
    }

    private void spawnHolograms(World world) {
        templates.clear();
        MessageService m = plugin.messages();
        for (Map.Entry<String, Point> entry : plugin.lobbyWorld().layout().holograms().entrySet()) {
            String id = entry.getKey();
            if (id.equals(ParkourService.BOARD)) {
                continue;
            }
            List<String> lines = m.rawList("displays." + id);
            if (lines.isEmpty()) {
                plugin.getLogger().warning("layout.yml hologram " + id + " has no text: add displays." + id + " to messages.yml");
                continue;
            }
            String template = String.join("<newline>", lines);
            templates.put(id, template);
            displays.text("display:" + id, entry.getValue().at(world), Display.Billboard.CENTER, 1f, 0,
                    m.parse(template, LiveStats.empty().resolver()));
        }
    }

    /** Reload: settings, NPC definitions and layout were re-read; respawn and restyle. */
    public void reload() {
        announcements.reload();
        spawnAll();
    }

    /** Disable: end runs, hide the bar, remove entities, save progress. */
    public void stop() {
        parkour.cancelAll();
        announcements.hideAll();
        npcs.despawnAll();
        displays.clear(plugin.lobbyWorld().world());
        data.flush();
    }

    private void tick() {
        ticks++;
        displays.tick();
        ambient.tick();
        cosmetics.tick();
        if (ticks % 2 == 0) {
            parkour.tick();
        }
        LobbySettings.Npcs settings = plugin.settings().npcs();
        if (settings.lookAtPlayers() && ticks % 5 == 0) {
            npcs.lookAtPlayers();
        }
        if (ticks % settings.updateIntervalTicks() == 0) {
            refreshLive();
        }
    }

    private void second() {
        wall.tick();
        announcements.tick();
        if (ticks % 60 < 20) {
            spin += (float) Math.PI;
            displays.spinItems(spin, 60);
        }
    }

    /**
     * Refreshes live hologram counts: snapshot on the main thread, text rendering off it, changes queued back on the
     * main thread and applied a few per tick.
     */
    public void refreshLive() {
        if (refreshing) {
            return;
        }
        refreshing = true;
        LiveStats stats = LiveStats.capture(plugin.api(), parkour.runners());
        List<NpcDefinition> spawned = npcs.ids().stream().map(id -> plugin.npcDefinitions().get(id)).filter(java.util.Objects::nonNull).toList();
        Map<String, String> snapshot = new LinkedHashMap<>(templates);
        MessageService m = plugin.messages();
        Tasks.async(() -> {
            Map<String, Component> texts = new HashMap<>();
            try {
                for (NpcDefinition definition : spawned) {
                    texts.put("npc:" + definition.id(), npcs.render(definition, stats));
                }
                snapshot.forEach((id, template) -> texts.put("display:" + id, m.parse(template, stats.resolver())));
            } finally {
                Tasks.sync(() -> {
                    texts.forEach(displays::update);
                    refreshing = false;
                });
            }
        });
    }

    /**
     * Welcome title, sound and join effect for a player who just joined.
     *
     * @param player player
     */
    public void welcome(Player player) {
        LobbySettings.Welcome welcome = plugin.settings().welcome();
        MessageService m = plugin.messages();
        if (welcome.titleEnabled()) {
            player.showTitle(Title.title(m.parse(welcome.title(), MessageService.p("player", player.getName())),
                    m.parse(welcome.subtitle(), MessageService.p("player", player.getName())),
                    Title.Times.times(Duration.ofMillis(welcome.fadeIn() * 50L), Duration.ofMillis(welcome.stay() * 50L),
                            Duration.ofMillis(welcome.fadeOut() * 50L))));
        }
        if (!welcome.sound().isEmpty()) {
            player.playSound(Sound.sound(Key.key(welcome.sound()), Sound.Source.MASTER, welcome.volume(), welcome.pitch()));
        }
        cosmetics.joinEffect(player);
        eggs.grant(player);
    }

    /**
     * Cleans up after a player who left.
     *
     * @param player player
     */
    public void quit(Player player) {
        parkour.cancel(player, false);
        announcements.forget(player);
        cosmetics.forget(player);
        eggs.revoke(player);
        npcs.forget(player.getUniqueId());
    }

    /** @return display entities */
    public Displays displays() {
        return displays;
    }

    /** @return action runner */
    public LobbyActions actions() {
        return actions;
    }

    /** @return NPCs */
    public NpcService npcs() {
        return npcs;
    }

    /** @return triggers */
    public TriggerService triggers() {
        return triggers;
    }

    /** @return parkour */
    public ParkourService parkour() {
        return parkour;
    }

    /** @return eggs */
    public EggService eggs() {
        return eggs;
    }

    /** @return leaderboard wall */
    public LeaderboardWall wall() {
        return wall;
    }

    /** @return ambient particles */
    public AmbientService ambient() {
        return ambient;
    }

    /** @return tips and announcements */
    public AnnouncementService announcements() {
        return announcements;
    }

    /** @return lobby progress */
    public LobbyData data() {
        return data;
    }
}
