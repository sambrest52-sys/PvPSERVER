package net.pvpserver.ffa;

import net.pvpserver.core.command.BaseCommand;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.state.PlayerState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /ffa [arena|leave|list]}.
 */
public final class FfaCommand extends BaseCommand {

    private final PvPFFA plugin;

    /**
     * @param plugin FFA plugin
     */
    public FfaCommand(PvPFFA plugin) {
        super(plugin.messages(), "ffa", List.of("freeforall"), "pvp.command.ffa", "Join or leave FFA", "[arena|leave|list]", true);
        this.plugin = plugin;
    }

    @Override
    protected void onCommand(CommandSender sender, String[] args) {
        Player player = (Player) sender;
        if (args.length == 0) {
            plugin.ffa().openFfaMenu(player);
            return;
        }
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "leave", "quit" -> {
                if (plugin.ffa().arenaOf(player) == null) {
                    messages.send(player, "ffa.not-in-ffa");
                    return;
                }
                if (plugin.api().combat().tags().isTagged(player) && !player.hasPermission("pvp.staff")) {
                    messages.send(player, "ffa.command-blocked", MessageService.p("seconds",
                            plugin.api().combat().tags().remaining(player) / 1000 + 1));
                    return;
                }
                plugin.ffa().leave(player);
            }
            case "list" -> {
                for (FfaArena arena : plugin.ffa().arenas()) {
                    messages.send(player, "ffa.list-entry", MessageService.p("arena", arena.id()),
                            MessageService.c("kit", arena.kit().name()), MessageService.p("players", arena.players().size()),
                            MessageService.p("type", arena.ranked() ? "Ranked" : "Unranked"));
                }
            }
            default -> {
                FfaArena arena = plugin.ffa().arena(args[0]);
                if (arena == null) {
                    messages.send(player, "ffa.unknown-arena", MessageService.p("arena", args[0]));
                    return;
                }
                if (plugin.api().states().is(player, PlayerState.FFA) && plugin.api().combat().tags().isTagged(player)) {
                    messages.send(player, "ffa.command-blocked", MessageService.p("seconds",
                            plugin.api().combat().tags().remaining(player) / 1000 + 1));
                    return;
                }
                plugin.ffa().join(player, arena);
            }
        }
    }

    @Override
    protected List<String> onSuggest(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("leave", "list"));
        plugin.ffa().arenas().forEach(arena -> options.add(arena.id()));
        return options;
    }
}
