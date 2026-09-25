package net.pvpserver.core.party;

import net.pvpserver.core.api.BridgeRegistry;
import net.pvpserver.core.api.bridge.MatchBridge;
import net.pvpserver.core.command.BaseCommand;
import net.pvpserver.core.command.SubCommand;
import net.pvpserver.core.command.Suggest;
import net.pvpserver.core.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /party} with create, invite, accept, join, leave, kick, promote, disband, open, close, info, chat, fight.
 */
public final class PartyCommand extends BaseCommand {

    private final PartyService parties;
    private final BridgeRegistry bridges;

    /**
     * @param messages messages
     * @param parties party service
     * @param bridges bridges (party fights)
     */
    public PartyCommand(MessageService messages, PartyService parties, BridgeRegistry bridges) {
        super(messages, "party", List.of("p"), "pvp.command.party", "Create and manage parties", "", true);
        this.parties = parties;
        this.bridges = bridges;
        sub(new Simple("create", "create", "Create a party", 0, (p, a) -> parties.create(p)));
        sub(new PlayerArg("invite", "invite <player>", "Invite a player", (p, target) -> parties.invite(p, target)));
        sub(new PlayerArg("accept", "accept <leader>", "Accept an invite", (p, target) -> parties.join(p, target)));
        sub(new PlayerArg("join", "join <leader>", "Join an open party", (p, target) -> parties.join(p, target)));
        sub(new Simple("leave", "leave", "Leave your party", 0, (p, a) -> parties.leave(p, false)));
        sub(new MemberArg("kick", "kick <member>", "Kick a member", (p, name) -> parties.kick(p, name)));
        sub(new MemberArg("promote", "promote <member>", "Make a member leader", (p, name) -> parties.promote(p, name)));
        sub(new Simple("disband", "disband", "Disband the party", 0, (p, a) -> parties.disband(p)));
        sub(new Simple("open", "open", "Allow anyone to join", 0, (p, a) -> parties.setOpen(p, true)));
        sub(new Simple("close", "close", "Invite only", 0, (p, a) -> parties.setOpen(p, false)));
        sub(new Simple("info", "info [player]", "Show party members", 0, this::info));
        sub(new Simple("chat", "chat <message>", "Talk to your party", 1, (p, a) -> parties.chat(p, String.join(" ", a))));
        sub(new Simple("fight", "fight", "Start a party fight", 0, this::fight));
    }

    @Override
    protected void onCommand(CommandSender sender, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target != null && target != player) {
                parties.invite(player, target);
                return;
            }
        }
        sendHelp(sender);
    }

    private void info(Player player, String[] args) {
        Optional<Party> party;
        if (args.length > 0) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                messages.send(player, "command.player-not-found", MessageService.p("player", args[0]));
                return;
            }
            party = parties.partyOf(target);
        } else {
            party = parties.partyOf(player);
        }
        if (party.isEmpty()) {
            messages.send(player, "party.not-in-party");
            return;
        }
        parties.info(player, party.get());
    }

    private void fight(Player player, String[] args) {
        Optional<Party> party = parties.partyOf(player);
        if (party.isEmpty()) {
            messages.send(player, "party.not-in-party");
            return;
        }
        if (!party.get().isLeader(player.getUniqueId())) {
            messages.send(player, "party.not-leader");
            return;
        }
        Optional<MatchBridge> matches = bridges.get(MatchBridge.class);
        if (matches.isEmpty()) {
            messages.send(player, "general.feature-unavailable");
            return;
        }
        matches.get().openPartyFightMenu(player);
    }

    @FunctionalInterface
    private interface PlayerAction {
        void run(Player player, String[] args);
    }

    @FunctionalInterface
    private interface TargetAction {
        void run(Player player, Player target);
    }

    @FunctionalInterface
    private interface NameAction {
        void run(Player player, String name);
    }

    private final class Simple extends SubCommand {
        private final PlayerAction action;

        Simple(String name, String usage, String description, int minArgs, PlayerAction action) {
            super(name, List.of(), null, usage, description, true, minArgs);
            this.action = action;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            action.run((Player) sender, args);
        }

        @Override
        public List<String> suggest(CommandSender sender, String[] args) {
            return name().equals("info") && args.length == 1 ? Suggest.players(sender) : List.of();
        }
    }

    private final class PlayerArg extends SubCommand {
        private final TargetAction action;

        PlayerArg(String name, String usage, String description, TargetAction action) {
            super(name, List.of(), null, usage, description, true, 1);
            this.action = action;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            Player player = (Player) sender;
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null || !player.canSee(target)) {
                messages.send(player, "command.player-not-found", MessageService.p("player", args[0]));
                return;
            }
            action.run(player, target);
        }

        @Override
        public List<String> suggest(CommandSender sender, String[] args) {
            return args.length == 1 ? Suggest.players(sender) : List.of();
        }
    }

    private final class MemberArg extends SubCommand {
        private final NameAction action;

        MemberArg(String name, String usage, String description, NameAction action) {
            super(name, List.of(), null, usage, description, true, 1);
            this.action = action;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            action.run((Player) sender, args[0]);
        }

        @Override
        public List<String> suggest(CommandSender sender, String[] args) {
            if (args.length != 1 || !(sender instanceof Player player)) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            parties.partyOf(player).ifPresent(party -> {
                for (UUID member : party.members()) {
                    Player online = Bukkit.getPlayer(member);
                    if (online != null && online != player) {
                        names.add(online.getName());
                    }
                }
            });
            return names;
        }
    }

    /**
     * {@code /pc <message>} shortcut for party chat.
     */
    public static final class PartyChatCommand extends BaseCommand {
        private final PartyService parties;

        /**
         * @param messages messages
         * @param parties parties
         */
        public PartyChatCommand(MessageService messages, PartyService parties) {
            super(messages, "pc", List.of("partychat"), "pvp.command.party", "Party chat", "<message>", true);
            this.parties = parties;
        }

        @Override
        protected void onCommand(CommandSender sender, String[] args) {
            if (args.length == 0) {
                usage(sender);
                return;
            }
            parties.chat((Player) sender, String.join(" ", args));
        }
    }
}
