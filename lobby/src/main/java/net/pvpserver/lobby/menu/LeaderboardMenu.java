package net.pvpserver.lobby.menu;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.gui.Button;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.gui.Menu;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.stats.LeaderboardEntry;
import net.pvpserver.core.stats.StatField;
import net.pvpserver.lobby.PvPLobby;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Cached top-10 per kit (and global) for a selectable statistic.
 */
public final class LeaderboardMenu extends Menu {

    private static final StatField[] FIELDS = {StatField.ELO, StatField.WINS, StatField.BEST_WIN_STREAK, StatField.FFA_KILLS,
            StatField.FFA_ELO, StatField.FFA_BEST_STREAK};

    private final PvPLobby plugin;
    private int fieldIndex;

    /**
     * @param plugin lobby plugin
     * @param viewer viewer
     */
    public LeaderboardMenu(PvPLobby plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    @Override
    protected Component title() {
        return plugin.menus().title("leaderboards", MessageService.p("stat", FIELDS[fieldIndex].displayName()));
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void build() {
        StatField field = FIELDS[fieldIndex];
        boolean ffaField = field.name().startsWith("FFA");
        int slot = 10;
        set(4, Button.display(board(Material.NETHER_STAR, plugin.messages().parse("<primary><bold>Global"), null, field)));
        for (Kit kit : plugin.api().kits().all()) {
            if (ffaField ? !kit.ffa() : !(kit.ranked() || kit.unranked())) {
                continue;
            }
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot >= 35) {
                break;
            }
            set(slot++, Button.display(board(kit.icon().getType(), kit.name(), kit.id(), field)));
        }
        List<Component> fieldLore = new ArrayList<>();
        for (int i = 0; i < FIELDS.length; i++) {
            fieldLore.add(plugin.messages().parse((i == fieldIndex ? "<primary>» " : "<gray>  ") + FIELDS[i].displayName()));
        }
        var switcher = ItemBuilder.of(plugin.menus().item("leaderboard-switch", Material.HOPPER)).lore(fieldLore).build();
        set(40, new Button(switcher, (player, click) -> {
            fieldIndex = (fieldIndex + (click.isRightClick() ? FIELDS.length - 1 : 1)) % FIELDS.length;
            open();
        }));
        fill(plugin.menus().filler());
    }

    private org.bukkit.inventory.ItemStack board(Material icon, Component name, String kit, StatField field) {
        List<LeaderboardEntry> entries = plugin.api().leaderboards().top(kit, field);
        List<Component> lore = new ArrayList<>();
        if (entries.isEmpty()) {
            lore.add(plugin.messages().get("leaderboard.empty"));
        }
        int position = 1;
        for (LeaderboardEntry entry : entries) {
            lore.add(plugin.messages().get("leaderboard.line", MessageService.p("position", position++),
                    MessageService.p("player", entry.name()), MessageService.p("value", entry.value())));
        }
        return ItemBuilder.of(icon).name(name).lore(lore).hideFlags().build();
    }
}
