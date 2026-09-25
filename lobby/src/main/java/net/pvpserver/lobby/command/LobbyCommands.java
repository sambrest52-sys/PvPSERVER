package net.pvpserver.lobby.command;

import net.pvpserver.core.command.BaseCommand;
import net.pvpserver.core.command.Suggest;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.core.stats.LeaderboardService;
import net.pvpserver.core.stats.StatField;
import net.pvpserver.core.util.Tasks;
import net.pvpserver.lobby.PvPLobby;
import net.pvpserver.lobby.menu.CosmeticsMenu;
import net.pvpserver.lobby.menu.LeaderboardMenu;
import net.pvpserver.lobby.menu.SettingsMenu;
import net.pvpserver.lobby.menu.StatsMenu;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Lobby commands: spawn, setspawn, settings, stats, leaderboard, cosmetics, kiteditor, fly, lbholo.
 */
public final class LobbyCommands {

    private LobbyCommands() {
    }

    /**
     * @param plugin lobby plugin
     * @return commands
     */
    public static List<BaseCommand> create(PvPLobby plugin) {
        MessageService m = plugin.messages();
        List<BaseCommand> commands = new ArrayList<>();
        commands.add(new Simple(m, "spawn", List.of("lobby", "hub", "l"), "pvp.command.spawn", "Return to the lobby", p -> {
            if (plugin.api().combat().tags().isTagged(p) && !p.hasPermission("pvp.staff")) {
                m.send(p, "lobby.combat-tagged");
                return;
            }
            switch (plugin.api().states().get(p)) {
                case MATCH -> m.send(p, "lobby.in-match");
                case FFA -> plugin.api().bridges().get(net.pvpserver.core.api.bridge.FfaBridge.class).ifPresentOrElse(f -> f.leave(p),
                        () -> plugin.lobby().sendToLobby(p));
                case SPECTATING -> p.performCommand("spectate leave");
                case QUEUE -> {
                    plugin.api().bridges().get(net.pvpserver.core.api.bridge.QueueBridge.class).ifPresent(q -> q.leaveQueue(p));
                    plugin.lobby().sendToLobby(p);
                }
                default -> plugin.lobby().sendToLobby(p);
            }
        }));
        commands.add(new Simple(m, "setspawn", List.of("setlobby"), "pvp.admin.lobby", "Set the lobby spawn", p -> {
            plugin.lobby().setSpawn(p.getLocation());
            m.send(p, "lobby.spawn-set");
        }));
        commands.add(new Simple(m, "settings", List.of("options", "prefs"), "pvp.command.settings", "Open your settings",
                p -> new SettingsMenu(plugin, p).open()));
        commands.add(new Simple(m, "cosmetics", List.of("cosmetic"), "pvp.command.cosmetics", "Choose cosmetics",
                p -> new CosmeticsMenu(plugin, p).open()));
        commands.add(new Simple(m, "leaderboard", List.of("leaderboards", "lb", "top"), "pvp.command.leaderboard", "View leaderboards",
                p -> new LeaderboardMenu(plugin, p).open()));
        commands.add(new Simple(m, "kiteditor", List.of("editkit", "editkits"), "pvp.command.kiteditor", "Edit your kit layouts",
                p -> plugin.kitEditor().openKitEditor(p)));
        commands.add(new Simple(m, "fly", List.of(), "pvp.lobby.fly", "Toggle lobby flight", p -> {
            if (!plugin.api().states().is(p, PlayerState.LOBBY, PlayerState.QUEUE)) {
                m.send(p, "lobby.fly-lobby-only");
                return;
            }
            boolean enabled = plugin.toggleFly(p);
            m.send(p, enabled ? "lobby.fly-on" : "lobby.fly-off");
        }));
        commands.add(new Stats(plugin));
        commands.add(new Holograms(plugin));
        return commands;
    }

    private static final class Simple extends BaseCommand {
        private final java.util.function.Consumer<Player> action;

        Simple(MessageService messages, String label, List<String> aliases, String permission, String description,
               java.util.function.Consumer<Player> action) {
            super(messages, label, aliases, permission, description, "", true);
            this.action = action;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            action.accept((Player) sender);
        }
    }

    private static final class Stats extends BaseCommand {
        private final PvPLobby plugin;

        Stats(PvPLobby plugin) {
            super(plugin.messages(), "stats", List.of("statistics", "profile"), "pvp.command.stats", "View statistics", "[player]", true);
            this.plugin = plugin;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            Player player = (Player) sender;
            if (args.length == 0) {
                var profile = plugin.api().profiles().get(player);
                if (profile != null) {
                    new StatsMenu(plugin, player, profile).open();
                }
                return;
            }
            String name = args[0];
            plugin.api().profiles().resolve(name).thenCompose(uuid -> {
                if (uuid.isEmpty()) {
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }
                return plugin.api().profiles().loadOffline(uuid.get(), name);
            }).whenComplete((profile, error) -> Tasks.sync(() -> {
                if (error != null || profile == null) {
                    messages.send(player, "command.player-never-joined", MessageService.p("player", name));
                    return;
                }
                if (player.isOnline()) {
                    new StatsMenu(plugin, player, profile).open();
                }
            }));
        }

        @Override
        protected List<String> onSuggest(CommandSender sender, String[] args) {
            return args.length == 1 ? Suggest.players(sender) : List.of();
        }
    }

    private static final class Holograms extends BaseCommand {
        private final PvPLobby plugin;

        Holograms(PvPLobby plugin) {
            super(plugin.messages(), "lbholo", List.of("leaderboardhologram"), "pvp.admin.lobby", "Manage leaderboard holograms",
                    "<create <id> <kit|global> <stat>|delete <id>|list|reload>", true);
            this.plugin = plugin;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            Player player = (Player) sender;
            if (args.length == 0) {
                usage(sender);
                return;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create" -> {
                    if (args.length < 4) {
                        usage(sender);
                        return;
                    }
                    StatField field = StatField.parse(args[3]);
                    String kit = args[2].toLowerCase(Locale.ROOT);
                    if (field == null || (!kit.equals(LeaderboardService.GLOBAL) && plugin.api().kits().get(kit).isEmpty())) {
                        messages.send(sender, "hologram.invalid");
                        return;
                    }
                    plugin.holograms().create(args[1], player.getLocation().add(0, 2.5, 0), kit, field);
                    messages.send(sender, "hologram.created", MessageService.p("id", args[1]));
                }
                case "delete", "remove" -> {
                    if (args.length < 2) {
                        usage(sender);
                        return;
                    }
                    messages.send(sender, plugin.holograms().delete(args[1]) ? "hologram.deleted" : "hologram.not-found",
                            MessageService.p("id", args[1]));
                }
                case "list" -> messages.send(sender, "hologram.list", MessageService.p("ids", String.join(", ", plugin.holograms().ids())));
                case "reload" -> {
                    plugin.holograms().reload();
                    messages.send(sender, "hologram.reloaded");
                }
                default -> usage(sender);
            }
        }

        @Override
        protected List<String> onSuggest(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return List.of("create", "delete", "list", "reload");
            }
            if (args[0].equalsIgnoreCase("delete") && args.length == 2) {
                return new ArrayList<>(plugin.holograms().ids());
            }
            if (args[0].equalsIgnoreCase("create")) {
                if (args.length == 3) {
                    List<String> kits = new ArrayList<>(plugin.api().kits().ids());
                    kits.add(LeaderboardService.GLOBAL);
                    return kits;
                }
                if (args.length == 4) {
                    return Arrays.stream(StatField.values()).map(Enum::name).toList();
                }
            }
            return List.of();
        }
    }
}
