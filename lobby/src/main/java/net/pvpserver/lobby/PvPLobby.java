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
import net.pvpserver.lobby.hologram.HologramService;
import net.pvpserver.lobby.kiteditor.KitEditor;
import net.pvpserver.lobby.menu.CosmeticsMenu;
import net.pvpserver.lobby.menu.LeaderboardMenu;
import net.pvpserver.lobby.menu.MenuConfig;
import net.pvpserver.lobby.menu.SettingsMenu;
import net.pvpserver.lobby.menu.StatsMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * PvPLobby plugin: spawn hub, hotbar, menus, kit editor and leaderboard holograms.
 */
public final class PvPLobby extends JavaPlugin implements Listener {

    private PracticeApi api;
    private MessageService messages;
    private ConfigFile config;
    private MenuConfig menus;
    private LobbyHotbar hotbar;
    private LobbyVisibility visibility;
    private LobbyService lobby;
    private KitEditor kitEditor;
    private HologramService holograms;
    private final Set<UUID> flying = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        api = Practice.api();
        config = new ConfigFile(this, "config.yml");
        messages = new MessageService(this, "messages.yml", api.messages());
        menus = new MenuConfig(new ConfigFile(this, "menus.yml"), messages);
        hotbar = new LobbyHotbar(api, new ConfigFile(this, "hotbar.yml"), messages);
        visibility = new LobbyVisibility(this, api);
        lobby = new LobbyService(api, config, hotbar, visibility);
        kitEditor = new KitEditor(this);
        holograms = new HologramService(this);

        api.bridges().register(LobbyBridge.class, lobby);
        api.bridges().register(KitEditorBridge.class, kitEditor);
        registerHotbarActions();

        ConfigFile scoreboard = new ConfigFile(this, "scoreboard.yml");
        LobbyScoreboard lobbyBoard = new LobbyScoreboard(api, messages, scoreboard, "lobby");
        LobbyScoreboard queueBoard = new LobbyScoreboard(api, messages, scoreboard, "queue");
        LobbyScoreboard editorBoard = new LobbyScoreboard(api, messages, scoreboard, "editing");
        api.sidebars().register(PlayerState.LOBBY, lobbyBoard);
        api.sidebars().register(PlayerState.QUEUE, queueBoard);
        api.sidebars().register(PlayerState.EDITING, editorBoard);

        getServer().getPluginManager().registerEvents(new LobbyListener(this, api, lobby), this);
        getServer().getPluginManager().registerEvents(kitEditor, this);
        getServer().getPluginManager().registerEvents(this, this);
        CommandRegistrar.register(this, LobbyCommands.create(this));
        holograms.start();

        api.reloads().register("lobby:config", () -> {
            config.reload();
            lobby.reload();
        });
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

        // Players already online (e.g. /reload) are reset into the lobby.
        getServer().getOnlinePlayers().forEach(lobby::sendToLobby);
        getLogger().info("PvPLobby enabled (spawn: " + lobby.world().getName() + ")");
    }

    @Override
    public void onDisable() {
        if (holograms != null) {
            holograms.despawnAll();
        }
        if (api != null) {
            api.bridges().unregister(LobbyBridge.class);
            api.bridges().unregister(KitEditorBridge.class);
            api.reloads().unregisterPrefix("lobby:");
        }
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
                    player.setAllowFlight(player.hasPermission("pvp.lobby.fly") || lobby.allowsDoubleJump(player));
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
        player.setAllowFlight(enabled || lobby.allowsDoubleJump(player));
        player.setFlying(enabled);
        return enabled;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        flying.remove(event.getPlayer().getUniqueId());
    }

    /** @return practice api */
    public PracticeApi api() {
        return api;
    }

    /** @return lobby messages */
    public MessageService messages() {
        return messages;
    }

    /** @return menus.yml access */
    public MenuConfig menus() {
        return menus;
    }

    /** @return lobby service */
    public LobbyService lobby() {
        return lobby;
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
