package net.pvpserver.duels.menu;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.arena.Arena;
import net.pvpserver.core.gui.Button;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.gui.Menu;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.duels.PvPDuels;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Second step of /duel: pick an arena (or random) and rounds, then send.
 */
public final class DuelOptionsMenu extends Menu {

    private final PvPDuels plugin;
    private final UUID target;
    private final Kit kit;
    private String arena;
    private int rounds;

    /**
     * @param plugin duels plugin
     * @param viewer challenger
     * @param target challenged player id
     * @param kit chosen kit
     */
    public DuelOptionsMenu(PvPDuels plugin, Player viewer, UUID target, Kit kit) {
        super(viewer);
        this.plugin = plugin;
        this.target = target;
        this.kit = kit;
        this.rounds = kit.rules().rounds();
    }

    @Override
    protected Component title() {
        return plugin.messages().parse(plugin.menus().getString("titles.duel-options", "Duel options"), MessageService.c("kit", kit.name()));
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void build() {
        List<Arena> arenas = new ArrayList<>();
        for (Arena candidate : plugin.api().arenas().arenas()) {
            if (candidate.enabled() && candidate.complete() && plugin.api().arenas().template(candidate.name()) != null
                    && kit.allowsArena(candidate.name(), candidate.tags())) {
                arenas.add(candidate);
            }
        }
        var random = ItemBuilder.of(Material.NETHER_STAR).name(plugin.messages().get("menu.arena-random"))
                .lore(plugin.messages().getList(arena == null ? "menu.arena-selected" : "menu.arena-select")).glow(arena == null).build();
        set(9, new Button(random, (p, c) -> {
            arena = null;
            refresh();
        }));
        int slot = 10;
        for (Arena candidate : arenas) {
            if (slot >= 17) {
                break;
            }
            boolean selected = candidate.name().equals(arena);
            var item = ItemBuilder.of(candidate.icon()).name(plugin.messages().parse(candidate.displayName()))
                    .lore(plugin.messages().getList(selected ? "menu.arena-selected" : "menu.arena-select")).glow(selected).build();
            set(slot++, new Button(item, (p, c) -> {
                arena = candidate.name();
                refresh();
            }));
        }
        int max = plugin.requests().maxRounds();
        var roundsItem = ItemBuilder.of(Material.CLOCK).amount(rounds)
                .name(plugin.messages().get("menu.rounds", MessageService.p("rounds", rounds), MessageService.p("best_of", rounds * 2 - 1)))
                .lore(plugin.messages().getList("menu.rounds-lore")).build();
        set(22, new Button(roundsItem, (p, c) -> {
            rounds = c.isRightClick() ? Math.max(1, rounds - 1) : (rounds % max) + 1;
            refresh();
        }));
        var send = ItemBuilder.of(Material.LIME_DYE).name(plugin.messages().get("menu.duel-send")).build();
        set(31, new Button(send, (p, c) -> {
            p.closeInventory();
            Player targetPlayer = plugin.getServer().getPlayer(target);
            if (targetPlayer == null) {
                plugin.messages().send(p, "command.player-not-found", MessageService.p("player", "?"));
                return;
            }
            plugin.requests().send(p, targetPlayer, kit, arena, rounds);
        }));
        fill(Material.GRAY_STAINED_GLASS_PANE);
    }
}
