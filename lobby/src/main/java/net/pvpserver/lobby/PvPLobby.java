package net.pvpserver.lobby;

import net.pvpserver.core.api.Practice;
import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.api.bridge.FfaBridge;
import net.pvpserver.core.api.bridge.KitEditorBridge;
import net.pvpserver.core.api.bridge.LobbyBridge;
import net.pvpserver.core.api.bridge.MatchBridge;
import net.pvpserver.core.api.bridge.QueueBridge;
import net.pvpserver.core.api.bridge.SpectateBridge;
import net.pvpserver.core.command.CommandRegistrar;
import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.party.Party;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.Setting;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.lobby.command.LobbyCommands;
import net.pvpserver.lobby.config.LobbySettings;
import net.pvpserver.lobby.config.NpcDefinition;
import net.pvpserver.lobby.feature.LobbyFeatures;
import net.pvpserver.lobby.hologram.HologramService;
import net.pvpserver.lobby.kiteditor.KitEditor;
import net.pvpserver.lobby.menu.CosmeticsMenu;
import net.pvpserver.lobby.menu.LeaderboardMenu;
import net.pvpserver.lobby.menu.MenuConfig;
import net.pvpserver.lobby.menu.SettingsMenu;
import net.pvpserver.lobby.menu.StatsMenu;
import net.pvpserver.lobby.world.LayoutStore;
import net.pvpserver.lobby.world.LobbyImporter;
import net.pvpserver.lobby.world.LobbyWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * PvPLobby plugin: the lobby world (generated hub or imported map), NPCs, portals, parkour, leaderboard wall, hotbar,
 * menus, kit editor and scoreboards.
 */
public final class PvPLobby extends JavaPlugin implements Listener {

    private PracticeApi api;
    private MessageService messages;
    private ConfigFile config;
    private ConfigFile npcFile;
    private volatile LobbySettings settings;
    private volatile Map<String, NpcDefinition> npcDefinitions = Map.of();
    private MenuConfig menus;
    private LobbyHotbar hotbar;
    private LobbyVisibility visibility;
    private LobbyWorld lobbyWorld;
    private LobbyService lobby;
    private LobbyFeatures features;
    private LobbyImporter importer;
    private KitEditor kitEditor;
    private HologramService holograms;
    private final Set<UUID> flying = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        api = Practice.api();
        config = new ConfigFile(this, "config.yml");
        npcFile = new ConfigFile(this, "npcs.yml", true);
        loadSettings();
        messages = new MessageService(this, "messages.yml", api.messages());
        menus = new MenuConfig(new ConfigFile(this, "menus.yml"), messages);
        hotbar = new LobbyHotbar(api, new ConfigFile(this, "hotbar.yml"), messages);
        visibility = new LobbyVisibility(this, api);

        lobbyWorld = new LobbyWorld(this, api, new LayoutStore(this));
        lobbyWorld.start(settings);
        lobby = new LobbyService(api, this, hotbar, visibility, lobbyWorld);
        features = new LobbyFeatures(this);
        importer = new LobbyImporter(this, lobbyWorld);
        kitEditor = new KitEditor(this);
        holograms = new HologramService(this);

        api.bridges().register(LobbyBridge.class, lobby);
        api.bridges().register(KitEditorBridge.class, kitEditor);
        registerHotbarActions();
        hotbar.override(player -> features.parkour().running(player) ? "parkour" : null);

        ConfigFile scoreboard = new ConfigFile(this, "scoreboard.yml");
        LobbyScoreboard.Extras extras = new LobbyScoreboard.Extras() {
            @Override
            public void apply(Player player, java.util.Set<String> flags, List<net.kyori.adventure.text.minimessage.tag.resolver.TagResolver> out) {
                String zone = features.triggers().zoneOf(player);
                out.add(MessageService.c("zone", zone == null ? messages.parse(settings.zones().names().getOrDefault("plaza", "Lobby"))
                        : messages.parse(settings.zones().names().getOrDefault(zone, zone))));
                out.add(MessageService.p("parkour_time", features.parkour().currentTime(player)));
                out.add(MessageService.p("parkour_best", features.parkour().bestTime(player)));
                out.add(MessageService.p("eggs_found", features.eggs().found(player)));
                out.add(MessageService.p("eggs_total", lobbyWorld.layout().eggs().size()));
                if (features.parkour().running(player)) {
                    flags.add("parkour");
                }
            }
        };
        LobbyScoreboard lobbyBoard = new LobbyScoreboard(api, messages, scoreboard, "lobby", extras);
        LobbyScoreboard queueBoard = new LobbyScoreboard(api, messages, scoreboard, "queue", extras);
        LobbyScoreboard editorBoard = new LobbyScoreboard(api, messages, scoreboard, "editing", extras);
        api.sidebars().register(PlayerState.LOBBY, lobbyBoard);
        api.sidebars().register(PlayerState.QUEUE, queueBoard);
        api.sidebars().register(PlayerState.EDITING, editorBoard);

        getServer().getPluginManager().registerEvents(new LobbyListener(this, api, lobby), this);
        getServer().getPluginManager().registerEvents(kitEditor, this);
        getServer().getPluginManager().registerEvents(this, this);
        CommandRegistrar.register(this, LobbyCommands.create(this));
        holograms.start();
        features.start();
        lobbyWorld.onChange(features::spawnAll);

        api.reloads().register("lobby:messages", messages);
        api.reloads().register("lobby:menus", menus::reload);
        api.reloads().register("lobby:hotbar", hotbar::reload);
        api.reloads().register("lobby:scoreboard", () -> {
            scoreboard.reload();
            lobbyBoard.reload();
            queueBoard.reload();
            editorBoard.reload();
        });
        api.reloads().register("lobby:holograms", holograms::reload);
        // Last: settings, NPCs and layout, then everything respawns with the reloaded messages.
        api.reloads().register("lobby:config", this::reloadLobby);

        // Players already online (e.g. /reload) are reset into the lobby.
        getServer().getOnlinePlayers().forEach(lobby::sendToLobby);
        getLogger().info("PvPLobby enabled (lobby world: " + lobbyWorld.world().getName() + ", " + features.displays().size()
                + " lobby entities)");
    }

    @Override
    public void onDisable() {
        if (features != null) {
            features.stop();
        }
        if (holograms != null) {
            holograms.despawnAll();
        }
        if (api != null) {
            api.bridges().unregister(LobbyBridge.class);
            api.bridges().unregister(KitEditorBridge.class);
            api.reloads().unregisterPrefix("lobby:");
        }
    }

    /**
     * Re-reads config.yml, npcs.yml and layout.yml and respawns the lobby's entities.
     *
     * @return warnings from the three files
     */
    public List<String> reloadLobby() {
        config.reload();
        npcFile.reload();
        List<String> warnings = loadSettings();
        warnings.addAll(lobbyWorld.layouts().load());
        lobbyWorld.reload(settings);
        features.reload();
        return warnings;
    }

    private List<String> loadSettings() {
        List<String> warnings = new ArrayList<>();
        List<String> configWarnings = new ArrayList<>();
        settings = LobbySettings.read(config.get(), configWarnings);
        configWarnings.forEach(w -> getLogger().warning("config.yml: " + w));
        List<String> npcWarnings = new ArrayList<>();
        npcDefinitions = Map.copyOf(NpcDefinition.read(npcFile.get(), npcWarnings));
        npcWarnings.forEach(w -> getLogger().warning("npcs.yml: " + w));
        configWarnings.forEach(w -> warnings.add("config.yml: " + w));
        npcWarnings.forEach(w -> warnings.add("npcs.yml: " + w));
        return warnings;
    }

    private void registerHotbarActions() {
        action("queue-unranked", p -> bridge(p, QueueBridge.class, q -> q.openQueueMenu(p, false)));
        action("queue-ranked", p -> bridge(p, QueueBridge.class, q -> q.openQueueMenu(p, true)));
        action("leave-queue", p -> bridge(p, QueueBridge.class, q -> q.leaveQueue(p)));
        action("ffa", p -> bridge(p, FfaBridge.class, f -> f.openFfaMenu(p)));
        action("spectate", p -> bridge(p, SpectateBridge.class, s -> s.openSpectateMenu(p)));
        action("party-fight", p -> bridge(p, MatchBridge.class, m -> m.openPartyFightMenu(p)));
        action("party-create", p -> api.parties().create(p));
        action("party-leave", p -> api.parties().leave(p, false));
        action("party-info", p -> {
            Optional<Party> party = api.parties().partyOf(p);
            party.ifPresentOrElse(value -> api.parties().info(p, value), () -> api.messages().send(p, "party.not-in-party"));
        });
        action("kit-editor", kitEditor::openKitEditor);
        action("settings", p -> new SettingsMenu(this, p).open());
        action("cosmetics", p -> new CosmeticsMenu(this, p).open());
        action("leaderboards", p -> new LeaderboardMenu(this, p).open());
        action("stats", p -> {
            PlayerProfile profile = api.profiles().get(p);
            if (profile != null) {
                new StatsMenu(this, p, profile).open();
            }
        });
        action("parkour-checkpoint", p -> features.parkour().toCheckpoint(p));
        action("parkour-restart", p -> features.parkour().restart(p));
        action("parkour-leave", p -> features.parkour().cancel(p, true));
    }

    private void action(String id, Consumer<Player> handler) {
        api.hotbar().register(id, handler);
    }

    private <T> void bridge(Player player, Class<T> type, Consumer<T> action) {
        api.bridges().get(type).ifPresentOrElse(action, () -> api.messages().send(player, "general.feature-unavailable"));
    }

    /**
     * Applies side effects of a changed setting.
     *
     * @param player player
     * @param setting setting
     */
    public void applySetting(Player player, Setting setting) {
        switch (setting) {
            case SCOREBOARD -> api.sidebars().refresh(player);
            case LOBBY_PLAYERS -> visibility.update(player);
            case DOUBLE_JUMP -> {
                if (api.states().is(player, PlayerState.LOBBY, PlayerState.QUEUE)) {
                    lobby.applyFlight(player);
                }
            }
            default -> {
            }
        }
    }

    /**
     * @param player player
     * @return whether /fly is enabled for the player
     */
    public boolean flying(Player player) {
        return flying.contains(player.getUniqueId());
    }

    /**
     * @param player player
     * @return new /fly state
     */
    public boolean toggleFly(Player player) {
        boolean enabled = flying.add(player.getUniqueId()) || !flying.remove(player.getUniqueId());
        if (enabled) {
            features.parkour().cancel(player, true);
        }
        player.setAllowFlight(enabled || lobby.allowsDoubleJump(player));
        player.setFlying(enabled);
        return enabled;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        flying.remove(event.getPlayer().getUniqueId());
        features.quit(event.getPlayer());
    }

    /** @return practice api */
    public PracticeApi api() {
        return api;
    }

    /** @return lobby messages */
    public MessageService messages() {
        return messages;
    }

    /** @return typed config.yml */
    public LobbySettings settings() {
        return settings;
    }

    /** @return NPC definitions from npcs.yml */
    public Map<String, NpcDefinition> npcDefinitions() {
        return npcDefinitions;
    }

    /** @return menus.yml access */
    public MenuConfig menus() {
        return menus;
    }

    /** @return lobby hotbar */
    public LobbyHotbar hotbar() {
        return hotbar;
    }

    /** @return lobby service */
    public LobbyService lobby() {
        return lobby;
    }

    /** @return lobby world */
    public LobbyWorld lobbyWorld() {
        return lobbyWorld;
    }

    /** @return NPCs, portals, parkour and the rest */
    public LobbyFeatures features() {
        return features;
    }

    /** @return lobby importer */
    public LobbyImporter importer() {
        return importer;
    }

    /** @return visibility */
    public LobbyVisibility visibility() {
        return visibility;
    }

    /** @return kit editor */
    public KitEditor kitEditor() {
        return kitEditor;
    }

    /** @return holograms */
    public HologramService holograms() {
        return holograms;
    }
}
