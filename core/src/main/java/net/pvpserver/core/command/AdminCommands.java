package net.pvpserver.core.command;

import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.arena.ArenaInstance;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.knockback.KnockbackProfile;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.rank.Rank;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * {@code /pvpadmin}, {@code /kb}, {@code /rank} and {@code /alerts}.
 */
public final class AdminCommands {

    private AdminCommands() {
    }

    /**
     * @param api practice api
     * @param storageType storage type name for the status output
     * @return commands
     */
    public static List<BaseCommand> create(PracticeApi api, String storageType) {
        return List.of(new PvPAdmin(api, storageType), new Knockback(api), new RankCmd(api), new Alerts(api));
    }

    private static final class Sub extends SubCommand {
        private final BiConsumer<CommandSender, String[]> action;
        private final Suggester suggester;

        Sub(String name, String permission, String usage, String description, boolean playerOnly, int minArgs,
            BiConsumer<CommandSender, String[]> action, Suggester suggester) {
            super(name, List.of(), permission, usage, description, playerOnly, minArgs);
            this.action = action;
            this.suggester = suggester;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            action.accept(sender, args);
        }

        @Override
        public List<String> suggest(CommandSender sender, String[] args) {
            return suggester == null ? List.of() : suggester.suggest(sender, args);
        }
    }

    @FunctionalInterface
    private interface Suggester {
        List<String> suggest(CommandSender sender, String[] args);
    }

    private static final class PvPAdmin extends BaseCommand {
        PvPAdmin(PracticeApi api, String storageType) {
            super(api.messages(), "pvpadmin", List.of("pa", "practiceadmin"), "pvp.admin", "Practice administration", "", false);
            MessageService m = api.messages();
            sub(new Sub("reload", "pvp.admin.reload", "reload", "Reload every config file", false, 0, (s, a) -> {
                List<String> errors = new ArrayList<>();
                long start = System.currentTimeMillis();
                int ok = api.reloads().reloadAll(errors::add);
                m.send(s, "admin.reloaded", MessageService.p("count", ok), MessageService.p("ms", System.currentTimeMillis() - start));
                errors.forEach(e -> m.send(s, "admin.reload-error", MessageService.p("error", e)));
            }, null));
            sub(new Sub("status", "pvp.admin", "status", "Show server status", false, 0, (s, a) -> {
                long inUse = api.arenas().instances().stream().filter(i -> i.state() == ArenaInstance.State.IN_USE).count();
                Runtime rt = Runtime.getRuntime();
                m.send(s, "admin.status", MessageService.p("tps", api.counts().tpsFormatted()),
                        MessageService.p("online", Bukkit.getOnlinePlayers().size()),
                        MessageService.p("lobby", api.states().count(PlayerState.LOBBY)),
                        MessageService.p("queue", api.counts().queued()), MessageService.p("fighting", api.counts().fighting()),
                        MessageService.p("ffa", api.counts().ffa()), MessageService.p("spectating", api.counts().spectating()),
                        MessageService.p("instances", api.arenas().instances().size()), MessageService.p("in_use", inUse),
                        MessageService.p("storage", storageType),
                        MessageService.p("memory", (rt.totalMemory() - rt.freeMemory()) / 1048576 + "/" + rt.maxMemory() / 1048576 + " MB"));
            }, null));
            sub(new Sub("setelo", "pvp.admin.stats", "setelo <player> <kit> <elo>", "Set a player's ELO", false, 3, (s, a) -> {
                Player target = Bukkit.getPlayerExact(a[0]);
                PlayerProfile profile = target == null ? null : api.profiles().get(target);
                Kit kit = api.kits().get(a[1]).orElse(null);
                if (profile == null || kit == null) {
                    m.send(s, "admin.invalid-target");
                    return;
                }
                try {
                    profile.stats(kit.id()).elo(Integer.parseInt(a[2]));
                } catch (NumberFormatException e) {
                    m.send(s, "command.not-a-number", MessageService.p("value", a[2]));
                    return;
                }
                profile.markDirty();
                m.send(s, "admin.elo-set", MessageService.p("player", target.getName()), MessageService.p("kit", kit.id()), MessageService.p("elo", a[2]));
            }, (s, a) -> a.length == 1 ? Suggest.players(s) : a.length == 2 ? new ArrayList<>(api.kits().ids()) : List.of()));
            sub(new Sub("resetstats", "pvp.admin.stats", "resetstats <player> [kit]", "Reset statistics", false, 1, (s, a) -> {
                api.profiles().resolve(a[0]).whenComplete((uuid, error) -> Tasks.sync(() -> {
                    if (error != null || uuid.isEmpty()) {
                        m.send(s, "command.player-never-joined", MessageService.p("player", a[0]));
                        return;
                    }
                    String kit = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : null;
                    PlayerProfile online = api.profiles().get(uuid.get());
                    if (online != null) {
                        if (kit == null) {
                            online.allStats().clear();
                        } else {
                            online.allStats().remove(kit);
                        }
                    }
                    net.pvpserver.core.storage.repository.StatsRepository repo = statsRepository(api);
                    if (repo != null) {
                        repo.reset(uuid.get(), kit);
                    }
                    m.send(s, "admin.stats-reset", MessageService.p("player", a[0]), MessageService.p("kit", kit == null ? "all kits" : kit));
                }));
            }, (s, a) -> a.length == 1 ? Suggest.players(s) : a.length == 2 ? new ArrayList<>(api.kits().ids()) : List.of()));
            sub(new Sub("kit", "pvp.admin.kits", "kit <kit> [player]", "Give a kit's items for inspection (/spawn resets)", false, 1, (s, a) -> {
                Kit kit = api.kits().get(a[0]).orElse(null);
                Player target = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : s instanceof Player p ? p : null;
                if (kit == null || target == null) {
                    m.send(s, "admin.invalid-target");
                    return;
                }
                api.kits().giveKit(target, kit);
                m.send(s, "admin.kit-given", MessageService.c("kit", kit.name()), MessageService.p("player", target.getName()));
            }, (s, a) -> a.length == 1 ? new ArrayList<>(api.kits().ids()) : a.length == 2 ? Suggest.players(s) : List.of()));
            sub(new Sub("leaderboards", "pvp.admin", "leaderboards", "Refresh leaderboards now", false, 0, (s, a) -> {
                api.leaderboards().refresh().whenComplete((v, e) -> Tasks.sync(() -> m.send(s, "admin.leaderboards-refreshed")));
            }, null));
        }

        private static net.pvpserver.core.storage.repository.StatsRepository statsRepository(PracticeApi api) {
            return api instanceof net.pvpserver.core.PvPCore core ? core.storage().stats() : null;
        }
    }

    private static final class Knockback extends BaseCommand {
        Knockback(PracticeApi api) {
            super(api.messages(), "kb", List.of("knockback"), "pvp.admin.kb", "Edit knockback profiles", "", false);
            MessageService m = api.messages();
            Suggester profiles = (s, a) -> a.length == 1 ? api.knockback().profiles().stream().map(KnockbackProfile::name).toList() : List.of();
            sub(new Sub("list", "pvp.admin.kb", "list", "List profiles", false, 0, (s, a) -> {
                m.send(s, "kb.list-header", MessageService.p("default", api.knockback().defaultProfile()));
                for (KnockbackProfile p : api.knockback().profiles()) {
                    sendProfile(m, s, p);
                }
            }, null));
            sub(new Sub("info", "pvp.admin.kb", "info <profile>", "Show a profile", false, 1, (s, a) -> {
                if (!api.knockback().exists(a[0])) {
                    m.send(s, "kb.not-found", MessageService.p("profile", a[0]));
                    return;
                }
                sendProfile(m, s, api.knockback().profile(a[0]));
            }, profiles));
            sub(new Sub("set", "pvp.admin.kb", "set <profile> <field> <value>", "Change a value (applies instantly)", false, 3, (s, a) -> {
                if (!api.knockback().exists(a[0])) {
                    m.send(s, "kb.not-found", MessageService.p("profile", a[0]));
                    return;
                }
                double value;
                try {
                    value = Double.parseDouble(a[2]);
                } catch (NumberFormatException e) {
                    m.send(s, "command.not-a-number", MessageService.p("value", a[2]));
                    return;
                }
                KnockbackProfile updated = api.knockback().profile(a[0]).with(a[1], value);
                if (updated == null) {
                    m.send(s, "kb.invalid-field", MessageService.p("fields", String.join(", ", KnockbackProfile.FIELDS)));
                    return;
                }
                api.knockback().put(updated);
                m.send(s, "kb.updated", MessageService.p("profile", updated.name()), MessageService.p("field", a[1]), MessageService.p("value", value));
            }, (s, a) -> a.length == 1 ? profiles.suggest(s, a) : a.length == 2 ? Arrays.asList(KnockbackProfile.FIELDS) : List.of()));
            sub(new Sub("create", "pvp.admin.kb", "create <name> [copy-from]", "Create a profile", false, 1, (s, a) -> {
                String name = a[0].toLowerCase(Locale.ROOT);
                if (api.knockback().exists(name)) {
                    m.send(s, "kb.exists", MessageService.p("profile", name));
                    return;
                }
                KnockbackProfile base = api.knockback().profile(a.length > 1 ? a[1] : null);
                api.knockback().put(base.rename(name));
                m.send(s, "kb.created", MessageService.p("profile", name));
            }, (s, a) -> a.length == 2 ? profiles.suggest(s, new String[]{a[1]}) : List.of()));
            sub(new Sub("delete", "pvp.admin.kb", "delete <profile>", "Delete a profile", false, 1, (s, a) -> {
                m.send(s, api.knockback().delete(a[0]) ? "kb.deleted" : "kb.cannot-delete", MessageService.p("profile", a[0]));
            }, profiles));
            sub(new Sub("default", "pvp.admin.kb", "default <profile>", "Set the default profile", false, 1, (s, a) -> {
                if (!api.knockback().exists(a[0])) {
                    m.send(s, "kb.not-found", MessageService.p("profile", a[0]));
                    return;
                }
                api.knockback().setDefault(a[0]);
                m.send(s, "kb.default-set", MessageService.p("profile", a[0]));
            }, profiles));
            sub(new Sub("kit", "pvp.admin.kb", "kit <kit> <profile>", "Assign a profile to a kit", false, 2, (s, a) -> {
                if (api.kits().get(a[0]).isEmpty() || !api.knockback().exists(a[1])) {
                    m.send(s, "kb.not-found", MessageService.p("profile", a[0] + "/" + a[1]));
                    return;
                }
                api.kits().setKnockback(a[0], a[1].toLowerCase(Locale.ROOT));
                m.send(s, "kb.kit-set", MessageService.p("kit", a[0]), MessageService.p("profile", a[1]));
            }, (s, a) -> a.length == 1 ? new ArrayList<>(api.kits().ids()) : a.length == 2 ? profiles.suggest(s, new String[]{a[1]}) : List.of()));
        }

        private static void sendProfile(MessageService m, CommandSender s, KnockbackProfile p) {
            m.send(s, "kb.profile", MessageService.p("profile", p.name()), MessageService.p("horizontal", p.horizontal()),
                    MessageService.p("vertical", p.vertical()), MessageService.p("friction", p.friction()),
                    MessageService.p("extra_horizontal", p.extraHorizontal()), MessageService.p("extra_vertical", p.extraVertical()),
                    MessageService.p("vertical_limit", p.verticalLimit()), MessageService.p("air", p.airHorizontalMultiplier()));
        }
    }

    private static final class RankCmd extends BaseCommand {
        RankCmd(PracticeApi api) {
            super(api.messages(), "rank", List.of("setrank"), "pvp.admin.rank", "Manage built-in ranks", "", false);
            MessageService m = api.messages();
            sub(new Sub("set", "pvp.admin.rank", "set <player> <rank>", "Set a player's rank", false, 2, (s, a) -> {
                Rank rank = api.ranks().rank(a[1]);
                if (rank == null) {
                    m.send(s, "rank.not-found", MessageService.p("rank", a[1]));
                    return;
                }
                api.profiles().resolve(a[0]).whenComplete((uuid, error) -> Tasks.sync(() -> {
                    if (error != null || uuid.isEmpty()) {
                        m.send(s, "command.player-never-joined", MessageService.p("player", a[0]));
                        return;
                    }
                    api.ranks().setRank(uuid.get(), rank.id());
                    Player online = Bukkit.getPlayer(uuid.get());
                    if (online != null) {
                        api.tab().updateName(online);
                        m.send(online, "rank.changed-notify", MessageService.p("rank", rank.displayName()));
                    }
                    m.send(s, "rank.set", MessageService.p("player", a[0]), MessageService.p("rank", rank.displayName()));
                }));
            }, (s, a) -> a.length == 1 ? Suggest.players(s) : a.length == 2 ? api.ranks().ranks().stream().map(Rank::id).toList() : List.of()));
            sub(new Sub("list", "pvp.admin.rank", "list", "List ranks", false, 0, (s, a) -> {
                for (Rank rank : api.ranks().ranks()) {
                    m.send(s, "rank.list-entry", MessageService.p("rank", rank.id()), MessageService.c("prefix", MessageService.mini().deserialize(rank.prefix())),
                            MessageService.p("weight", rank.weight()));
                }
            }, null));
            sub(new Sub("info", "pvp.admin.rank", "info <player>", "Show a player's rank", false, 1, (s, a) -> {
                Player target = Bukkit.getPlayerExact(a[0]);
                if (target == null) {
                    m.send(s, "command.player-not-found", MessageService.p("player", a[0]));
                    return;
                }
                m.send(s, "rank.info", MessageService.p("player", target.getName()), MessageService.p("rank", api.ranks().rankOf(target).displayName()),
                        MessageService.p("provider", api.ranks().usingLuckPerms() ? "LuckPerms" : "built-in"));
            }, (s, a) -> a.length == 1 ? Suggest.players(s) : List.of()));
        }
    }

    private static final class Alerts extends BaseCommand {
        private final PracticeApi api;

        Alerts(PracticeApi api) {
            super(api.messages(), "alerts", List.of(), "pvp.staff.alerts", "Toggle anticheat alerts", "", true);
            this.api = api;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            messages.send(sender, api.checks().toggleAlerts((Player) sender) ? "anticheat.alerts-on" : "anticheat.alerts-off");
        }
    }
}
