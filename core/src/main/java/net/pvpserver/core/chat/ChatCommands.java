package net.pvpserver.core.chat;

import net.pvpserver.core.command.BaseCommand;
import net.pvpserver.core.command.Suggest;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.ProfileService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * {@code /msg}, {@code /r}, {@code /ignore}, {@code /unignore}, {@code /chat}, {@code /socialspy}.
 */
public final class ChatCommands {

    private ChatCommands() {
    }

    /**
     * @param messages messages
     * @param chat chat service
     * @param profiles profiles
     * @return commands
     */
    public static List<BaseCommand> create(MessageService messages, ChatService chat, ProfileService profiles) {
        return List.of(new Msg(messages, chat), new Reply(messages, chat, profiles), new Ignore(messages, profiles),
                new Unignore(messages, profiles), new ChatAdmin(messages, chat), new SocialSpy(messages, chat));
    }

    private static final class Msg extends BaseCommand {
        private final ChatService chat;

        Msg(MessageService messages, ChatService chat) {
            super(messages, "msg", List.of("message", "tell", "whisper", "w", "m", "pm"), "pvp.command.msg", "Private message a player", "<player> <message>", true);
            this.chat = chat;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            if (args.length < 2) {
                usage(sender);
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                messages.send(sender, "command.player-not-found", MessageService.p("player", args[0]));
                return;
            }
            chat.privateMessage((Player) sender, target, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        }

        @Override
        protected List<String> onSuggest(CommandSender sender, String[] args) {
            return args.length == 1 ? Suggest.players(sender) : List.of();
        }
    }

    private static final class Reply extends BaseCommand {
        private final ChatService chat;
        private final ProfileService profiles;

        Reply(MessageService messages, ChatService chat, ProfileService profiles) {
            super(messages, "reply", List.of("r"), "pvp.command.msg", "Reply to your last conversation", "<message>", true);
            this.chat = chat;
            this.profiles = profiles;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            PlayerProfile profile = profiles.get((Player) sender);
            UUID last = profile == null ? null : profile.lastMessaged();
            Player target = last == null ? null : Bukkit.getPlayer(last);
            if (target == null) {
                messages.send(sender, "chat.no-reply");
                return;
            }
            chat.privateMessage((Player) sender, target, String.join(" ", args));
        }
    }

    private static final class Ignore extends BaseCommand {
        private final ProfileService profiles;

        Ignore(MessageService messages, ProfileService profiles) {
            super(messages, "ignore", List.of(), "pvp.command.ignore", "Ignore a player or list ignored players", "<player|list>", true);
            this.profiles = profiles;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            Player player = (Player) sender;
            PlayerProfile profile = profiles.get(player);
            if (profile == null) {
                return;
            }
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                List<String> names = new ArrayList<>();
                for (UUID id : profile.ignored()) {
                    Player online = Bukkit.getPlayer(id);
                    names.add(online != null ? online.getName() : String.valueOf(Bukkit.getOfflinePlayer(id).getName()));
                }
                messages.send(player, "chat.ignore-list", MessageService.p("players", names.isEmpty() ? "-" : String.join(", ", names)));
                return;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                messages.send(sender, "command.player-not-found", MessageService.p("player", args[0]));
                return;
            }
            if (target == player) {
                messages.send(sender, "chat.ignore-self");
                return;
            }
            if (target.hasPermission("pvp.staff")) {
                messages.send(sender, "chat.ignore-staff");
                return;
            }
            profile.ignored().add(target.getUniqueId());
            profile.markDirty();
            messages.send(sender, "chat.ignored", MessageService.p("player", target.getName()));
        }

        @Override
        protected List<String> onSuggest(CommandSender sender, String[] args) {
            if (args.length != 1) {
                return List.of();
            }
            List<String> list = new ArrayList<>(Suggest.players(sender));
            list.add("list");
            return list;
        }
    }

    private static final class Unignore extends BaseCommand {
        private final ProfileService profiles;

        Unignore(MessageService messages, ProfileService profiles) {
            super(messages, "unignore", List.of(), "pvp.command.ignore", "Stop ignoring a player", "<player>", true);
            this.profiles = profiles;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            if (args.length < 1) {
                usage(sender);
                return;
            }
            PlayerProfile profile = profiles.get((Player) sender);
            if (profile == null) {
                return;
            }
            UUID found = null;
            for (UUID id : profile.ignored()) {
                String name = Bukkit.getOfflinePlayer(id).getName();
                if (name != null && name.equalsIgnoreCase(args[0])) {
                    found = id;
                }
            }
            if (found == null) {
                messages.send(sender, "chat.not-ignored", MessageService.p("player", args[0]));
                return;
            }
            profile.ignored().remove(found);
            profile.markDirty();
            messages.send(sender, "chat.unignored", MessageService.p("player", args[0]));
        }

        @Override
        protected List<String> onSuggest(CommandSender sender, String[] args) {
            if (args.length != 1 || !(sender instanceof Player player)) {
                return List.of();
            }
            PlayerProfile profile = profiles.get(player);
            List<String> names = new ArrayList<>();
            if (profile != null) {
                for (UUID id : profile.ignored()) {
                    String name = Bukkit.getOfflinePlayer(id).getName();
                    if (name != null) {
                        names.add(name);
                    }
                }
            }
            return names;
        }
    }

    private static final class ChatAdmin extends BaseCommand {
        private final ChatService chat;

        ChatAdmin(MessageService messages, ChatService chat) {
            super(messages, "chat", List.of(), "pvp.chat.admin", "Chat moderation (slow, clear, mute)", "<slow <seconds>|clear|mute|unmute>", false);
            this.chat = chat;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            if (args.length == 0) {
                usage(sender);
                return;
            }
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "slow" -> {
                    int seconds = 0;
                    if (args.length > 1) {
                        try {
                            seconds = Integer.parseInt(args[1]);
                        } catch (NumberFormatException e) {
                            usage(sender);
                            return;
                        }
                    }
                    chat.slowMode(seconds);
                    Bukkit.getServer().sendMessage(messages.get(seconds > 0 ? "chat.slow-enabled" : "chat.slow-disabled",
                            MessageService.p("seconds", seconds), MessageService.p("staff", sender.getName())));
                }
                case "clear" -> chat.clearChat(sender.getName());
                case "mute", "lock" -> {
                    chat.globalMute(true);
                    Bukkit.getServer().sendMessage(messages.get("chat.muted-global", MessageService.p("staff", sender.getName())));
                }
                case "unmute", "unlock" -> {
                    chat.globalMute(false);
                    Bukkit.getServer().sendMessage(messages.get("chat.unmuted-global", MessageService.p("staff", sender.getName())));
                }
                default -> usage(sender);
            }
        }

        @Override
        protected List<String> onSuggest(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return List.of("slow", "clear", "mute", "unmute");
            }
            return args.length == 2 && args[0].equalsIgnoreCase("slow") ? List.of("0", "3", "5", "10", "30") : List.of();
        }
    }

    private static final class SocialSpy extends BaseCommand {
        private final ChatService chat;

        SocialSpy(MessageService messages, ChatService chat) {
            super(messages, "socialspy", List.of("spy"), "pvp.staff.socialspy", "Toggle private message spying", "", true);
            this.chat = chat;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            messages.send(sender, chat.toggleSocialSpy((Player) sender) ? "chat.spy-on" : "chat.spy-off");
        }
    }
}
