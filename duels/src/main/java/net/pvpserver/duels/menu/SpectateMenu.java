package net.pvpserver.duels.menu;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.gui.Button;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.gui.PaginatedMenu;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.util.TimeUtil;
import net.pvpserver.duels.PvPDuels;
import net.pvpserver.duels.match.Match;
import net.pvpserver.duels.match.MatchParticipant;
import net.pvpserver.duels.match.MatchState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Lists live matches; clicking one starts spectating it.
 */
public final class SpectateMenu extends PaginatedMenu {

    private final PvPDuels plugin;

    /**
     * @param plugin duels plugin
     * @param viewer viewer
     */
    public SpectateMenu(PvPDuels plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    @Override
    protected Component title() {
        return plugin.messages().parse(plugin.menus().getString("titles.spectate", "<dark_gray>Live Matches"));
    }

    @Override
    protected List<Button> content() {
        List<Button> buttons = new ArrayList<>();
        for (Match match : plugin.matches().matches()) {
            if (match.state() == MatchState.STARTING || match.state() == MatchState.ENDED || match.arena() == null) {
                continue;
            }
            var item = ItemBuilder.of(match.kit().icon())
                    .name(plugin.messages().parse("<white>" + MessageService.mini().escapeTags(match.description())))
                    .lore(plugin.messages().getList("menu.spectate-lore", MessageService.c("kit", match.kit().name()),
                            MessageService.p("type", match.ranked() ? "Ranked" : "Unranked"),
                            MessageService.p("duration", TimeUtil.formatClock(match.durationMillis())),
                            MessageService.p("spectators", match.spectators().size())))
                    .build();
            buttons.add(new Button(item, (player, click) -> {
                Player target = null;
                for (MatchParticipant participant : match.participants()) {
                    if (!participant.disconnected() && Bukkit.getPlayer(participant.uuid()) != null) {
                        target = Bukkit.getPlayer(participant.uuid());
                        break;
                    }
                }
                if (target != null) {
                    player.closeInventory();
                    plugin.spectators().spectate(player, target);
                }
            }));
        }
        return buttons;
    }
}
