package net.pvpserver.duels.spectate;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.gui.Button;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.gui.PaginatedMenu;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.duels.PvPDuels;
import net.pvpserver.duels.match.Match;
import net.pvpserver.duels.match.MatchParticipant;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Teleport-to-player menu for spectators.
 */
public final class SpectateTeleportMenu extends PaginatedMenu {

    private final PvPDuels plugin;

    /**
     * @param plugin duels plugin
     * @param viewer spectator
     */
    public SpectateTeleportMenu(PvPDuels plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    @Override
    protected Component title() {
        return plugin.messages().parse(plugin.menus().getString("titles.spectate-players", "<dark_gray>Teleport to player"));
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected List<Button> content() {
        List<Button> buttons = new ArrayList<>();
        Match match = plugin.spectators().spectatedMatch(viewer);
        if (match == null) {
            return buttons;
        }
        for (MatchParticipant participant : match.participants()) {
            Player target = Bukkit.getPlayer(participant.uuid());
            if (target == null || participant.disconnected()) {
                continue;
            }
            var item = ItemBuilder.of(Material.PLAYER_HEAD).skull(target)
                    .name(Component.text(participant.name(), participant.team().color()))
                    .lore(plugin.messages().getList("spectate.player-lore",
                            MessageService.p("health", String.format(java.util.Locale.ROOT, "%.1f", target.getHealth() / 2.0)),
                            MessageService.p("ping", target.getPing()), MessageService.p("hits", participant.hits())))
                    .build();
            buttons.add(new Button(item, (player, click) -> {
                player.teleport(target.getLocation());
                player.closeInventory();
            }));
        }
        return buttons;
    }
}
