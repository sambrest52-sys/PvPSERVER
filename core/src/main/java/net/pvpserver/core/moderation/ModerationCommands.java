package net.pvpserver.core.moderation;

import net.pvpserver.core.command.BaseCommand;
import net.pvpserver.core.command.Suggest;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.profile.ProfileService;
import net.pvpserver.core.util.Tasks;
import net.pvpserver.core.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Moderation commands: ban, tempban, mute, tempmute, kick, warn, unban, unmute, history, freeze, vanish, staffchat,
 * report, reports.
 */
public final class ModerationCommands {

    private ModerationCommands() {
    }

    /**
     * Builds every moderation command.
     *
     * @param messages messages
     * @param punishments punishment service
     * @param staff staff service
     * @param reports report service
     * @param profiles profiles (name resolution)
     * @return commands
     */
    public static List<BaseCommand> create(MessageService messages, PunishmentService punishments, StaffService staff,
                                           ReportService reports, ProfileService profiles) {
        List<BaseCommand> list = new ArrayList<>();
        list.add(new Punish(messages, punishments, profiles, "ban", List.of(), PunishmentType.BAN, false, "Permanently ban a player"));
        list.add(new Punish(messages, punishments, profiles, "tempban", List.of("tban"), PunishmentType.BAN, true, "Temporarily ban a player"));
        list.add(new Punish(messages, punishments, profiles, "mute", List.of(), PunishmentType.MUTE, false, "Permanently mute a player"));
        list.add(new Punish(messages, punishments, profiles, "tempmute", List.of("tmute"), PunishmentType.MUTE, true, "Temporarily mute a player"));
        list.add(new Punish(messages, punishments, profiles, "kick", List.of(), PunishmentType.KICK, false, "Kick a player"));
        list.add(new Punish(messages, punishments, profiles, "warn", List.of(), PunishmentType.WARN, false, "Warn a player"));
        list.add(new Pardon(messages, punishments, profiles, "unban", List.of("pardon"), PunishmentType.BAN));
        list.add(new Pardon(messages, punishments, profiles, "unmute", List.of(), PunishmentType.MUTE));
        list.add(new History(messages, punishments, profiles));
        list.add(new Freeze(messages, staff));
        list.add(new Vanish(messages, staff));
        list.add(new StaffChat(messages, staff));
        list.add(new ReportCmd(messages, reports));
        list.add(new Reports(messages, reports));
        return list;
    }

    private static void resolve(MessageService messages, ProfileService profiles, CommandSender sender, String name,
                                BiConsumer<UUID, String> action) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            action.accept(online.getUniqueId(), online.getName());
            return;
        }
        profiles.resolve(name).whenComplete((uuid, error) -> Tasks.sync(() -> {
            if (error != null || uuid == null || uuid.isEmpty()) {
                messages.send(sender, "command.player-never-joined", MessageService.p("player", name));
                return;
            }
            action.accept(uuid.get(), name);
        }));
    }

    private static final class Punish extends BaseCommand {
        private final PunishmentService punishments;
        private final ProfileService profiles;
        private final PunishmentType type;
        private final boolean temporary;

        Punish(MessageService messages, PunishmentService punishments, ProfileService profiles, String label, List<String> aliases,
               PunishmentType type, boolean temporary, String description) {
            super(messages, label, aliases, "pvp.moderation." + label, description,
                    temporary ? "<player> <duration> [reason] [-s]" : type == PunishmentType.WARN ? "<player> <reason> [-s]" : "<player> [reason] [-s]",
                    false);
            this.punishments = punishments;
            this.profiles = profiles;
            this.type = type;
            this.temporary = temporary;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            int required = temporary || type == PunishmentType.WARN ? 2 : 1;
            if (args.length < required) {
                usage(sender);
                return;
            }
            List<String> rest = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
            boolean silent = rest.remove("-s");
            long duration = Long.MAX_VALUE;
            if (temporary) {
                duration = TimeUtil.parseDuration(rest.isEmpty() ? "" : rest.remove(0));
                if (duration <= 0) {
                    messages.send(sender, "moderation.invalid-duration");
                    return;
                }
            }
            String reason = rest.isEmpty() ? messages.raw("moderation.default-reason") : String.join(" ", rest);
            long finalDuration = duration;
            Player online = Bukkit.getPlayerExact(args[0]);
            if (online != null && online.hasPermission("pvp.moderation.exempt") && !(sender.hasPermission("pvp.moderation.override"))) {
                messages.send(sender, "moderation.exempt", MessageService.p("player", online.getName()));
                return;
            }
            if (type == PunishmentType.KICK && online == null) {
                messages.send(sender, "command.player-not-found", MessageService.p("player", args[0]));
                return;
            }
            resolve(messages, profiles, sender, args[0], (uuid, name) ->
                    punishments.punish(sender, uuid, name, type, finalDuration, reason, silent));
        }

        @Override
        protected List<String> onSuggest(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return Suggest.players(sender);
            }
            if (args.length == 2 && temporary) {
                return Suggest.durations();
            }
            return List.of("-s");
        }
    }

    private static final class Pardon extends BaseCommand {
        private final PunishmentService punishments;
        private final ProfileService profiles;
        private final PunishmentType type;

        Pardon(MessageService messages, PunishmentService punishments, ProfileService profiles, String label, List<String> aliases, PunishmentType type) {
            super(messages, label, aliases, "pvp.moderation." + label, "Remove a " + type.name().toLowerCase(Locale.ROOT), "<player> [reason]", false);
            this.punishments = punishments;
            this.profiles = profiles;
            this.type = type;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "No reason";
            resolve(messages, profiles, sender, args[0], (uuid, name) -> punishments.pardon(sender, uuid, name, type, reason));
        }

        @Override
        protected List<String> onSuggest(CommandSender sender, String[] args) {
            return args.length == 1 ? Suggest.players(sender) : List.of();
        }
    }

    private static final class History extends BaseCommand {
        private final PunishmentService punishments;
        private final ProfileService profiles;

        History(MessageService messages, PunishmentService punishments, ProfileService profiles) {
            super(messages, "history", List.of("punishments"), "pvp.moderation.history", "Show punishment history", "<player>", false);
            this.punishments = punishments;
            this.profiles = profiles;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            resolve(messages, profiles, sender, args[0], (uuid, name) -> punishments.history(uuid, 20).whenComplete((list, error) -> Tasks.sync(() -> {
                if (error != null) {
                    messages.send(sender, "command.error");
                    return;
                }
                messages.send(sender, "moderation.history-header", MessageService.p("player", name), MessageService.p("count", list.size()));
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                long now = System.currentTimeMillis();
                for (Punishment p : list) {
                    String status = !p.type().lasting() ? "" : p.inForce(now) ? "active" : p.active() ? "expired" : "removed by " + p.removedBy();
                    messages.send(sender, "moderation.history-entry", MessageService.p("date", format.format(new Date(p.createdAt()))),
                            MessageService.p("type", p.type().name()), MessageService.p("reason", p.reason()),
                            MessageService.p("staff", p.issuerName()), MessageService.p("status", status),
                            MessageService.p("duration", durationOf(p)));
                }
            })));
        }

        private static String durationOf(Punishment p) {
            if (!p.type().lasting()) {
                return "-";
            }
            return p.permanent() ? "permanent" : TimeUtil.formatDuration(p.expiresAt() - p.createdAt());
        }

        @Override
        protected List<String> onSuggest(CommandSender sender, String[] args) {
            return args.length == 1 ? Suggest.players(sender) : List.of();
        }
    }

    private static final class Freeze extends BaseCommand {
        private final StaffService staff;

        Freeze(MessageService messages, StaffService staff) {
            super(messages, "freeze", List.of("ss"), "pvp.staff.freeze", "Freeze a player for a screenshare", "<player>", false);
            this.staff = staff;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                messages.send(sender, "command.player-not-found", MessageService.p("player", args[0]));
                return;
            }
            boolean frozen = staff.toggleFreeze(target);
            staff.alert(frozen ? "staff.froze" : "staff.unfroze", MessageService.p("player", target.getName()), MessageService.p("staff", sender.getName()));
        }

        @Override
        protected List<String> onSuggest(CommandSender sender, String[] args) {
            return args.length == 1 ? Suggest.players(sender) : List.of();
        }
    }

    private static final class Vanish extends BaseCommand {
        private final StaffService staff;

        Vanish(MessageService messages, StaffService staff) {
            super(messages, "vanish", List.of("v"), "pvp.staff.vanish", "Toggle vanish", "", true);
            this.staff = staff;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            boolean vanished = staff.toggleVanish((Player) sender);
            messages.send(sender, vanished ? "staff.vanished" : "staff.unvanished");
        }
    }

    private static final class StaffChat extends BaseCommand {
        private final StaffService staff;

        StaffChat(MessageService messages, StaffService staff) {
            super(messages, "staffchat", List.of("sc"), "pvp.staff.chat", "Talk in or toggle staff chat", "[message]", false);
            this.staff = staff;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            if (args.length == 0) {
                if (!(sender instanceof Player player)) {
                    usage(sender);
                    return;
                }
                messages.send(sender, staff.toggleStaffChat(player) ? "staff.chat-on" : "staff.chat-off");
                return;
            }
            staff.staffChat(sender.getName(), String.join(" ", args));
        }
    }

    private static final class ReportCmd extends BaseCommand {
        private final ReportService reports;

        ReportCmd(MessageService messages, ReportService reports) {
            super(messages, "report", List.of(), "pvp.command.report", "Report a player to staff", "<player> <reason>", true);
            this.reports = reports;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            if (args.length < 2) {
                usage(sender);
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !((Player) sender).canSee(target)) {
                messages.send(sender, "command.player-not-found", MessageService.p("player", args[0]));
                return;
            }
            if (target == sender) {
                messages.send(sender, "report.self");
                return;
            }
            reports.report((Player) sender, target, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        }

        @Override
        protected List<String> onSuggest(CommandSender sender, String[] args) {
            return args.length == 1 ? Suggest.players(sender) : List.of("cheating", "kill aura", "reach", "autoclicker", "chat abuse");
        }
    }

    private static final class Reports extends BaseCommand {
        private final ReportService reports;

        Reports(MessageService messages, ReportService reports) {
            super(messages, "reports", List.of(), "pvp.staff.reports", "Show recent reports", "", false);
            this.reports = reports;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            reports.recent(15).whenComplete((list, error) -> Tasks.sync(() -> {
                if (error != null) {
                    messages.send(sender, "command.error");
                    return;
                }
                messages.send(sender, "report.list-header", MessageService.p("count", list.size()));
                SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm");
                for (Report r : list) {
                    messages.send(sender, "report.list-entry", MessageService.p("date", format.format(new Date(r.createdAt()))),
                            MessageService.p("reporter", r.reporterName()), MessageService.p("player", r.targetName()),
                            MessageService.p("reason", r.reason()));
                }
            }));
        }
    }
}
