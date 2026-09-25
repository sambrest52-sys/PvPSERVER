package net.pvpserver.duels.menu;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.gui.Button;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.gui.Menu;
import net.pvpserver.core.gui.PaginatedMenu;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.party.Party;
import net.pvpserver.duels.PvPDuels;
import net.pvpserver.duels.match.MatchType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Party leader menu: split fight, party FFA, or challenge another party.
 */
public final class PartyFightMenu extends Menu {

    private final PvPDuels plugin;

    /**
     * @param plugin duels plugin
     * @param viewer party leader
     */
    public PartyFightMenu(PvPDuels plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    @Override
    protected Component title() {
        return plugin.messages().parse(plugin.menus().getString("titles.party-fight", "Party Fight"));
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void build() {
        set(11, new Button(ItemBuilder.of(Material.SHEARS).name(plugin.messages().get("menu.party-split"))
                .lore(plugin.messages().getList("menu.party-split-lore")).build(), (p, c) -> pickKit(p, MatchType.PARTY_SPLIT)));
        set(13, new Button(ItemBuilder.of(Material.TNT).name(plugin.messages().get("menu.party-ffa"))
                .lore(plugin.messages().getList("menu.party-ffa-lore")).build(), (p, c) -> pickKit(p, MatchType.PARTY_FFA)));
        set(15, new Button(ItemBuilder.of(Material.GOLDEN_SWORD).name(plugin.messages().get("menu.party-duel"))
                .lore(plugin.messages().getList("menu.party-duel-lore")).hideFlags().build(), (p, c) -> new OtherParties(p).open()));
        fill(Material.GRAY_STAINED_GLASS_PANE);
    }

    private void pickKit(Player leader, MatchType type) {
        Optional<Party> party = plugin.api().parties().partyOf(leader);
        if (party.isEmpty() || !party.get().isLeader(leader.getUniqueId())) {
            plugin.messages().send(leader, "party.not-leader");
            return;
        }
        if (party.get().size() < 2) {
            plugin.messages().send(leader, "party-fight.too-small");
            return;
        }
        new KitPickMenu(plugin, leader, plugin.messages().parse(plugin.menus().getString("titles.party-kit", "Choose a kit")),
                plugin.api().kits().queueKits(false), (player, kit) -> start(player, type, kit)).open();
    }

    private void start(Player leader, MatchType type, Kit kit) {
        leader.closeInventory();
        Optional<Party> party = plugin.api().parties().partyOf(leader);
        if (party.isEmpty() || !party.get().isLeader(leader.getUniqueId())) {
            return;
        }
        List<UUID> members = new ArrayList<>(party.get().members());
        UUID busy = plugin.api().states().firstBusy(members);
        if (busy != null) {
            Player busyPlayer = Bukkit.getPlayer(busy);
            plugin.messages().send(leader, "queue.party-member-busy", MessageService.p("player", busyPlayer == null ? "?" : busyPlayer.getName()));
            return;
        }
        Collections.shuffle(members);
        List<List<UUID>> teams = new ArrayList<>();
        if (type == MatchType.PARTY_FFA) {
            members.forEach(member -> teams.add(List.of(member)));
        } else {
            teams.addAll(Party.split(members));
        }
        plugin.matches().create(type, kit, false, teams, 1, null);
    }

    private final class OtherParties extends PaginatedMenu {
        OtherParties(Player viewer) {
            super(viewer);
        }

        @Override
        protected Component title() {
            return plugin.messages().parse(plugin.menus().getString("titles.party-list", "Parties"));
        }

        @Override
        protected List<Button> content() {
            List<Button> buttons = new ArrayList<>();
            Optional<Party> own = plugin.api().parties().partyOf(viewer);
            for (Party party : plugin.api().parties().parties()) {
                if (own.isPresent() && own.get() == party) {
                    continue;
                }
                Player leader = Bukkit.getPlayer(party.leader());
                if (leader == null || !viewer.canSee(leader)) {
                    continue;
                }
                var item = ItemBuilder.of(Material.PLAYER_HEAD).skull(leader)
                        .name(plugin.messages().get("menu.party-entry", MessageService.p("player", leader.getName())))
                        .lore(plugin.messages().getList("menu.party-entry-lore", MessageService.p("size", party.size())))
                        .build();
                buttons.add(new Button(item, (player, click) -> new KitPickMenu(plugin, player,
                        plugin.messages().parse(plugin.menus().getString("titles.party-kit", "Choose a kit")),
                        plugin.api().kits().queueKits(false), (p, kit) -> {
                    p.closeInventory();
                    plugin.requests().send(p, leader, kit, null, 1);
                }).open()));
            }
            return buttons;
        }
    }
}
