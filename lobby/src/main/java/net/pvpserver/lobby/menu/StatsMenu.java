package net.pvpserver.lobby.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.pvpserver.core.gui.Button;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.gui.PaginatedMenu;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.stats.KitStats;
import net.pvpserver.core.util.TimeUtil;
import net.pvpserver.lobby.PvPLobby;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-kit statistics of a (possibly offline) profile.
 */
public final class StatsMenu extends PaginatedMenu {

    private final PvPLobby plugin;
    private final PlayerProfile target;

    /**
     * @param plugin lobby plugin
     * @param viewer viewer
     * @param target profile to show
     */
    public StatsMenu(PvPLobby plugin, Player viewer, PlayerProfile target) {
        super(viewer);
        this.plugin = plugin;
        this.target = target;
    }

    @Override
    protected Component title() {
        return plugin.menus().title("stats", MessageService.p("player", target.name()));
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected List<Button> content() {
        List<Button> buttons = new ArrayList<>();
        for (Kit kit : plugin.api().kits().all()) {
            KitStats stats = target.allStats().get(kit.id());
            if (stats == null) {
                stats = new KitStats(kit.id(), plugin.api().stats().startingElo());
            }
            TagResolver resolvers = TagResolver.resolver(
                    MessageService.c("kit", kit.name()),
                    MessageService.p("elo", stats.elo()),
                    MessageService.p("wins", stats.wins()),
                    MessageService.p("losses", stats.losses()),
                    MessageService.p("ranked_wins", stats.rankedWins()),
                    MessageService.p("ranked_losses", stats.rankedLosses()),
                    MessageService.p("win_rate", stats.winRate()),
                    MessageService.p("streak", stats.winStreak()),
                    MessageService.p("best_streak", stats.bestWinStreak()),
                    MessageService.p("ffa_kills", stats.ffaKills()),
                    MessageService.p("ffa_deaths", stats.ffaDeaths()),
                    MessageService.p("ffa_kdr", stats.kdr()),
                    MessageService.p("ffa_elo", stats.ffaElo()),
                    MessageService.p("ffa_best_streak", stats.ffaBestStreak()));
            List<Component> lore = new ArrayList<>(plugin.menus().lore("stats-kit", resolvers));
            if (kit.ffa()) {
                lore.addAll(plugin.menus().lore("stats-kit-ffa", resolvers));
            }
            buttons.add(Button.display(ItemBuilder.of(kit.icon()).lore(lore).hideFlags().build()));
        }
        return buttons;
    }

    @Override
    protected void decorate() {
        TagResolver resolvers = TagResolver.resolver(
                MessageService.p("player", target.name()),
                MessageService.p("elo", target.globalElo()),
                MessageService.p("rank", target.rankId()),
                MessageService.p("playtime", TimeUtil.formatDuration(target.playtimeMillis())));
        var head = ItemBuilder.of(Material.PLAYER_HEAD)
                .name(plugin.messages().parse("<primary><bold><player>", resolvers))
                .lore(plugin.menus().lore("stats-header", resolvers))
                .skull(Bukkit.getOfflinePlayer(target.uuid()))
                .build();
        set(31, Button.display(head));
    }
}
