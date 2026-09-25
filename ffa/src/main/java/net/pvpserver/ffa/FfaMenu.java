package net.pvpserver.ffa;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.gui.Button;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.gui.Menu;
import net.pvpserver.core.message.MessageService;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * FFA arena selection with player counts.
 */
public final class FfaMenu extends Menu {

    private final PvPFFA plugin;

    /**
     * @param plugin FFA plugin
     * @param viewer viewer
     */
    public FfaMenu(PvPFFA plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    @Override
    protected Component title() {
        return plugin.messages().parse(plugin.config().get().getString("menu-title", "<dark_gray>Free For All"));
    }

    @Override
    protected int rows() {
        return Math.min(6, 2 + (plugin.ffa().arenas().size() + 6) / 7);
    }

    @Override
    protected void build() {
        int slot = 10;
        for (FfaArena arena : plugin.ffa().arenas()) {
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot >= rows() * 9 - 9) {
                break;
            }
            var item = ItemBuilder.of(arena.icon())
                    .amount(Math.max(1, Math.min(64, arena.players().size())))
                    .name(plugin.messages().parse(arena.displayName()))
                    .lore(plugin.messages().getList(arena.ranked() ? "menu.arena-lore-ranked" : "menu.arena-lore",
                            MessageService.c("kit", arena.kit().name()), MessageService.p("players", arena.players().size())))
                    .hideFlags()
                    .build();
            set(slot++, new Button(item, (player, click) -> {
                player.closeInventory();
                plugin.ffa().join(player, arena);
            }));
        }
        fill(Material.GRAY_STAINED_GLASS_PANE);
    }
}
