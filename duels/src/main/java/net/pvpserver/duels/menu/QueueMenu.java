package net.pvpserver.duels.menu;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.gui.Button;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.gui.Menu;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.party.Party;
import net.pvpserver.duels.PvPDuels;
import net.pvpserver.duels.queue.QueueMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * Ranked / unranked queue selection with live queue and fight counts. Solo players can switch between 1v1 and 2v2;
 * party leaders always queue 2v2.
 */
public final class QueueMenu extends Menu {

    private final PvPDuels plugin;
    private final boolean ranked;
    private QueueMode mode;

    /**
     * @param plugin duels plugin
     * @param viewer viewer
     * @param ranked ranked queue
     */
    public QueueMenu(PvPDuels plugin, Player viewer, boolean ranked) {
        super(viewer);
        this.plugin = plugin;
        this.ranked = ranked;
        this.mode = plugin.api().parties().partyOf(viewer).isPresent() ? QueueMode.TWO_V_TWO : QueueMode.ONE_V_ONE;
    }

    @Override
    protected Component title() {
        return plugin.messages().parse(plugin.menus().getString(ranked ? "titles.queue-ranked" : "titles.queue-unranked", "Queue"),
                MessageService.p("mode", mode.label()));
    }

    @Override
    protected int rows() {
        List<Kit> kits = plugin.api().kits().queueKits(ranked);
        return Math.min(6, 2 + (kits.size() + 6) / 7);
    }

    @Override
    protected void build() {
        List<Kit> kits = plugin.api().kits().queueKits(ranked);
        int slot = 10;
        for (Kit kit : kits) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot >= rows() * 9 - 9) {
                break;
            }
            int queued = plugin.queues().queuedPlayers(kit.id(), ranked);
            int fighting = plugin.matches().playersFighting(kit.id(), ranked);
            int elo = plugin.api().stats().eloOf(viewer.getUniqueId(), kit.id());
            var item = ItemBuilder.of(kit.icon())
                    .amount(Math.max(1, Math.min(64, queued)))
                    .lore(plugin.messages().getList(ranked ? "menu.queue-lore-ranked" : "menu.queue-lore",
                            MessageService.p("queued", queued), MessageService.p("fighting", fighting), MessageService.p("elo", elo),
                            MessageService.p("mode", mode.label())))
                    .build();
            set(slot++, new Button(item, (player, click) -> {
                player.closeInventory();
                plugin.queues().join(player, kit, ranked, mode);
            }));
        }
        Optional<Party> party = plugin.api().parties().partyOf(viewer);
        if (party.isEmpty()) {
            var toggle = ItemBuilder.of(mode == QueueMode.ONE_V_ONE ? Material.IRON_SWORD : Material.GOLDEN_SWORD)
                    .name(plugin.messages().get("menu.queue-mode", MessageService.p("mode", mode.label())))
                    .lore(plugin.messages().getList("menu.queue-mode-lore"))
                    .hideFlags().build();
            set(rows() * 9 - 5, new Button(toggle, (player, click) -> {
                mode = mode == QueueMode.ONE_V_ONE ? QueueMode.TWO_V_TWO : QueueMode.ONE_V_ONE;
                open();
            }));
        }
        fill(Material.GRAY_STAINED_GLASS_PANE);
    }
}
