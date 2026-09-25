package net.pvpserver.core;

import net.pvpserver.core.anticheat.CheckService;
import net.pvpserver.core.api.BridgeRegistry;
import net.pvpserver.core.api.Counts;
import net.pvpserver.core.api.Practice;
import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.arena.ArenaCommand;
import net.pvpserver.core.arena.ArenaEditor;
import net.pvpserver.core.arena.ArenaListener;
import net.pvpserver.core.arena.ArenaService;
import net.pvpserver.core.chat.ChatCommands;
import net.pvpserver.core.chat.ChatService;
import net.pvpserver.core.combat.CombatListener;
import net.pvpserver.core.combat.CombatService;
import net.pvpserver.core.combat.CombatTagService;
import net.pvpserver.core.command.AdminCommands;
import net.pvpserver.core.command.BaseCommand;
import net.pvpserver.core.command.CommandRegistrar;
import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.config.ReloadRegistry;
import net.pvpserver.core.cosmetic.CosmeticService;
import net.pvpserver.core.gui.MenuListener;
import net.pvpserver.core.hotbar.HotbarService;
import net.pvpserver.core.kit.KitService;
import net.pvpserver.core.knockback.KnockbackService;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.moderation.ModerationCommands;
import net.pvpserver.core.moderation.PunishmentService;
import net.pvpserver.core.moderation.ReportService;
import net.pvpserver.core.moderation.StaffService;
import net.pvpserver.core.party.PartyCommand;
import net.pvpserver.core.party.PartyService;
import net.pvpserver.core.profile.ProfileService;
import net.pvpserver.core.rank.RankService;
import net.pvpserver.core.scoreboard.SidebarService;
import net.pvpserver.core.scoreboard.TabService;
import net.pvpserver.core.state.PlayerStateService;
import net.pvpserver.core.stats.LeaderboardService;
import net.pvpserver.core.stats.StatsService;
import net.pvpserver.core.storage.Database;
import net.pvpserver.core.storage.Storage;
import net.pvpserver.core.util.Tasks;
import net.pvpserver.core.world.WorldService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * PvPCore plugin: boots every shared service and exposes them through {@link PracticeApi}.
 */
public final class PvPCore extends JavaPlugin implements PracticeApi {

    private ConfigFile config;
    private MessageService messages;
    private Database database;
    private Storage storage;
    private PlayerStateService states;
    private BridgeRegistry bridges;
    private ReloadRegistry reloads;
    private ProfileService profiles;
    private RankService ranks;
    private KitService kits;
    private KnockbackService knockback;
    private CombatService combat;
    private CombatListener combatListener;
    private PartyService parties;
    private WorldService worlds;
    private ArenaService arenas;
    private ArenaEditor arenaEditor;
    private StaffService staff;
    private PunishmentService punishments;
    private ReportService reports;
    private ChatService chat;
    private CosmeticService cosmetics;
    private CheckService checks;
    private StatsService stats;
    private LeaderboardService leaderboards;
    private HotbarService hotbar;
    private Counts counts;
    private SidebarService sidebars;
    private TabService tab;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        Tasks.init(this);
        try {
            boot();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "PvPCore failed to start; disabling", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Practice.set(this);
        getLogger().info("PvPCore enabled in " + (System.currentTimeMillis() - start) + "ms (storage: " + database.type() + ")");
    }

    private void boot() {
        config = new ConfigFile(this, "config.yml");
        YamlConfiguration c = config.get();
        messages = new MessageService(this, "messages.yml", null);
        reloads = new ReloadRegistry();
        bridges = new BridgeRegistry();
        states = new PlayerStateService();

        database = new Database(c.getConfigurationSection("storage"), getDataFolder(), getLogger());
        String defaultRank = new ConfigFile(this, "ranks.yml").get().getString("default-rank", "default");
        storage = new Storage(database, defaultRank, c.getInt("elo.starting", 1000));

        profiles = new ProfileService(this, storage.profiles(), storage.stats(), storage.layouts(), messages);
        ranks = new RankService(this, profiles);
        kits = new KitService(this, profiles, storage.layouts());
        knockback = new KnockbackService(this);
        combat = new CombatService(kits, knockback, new CombatTagService(15, 12));
        combat.configure(c.getConfigurationSection("combat"));
        combatListener = new CombatListener(combat, states, messages, kits.specialKey());
        combatListener.configure(c.getConfigurationSection("combat"));
        parties = new PartyService(messages, profiles, states, bridges);
        parties.configure(c.getConfigurationSection("party"));

        worlds = new WorldService(getLogger());
        arenas = new ArenaService(this, c.getConfigurationSection("arenas"), worlds);
        arenas.start();
        arenaEditor = new ArenaEditor(this, arenas, worlds, messages, c.getString("arenas.editor-world", "pvp_editor"));

        staff = new StaffService(this, messages);
        staff.configure(c.getStringList("moderation.frozen-allowed-commands"));
        punishments = new PunishmentService(storage.punishments(), messages, staff, getLogger());
        punishments.configure(c.getBoolean("moderation.broadcast-public", false));
        reports = new ReportService(storage.reports(), messages, staff);
        reports.configure(c.getInt("moderation.report-cooldown-seconds", 60));
        chat = new ChatService(messages, profiles, ranks, punishments, staff);
        chat.configure(c.getConfigurationSection("chat"));
        cosmetics = new CosmeticService(this, profiles, messages);
        checks = new CheckService(messages, staff);
        checks.configure(c.getConfigurationSection("anticheat"));
        stats = new StatsService(profiles, storage.matches());
        stats.configure(c.getConfigurationSection("elo"));
        leaderboards = new LeaderboardService(storage.stats(), kits, getLogger());
        hotbar = new HotbarService(this);
        counts = new Counts(bridges, states);
        sidebars = new SidebarService(profiles, states, getLogger());
        tab = new TabService(messages, ranks, counts);
        tab.configure(c.getConfigurationSection("tab"));
        tab.staff(staff);
        staff.onVanishChange(tab::updateName);

        List<Listener> listeners = List.of(states, profiles, ranks, combatListener, parties, new ArenaListener(arenas), arenaEditor,
                staff, punishments, chat, checks, hotbar, sidebars, tab, new MenuListener());
        listeners.forEach(listener -> getServer().getPluginManager().registerEvents(listener, this));

        profiles.start(c.getInt("profiles.autosave-seconds", 300));
        counts.start();
        sidebars.start(c.getInt("scoreboard.update-interval-ticks", 10));
        tab.start(c.getInt("tab.update-interval-ticks", 40));
        leaderboards.start(c.getInt("leaderboards.refresh-seconds", 300), c.getInt("leaderboards.size", 10));

        List<BaseCommand> commands = new ArrayList<>();
        commands.add(new PartyCommand(messages, parties, bridges));
        commands.add(new PartyCommand.PartyChatCommand(messages, parties));
        commands.add(new ArenaCommand(messages, arenas, arenaEditor));
        commands.addAll(ModerationCommands.create(messages, punishments, staff, reports, profiles));
        commands.addAll(ChatCommands.create(messages, chat, profiles));
        commands.addAll(AdminCommands.create(this, database.type().name()));
        CommandRegistrar.register(this, commands);

        reloads.register("core:config", () -> {
            config.reload();
            YamlConfiguration r = config.get();
            combat.configure(r.getConfigurationSection("combat"));
            combatListener.configure(r.getConfigurationSection("combat"));
            parties.configure(r.getConfigurationSection("party"));
            staff.configure(r.getStringList("moderation.frozen-allowed-commands"));
            punishments.configure(r.getBoolean("moderation.broadcast-public", false));
            reports.configure(r.getInt("moderation.report-cooldown-seconds", 60));
            chat.configure(r.getConfigurationSection("chat"));
            checks.configure(r.getConfigurationSection("anticheat"));
            stats.configure(r.getConfigurationSection("elo"));
            tab.configure(r.getConfigurationSection("tab"));
        });
        reloads.register("core:messages", messages);
        reloads.register("core:ranks", ranks);
        reloads.register("core:kits", kits);
        reloads.register("core:knockback", knockback);
        reloads.register("core:arenas", arenas);
        reloads.register("core:cosmetics", cosmetics);
    }

    @Override
    public void onDisable() {
        Practice.set(null);
        if (arenas != null) {
            arenas.shutdown();
        }
        if (profiles != null) {
            profiles.saveAllBlocking();
        }
        if (database != null) {
            database.close();
        }
    }

    /** @return storage repositories */
    public Storage storage() {
        return storage;
    }

    /** @return config.yml */
    public ConfigFile coreConfig() {
        return config;
    }

    @Override
    public MessageService messages() {
        return messages;
    }

    @Override
    public PlayerStateService states() {
        return states;
    }

    @Override
    public BridgeRegistry bridges() {
        return bridges;
    }

    @Override
    public ReloadRegistry reloads() {
        return reloads;
    }

    @Override
    public ProfileService profiles() {
        return profiles;
    }

    @Override
    public RankService ranks() {
        return ranks;
    }

    @Override
    public KitService kits() {
        return kits;
    }

    @Override
    public KnockbackService knockback() {
        return knockback;
    }

    @Override
    public CombatService combat() {
        return combat;
    }

    @Override
    public PartyService parties() {
        return parties;
    }

    @Override
    public ArenaService arenas() {
        return arenas;
    }

    @Override
    public SidebarService sidebars() {
        return sidebars;
    }

    @Override
    public TabService tab() {
        return tab;
    }

    @Override
    public ChatService chat() {
        return chat;
    }

    @Override
    public PunishmentService punishments() {
        return punishments;
    }

    @Override
    public StaffService staff() {
        return staff;
    }

    @Override
    public CosmeticService cosmetics() {
        return cosmetics;
    }

    @Override
    public CheckService checks() {
        return checks;
    }

    @Override
    public StatsService stats() {
        return stats;
    }

    @Override
    public LeaderboardService leaderboards() {
        return leaderboards;
    }

    @Override
    public HotbarService hotbar() {
        return hotbar;
    }

    @Override
    public WorldService worlds() {
        return worlds;
    }

    @Override
    public Counts counts() {
        return counts;
    }
}
