package net.pvpserver.duels;

import net.pvpserver.core.api.Practice;
import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.api.bridge.MatchBridge;
import net.pvpserver.core.api.bridge.QueueBridge;
import net.pvpserver.core.api.bridge.SpectateBridge;
import net.pvpserver.core.command.CommandRegistrar;
import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.duels.command.DuelsCommands;
import net.pvpserver.duels.match.MatchListener;
import net.pvpserver.duels.match.MatchManager;
import net.pvpserver.duels.match.MatchScoreboard;
import net.pvpserver.duels.queue.QueueManager;
import net.pvpserver.duels.request.RequestManager;
import net.pvpserver.duels.spectate.SpectatorManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PvPDuels plugin: queues, matches, duel requests, party fights and spectating.
 */
public final class PvPDuels extends JavaPlugin {

    private PracticeApi api;
    private MessageService messages;
    private ConfigFile config;
    private ConfigFile menus;
    private QueueManager queues;
    private MatchManager matches;
    private RequestManager requests;
    private SpectatorManager spectators;
    private DuelsBridge bridge;

    @Override
    public void onEnable() {
        api = Practice.api();
        config = new ConfigFile(this, "config.yml");
        messages = new MessageService(this, "messages.yml", api.messages());
        menus = new ConfigFile(this, "menus.yml");
        matches = new MatchManager(this);
        queues = new QueueManager(this);
        requests = new RequestManager(this);
        spectators = new SpectatorManager(this);
        configure();

        bridge = new DuelsBridge(this);
        api.bridges().register(QueueBridge.class, bridge);
        api.bridges().register(MatchBridge.class, bridge);
        api.bridges().register(SpectateBridge.class, bridge);

        ConfigFile scoreboard = new ConfigFile(this, "scoreboard.yml");
        MatchScoreboard matchBoard = new MatchScoreboard(this, scoreboard, false);
        MatchScoreboard spectatorBoard = new MatchScoreboard(this, scoreboard, true);
        api.sidebars().register(PlayerState.MATCH, matchBoard);
        api.sidebars().register(PlayerState.SPECTATING, spectatorBoard);

        var pm = getServer().getPluginManager();
        pm.registerEvents(queues, this);
        pm.registerEvents(new MatchListener(this), this);
        pm.registerEvents(requests, this);
        pm.registerEvents(spectators, this);
        CommandRegistrar.register(this, DuelsCommands.create(this));

        api.reloads().register("duels:config", () -> {
            config.reload();
            configure();
        });
        api.reloads().register("duels:messages", messages);
        api.reloads().register("duels:menus", menus::reload);
        api.reloads().register("duels:scoreboard", () -> {
            scoreboard.reload();
            matchBoard.reload();
            spectatorBoard.reload();
        });
        getLogger().info("PvPDuels enabled");
    }

    private void configure() {
        YamlConfiguration c = config.get();
        matches.configure(c.getConfigurationSection("match"));
        queues.configure(c.getConfigurationSection("queue"));
        requests.configure(c.getConfigurationSection("requests"));
    }

    @Override
    public void onDisable() {
        // Server stop or plugin disable mid-match: cancel matches (no stats) and empty queues so nobody is stuck.
        if (matches != null) {
            matches.shutdown();
        }
        if (queues != null) {
            queues.clear();
        }
        if (api != null) {
            api.bridges().unregister(QueueBridge.class);
            api.bridges().unregister(MatchBridge.class);
            api.bridges().unregister(SpectateBridge.class);
            api.reloads().unregisterPrefix("duels:");
        }
    }

    /** @return practice api */
    public PracticeApi api() {
        return api;
    }

    /** @return duels messages */
    public MessageService messages() {
        return messages;
    }

    /** @return menus.yml */
    public YamlConfiguration menus() {
        return menus.get();
    }

    /** @return queues */
    public QueueManager queues() {
        return queues;
    }

    /** @return matches */
    public MatchManager matches() {
        return matches;
    }

    /** @return duel requests */
    public RequestManager requests() {
        return requests;
    }

    /** @return spectators */
    public SpectatorManager spectators() {
        return spectators;
    }

    /** @return bridge implementation */
    public DuelsBridge bridge() {
        return bridge;
    }
}
