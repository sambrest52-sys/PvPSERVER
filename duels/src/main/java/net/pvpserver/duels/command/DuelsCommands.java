package net.pvpserver.duels.command;

import net.pvpserver.core.command.BaseCommand;
import net.pvpserver.core.command.SubCommand;
import net.pvpserver.core.command.Suggest;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.core.util.TimeUtil;
import net.pvpserver.duels.PvPDuels;
import net.pvpserver.duels.match.InventorySnapshot;
import net.pvpserver.duels.match.Match;
import net.pvpserver.duels.menu.DuelOptionsMenu;
import net.pvpserver.duels.menu.SnapshotMenu;
import net.pvpserver.duels.queue.QueueMode;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * /duel, /rematch, /spectate, /leave, /inventory, /queue, /matches.
 */
public final class DuelsCommands {

    private DuelsCommands() {
    }

    /**
     * @param plugin duels plugin
     * @return commands
     */
    public static List<BaseCommand> create(PvPDuels plugin) {
        return List.of(new Duel(plugin), new Rematch(plugin), new Spectate(plugin), new Leave(plugin), new Inventory(plugin),
                new Queue(plugin), new Matches(plugin));
    }

    private static final class Duel extends BaseCommand {
        private final PvPDuels plugin;

        Duel(PvPDuels plugin) {
            super(plugin.messages(), "duel", List.of("1v1", "fight", "challenge"), "pvp.command.duel", "Challenge a player", "<player> [kit]", true);
            this.plugin = plugin;
            sub(new SubCommand("accept", List.of(), null, "accept <player>", "Accept a duel", true, 1) {
                @Override
                public void execute(CommandSender sender, String[] args) {
                    plugin.requests().accept((Player) sender, args[0]);
                }

                @Override
                public List<String> suggest(CommandSender sender, String[] args) {
                    return plugin.requests().pendingSenders((Player) sender);
                }
            });
            sub(new SubCommand("deny", List.of("decline"), null, "deny <player>", "Deny a duel", true, 1) {
                @Override
                public void execute(CommandSender sender, String[] args) {
                    plugin.requests().deny((Player) sender, args[0]);
                }

                @Override
                public List<String> suggest(CommandSender sender, String[] args) {
                    return plugin.requests().pendingSenders((Player) sender);
                }
            });
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            if (args.length == 0) {
                usage(sender);
                return;
            }
            Player player = (Player) sender;
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !player.canSee(target)) {
                messages.send(sender, "command.player-not-found", MessageService.p("player", args[0]));
                return;
            }
            if (target == player) {
                messages.send(sender, "duel.self");
                return;
            }
            if (args.length > 1) {
                Optional<Kit> kit = plugin.api().kits().get(args[1]);
                if (kit.isEmpty() || !kit.get().unranked()) {
                    messages.send(sender, "duel.unknown-kit", MessageService.p("kit", args[1]));
                    return;
                }
                new DuelOptionsMenu(plugin, player, target.getUniqueId(), kit.get()).open();
                return;
            }
            plugin.bridge().openDuelMenu(player, target);
        }

        @Override
        protected List<String> onSuggest(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return Suggest.players(sender);
            }
            return args.length == 2 ? new ArrayList<>(plugin.api().kits().queueKits(false).stream().map(Kit::id).toList()) : List.of();
        }
    }

    private static final class Rematch extends BaseCommand {
        private final PvPDuels plugin;

        Rematch(PvPDuels plugin) {
            super(plugin.messages(), "rematch", List.of(), "pvp.command.duel", "Rematch your last opponent", "", true);
            this.plugin = plugin;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            plugin.requests().rematch((Player) sender);
        }
    }

    private static final class Spectate extends BaseCommand {
        private final PvPDuels plugin;

        Spectate(PvPDuels plugin) {
            super(plugin.messages(), "spectate", List.of("spec", "watch"), "pvp.command.spectate", "Spectate a match", "<player|leave>", true);
            this.plugin = plugin;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            Player player = (Player) sender;
            if (args.length == 0) {
                plugin.bridge().openSpectateMenu(player);
                return;
            }
            if (args[0].equalsIgnoreCase("leave") || args[0].equalsIgnoreCase("stop")) {
                plugin.spectators().stop(player, true);
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !player.canSee(target)) {
                messages.send(sender, "command.player-not-found", MessageService.p("player", args[0]));
                return;
            }
            plugin.spectators().spectate(player, target);
        }

        @Override
        protected List<String> onSuggest(CommandSender sender, String[] args) {
            if (args.length != 1) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            names.add("leave");
            for (Match match : plugin.matches().matches()) {
                match.onlinePlayers().forEach(p -> names.add(p.getName()));
            }
            return names;
        }
    }

    private static final class Leave extends BaseCommand {
        private final PvPDuels plugin;

        Leave(PvPDuels plugin) {
            super(plugin.messages(), "leave", List.of("forfeit", "ff"), "pvp.command.leave", "Forfeit your match or stop spectating", "", true);
            this.plugin = plugin;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            Player player = (Player) sender;
            PlayerState state = plugin.api().states().get(player);
            switch (state) {
                case MATCH -> plugin.matches().forfeit(player, false);
                case SPECTATING -> plugin.spectators().stop(player, true);
                case QUEUE -> plugin.queues().leave(player, true);
                default -> messages.send(sender, "match.nothing-to-leave");
            }
        }
    }

    private static final class Inventory extends BaseCommand {
        private final PvPDuels plugin;

        Inventory(PvPDuels plugin) {
            super(plugin.messages(), "inventory", List.of("inv", "viewinv"), "pvp.command.inventory", "View a post-match inventory", "<id>", true);
            this.plugin = plugin;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            if (args.length == 0) {
                usage(sender);
                return;
            }
            InventorySnapshot snapshot;
            try {
                snapshot = plugin.matches().snapshots().get(UUID.fromString(args[0]));
            } catch (IllegalArgumentException e) {
                snapshot = null;
            }
            if (snapshot == null) {
                messages.send(sender, "inventory.expired");
                return;
            }
            new SnapshotMenu(plugin, (Player) sender, snapshot).open();
        }
    }

    private static final class Queue extends BaseCommand {
        private final PvPDuels plugin;

        Queue(PvPDuels plugin) {
            super(plugin.messages(), "queue", List.of("q"), "pvp.command.queue", "Join or leave a queue", "", true);
            this.plugin = plugin;
            sub(new SubCommand("leave", List.of(), null, "leave", "Leave your queue", true, 0) {
                @Override
                public void execute(CommandSender sender, String[] args) {
                    if (!plugin.queues().leave((Player) sender, true)) {
                        plugin.messages().send(sender, "queue.not-queued");
                    }
                }
            });
            sub(new SubCommand("join", List.of(), null, "join <kit> <ranked|unranked> [1v1|2v2]", "Join a queue", true, 2) {
                @Override
                public void execute(CommandSender sender, String[] args) {
                    Optional<Kit> kit = plugin.api().kits().get(args[0]);
                    if (kit.isEmpty()) {
                        plugin.messages().send(sender, "duel.unknown-kit", MessageService.p("kit", args[0]));
                        return;
                    }
                    boolean ranked = args[1].equalsIgnoreCase("ranked");
                    QueueMode mode = args.length > 2 && args[2].equalsIgnoreCase("2v2") ? QueueMode.TWO_V_TWO : QueueMode.ONE_V_ONE;
                    plugin.queues().join((Player) sender, kit.get(), ranked, mode);
                }

                @Override
                public List<String> suggest(CommandSender sender, String[] args) {
                    return switch (args.length) {
                        case 1 -> plugin.api().kits().queueKits(false).stream().map(Kit::id).toList();
                        case 2 -> List.of("ranked", "unranked");
                        case 3 -> List.of("1v1", "2v2");
                        default -> List.of();
                    };
                }
            });
            sub(new SubCommand("ranked", List.of(), null, "ranked", "Open the ranked queue menu", true, 0) {
                @Override
                public void execute(CommandSender sender, String[] args) {
                    plugin.bridge().openQueueMenu((Player) sender, true);
                }
            });
            sub(new SubCommand("unranked", List.of(), null, "unranked", "Open the unranked queue menu", true, 0) {
                @Override
                public void execute(CommandSender sender, String[] args) {
                    plugin.bridge().openQueueMenu((Player) sender, false);
                }
            });
        }
    }

    private static final class Matches extends BaseCommand {
        private final PvPDuels plugin;

        Matches(PvPDuels plugin) {
            super(plugin.messages(), "matches", List.of("match"), "pvp.admin.matches", "List or cancel matches", "[cancel <player>]", false);
            this.plugin = plugin;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("cancel")) {
                Player target = Bukkit.getPlayerExact(args[1]);
                Match match = target == null ? null : plugin.matches().matchOf(target.getUniqueId());
                if (match == null) {
                    messages.send(sender, "spectate.not-in-match", MessageService.p("player", args[1]));
                    return;
                }
                plugin.matches().cancel(match);
                messages.send(sender, "match.admin-cancelled", MessageService.p("match", match.description()));
                return;
            }
            messages.send(sender, "match.list-header", MessageService.p("count", plugin.matches().matches().size()));
            for (Match match : plugin.matches().matches()) {
                messages.send(sender, "match.list-entry", MessageService.p("match", match.description()),
                        MessageService.c("kit", match.kit().name()), MessageService.p("state", match.state().name().toLowerCase(Locale.ROOT)),
                        MessageService.p("duration", TimeUtil.formatClock(match.durationMillis())),
                        MessageService.p("arena", match.arena() == null ? "-" : match.arena().arena().name()));
            }
        }

        @Override
        protected List<String> onSuggest(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return List.of("cancel");
            }
            List<String> names = new ArrayList<>();
            for (Match match : plugin.matches().matches()) {
                match.onlinePlayers().forEach(p -> names.add(p.getName()));
            }
            return names;
        }
    }
}
