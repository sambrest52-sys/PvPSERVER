package net.pvpserver.duels.menu;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.gui.Button;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.gui.PaginatedMenu;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.duels.PvPDuels;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Generic kit chooser used by duels and party fights.
 */
public final class KitPickMenu extends PaginatedMenu {

    private final PvPDuels plugin;
    private final Component title;
    private final List<Kit> kits;
    private final BiConsumer<Player, Kit> onPick;

    /**
     * @param plugin duels plugin
     * @param viewer viewer
     * @param title title
     * @param kits kits to offer
     * @param onPick callback
     */
    public KitPickMenu(PvPDuels plugin, Player viewer, Component title, List<Kit> kits, BiConsumer<Player, Kit> onPick) {
        super(viewer);
        this.plugin = plugin;
        this.title = title;
        this.kits = kits;
        this.onPick = onPick;
    }

    @Override
    protected Component title() {
        return title;
    }

    @Override
    protected int rows() {
        return Math.min(6, Math.max(2, (kits.size() + 8) / 9 + 1));
    }

    @Override
    protected List<Button> content() {
        List<Button> buttons = new ArrayList<>();
        for (Kit kit : kits) {
            var item = ItemBuilder.of(kit.icon()).lore(kit.lore(plugin.messages().getList("menu.kit-pick-lore"))).build();
            buttons.add(new Button(item, (player, click) -> onPick.accept(player, kit)));
        }
        return buttons;
    }
}
