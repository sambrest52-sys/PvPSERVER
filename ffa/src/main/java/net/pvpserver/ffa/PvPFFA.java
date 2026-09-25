package net.pvpserver.ffa;

import net.pvpserver.core.api.Practice;
import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.api.bridge.FfaBridge;
import net.pvpserver.core.command.CommandRegistrar;
import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.state.PlayerState;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * PvPFFA plugin: ranked and unranked free-for-all arenas.
 */
public final class PvPFFA extends JavaPlugin {

    private PracticeApi api;
    private MessageService messages;
    private ConfigFile config;
    private FfaManager ffa;
    private BlockDecay decay;

    @Override
    public void onEnable() {
        api = Practice.api();
        config = new ConfigFile(this, "config.yml");
        messages = new MessageService(this, "messages.yml", api.messages());
        ffa = new FfaManager(this);
        decay = new BlockDecay(config.get().getInt("block-decay-seconds", 10));
        api.bridges().register(FfaBridge.class, ffa);
        api.hotbar().register("ffa", player -> ffa.openFfaMenu(player));

        ConfigFile scoreboard = new ConfigFile(this, "scoreboard.yml");
        FfaScoreboard board = new FfaScoreboard(this, scoreboard);
        api.sidebars().register(PlayerState.FFA, board);

        getServer().getPluginManager().registerEvents(new FfaListener(this, decay), this);
        CommandRegistrar.register(this, List.of(new FfaCommand(this)));
        ffa.setup();

        api.reloads().register("ffa:config", () -> {
            config.reload();
            decay.configure(config.get().getInt("block-decay-seconds", 10));
        });
        api.reloads().register("ffa:messages", messages);
        api.reloads().register("ffa:scoreboard", () -> {
            scoreboard.reload();
            board.reload();
        });
        getLogger().info("PvPFFA enabled");
    }

    @Override
    public void onDisable() {
        if (ffa != null) {
            ffa.shutdown();
        }
        if (decay != null) {
            decay.clearAll();
        }
        if (api != null) {
            api.bridges().unregister(FfaBridge.class);
            api.reloads().unregisterPrefix("ffa:");
        }
    }

    /** @return practice api */
    public PracticeApi api() {
        return api;
    }

    /** @return FFA messages */
    public MessageService messages() {
        return messages;
    }

    /** @return config.yml */
    public ConfigFile config() {
        return config;
    }

    /** @return FFA manager */
    public FfaManager ffa() {
        return ffa;
    }
}
