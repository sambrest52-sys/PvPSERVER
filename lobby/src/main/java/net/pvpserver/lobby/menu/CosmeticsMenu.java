package net.pvpserver.lobby.menu;

import net.kyori.adventure.text.Component;
import net.pvpserver.core.cosmetic.Cosmetic;
import net.pvpserver.core.cosmetic.CosmeticType;
import net.pvpserver.core.gui.Button;
import net.pvpserver.core.gui.ItemBuilder;
import net.pvpserver.core.gui.Menu;
import net.pvpserver.core.gui.PaginatedMenu;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.lobby.PvPLobby;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Cosmetic category picker and per-category selection menus.
 */
public final class CosmeticsMenu extends Menu {

    private final PvPLobby plugin;

    /**
     * @param plugin lobby plugin
     * @param viewer viewer
     */
    public CosmeticsMenu(PvPLobby plugin, Player viewer) {
        super(viewer);
        this.plugin = plugin;
    }

    @Override
    protected Component title() {
        return plugin.menus().title("cosmetics");
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void build() {
        MenuConfig menus = plugin.menus();
        set(10, new Button(menus.item("cosmetics.kill-effects", Material.DIAMOND_SWORD),
                (p, c) -> new Category(plugin, p, CosmeticType.KILL_EFFECT).open()));
        set(11, new Button(menus.item("cosmetics.death-animations", Material.SKELETON_SKULL),
                (p, c) -> new Category(plugin, p, CosmeticType.DEATH_ANIMATION).open()));
        set(12, new Button(menus.item("cosmetics.join-messages", Material.OAK_SIGN),
                (p, c) -> new Category(plugin, p, CosmeticType.JOIN_MESSAGE).open()));
        set(14, new Button(menus.item("cosmetics.trails", Material.BLAZE_POWDER),
                (p, c) -> new Category(plugin, p, CosmeticType.TRAIL).open()));
        set(16, new Button(menus.item("cosmetics.join-effects", Material.FIREWORK_ROCKET),
                (p, c) -> new Category(plugin, p, CosmeticType.JOIN_EFFECT).open()));
        fill(menus.filler());
    }

    /**
     * Lists cosmetics of one type.
     */
    static final class Category extends PaginatedMenu {
        private final PvPLobby plugin;
        private final CosmeticType type;

        Category(PvPLobby plugin, Player viewer, CosmeticType type) {
            super(viewer);
            this.plugin = plugin;
            this.type = type;
        }

        @Override
        protected Component title() {
            return plugin.menus().title("cosmetics-" + type.section());
        }

        @Override
        protected int rows() {
            return 4;
        }

        @Override
        protected List<Button> content() {
            MenuConfig menus = plugin.menus();
            String selected = plugin.api().cosmetics().selected(viewer, type);
            List<Button> buttons = new ArrayList<>();
            var none = ItemBuilder.of(menus.item("cosmetics.none", Material.BARRIER))
                    .lore(menus.lore(selected.equals("none") ? "cosmetic-selected" : "cosmetic-select")).build();
            buttons.add(new Button(none, (p, c) -> {
                plugin.api().cosmetics().select(p, type, "none");
                refresh();
            }));
            for (Cosmetic cosmetic : plugin.api().cosmetics().all(type)) {
                boolean unlocked = plugin.api().cosmetics().unlocked(viewer, cosmetic);
                boolean active = cosmetic.id().equals(selected);
                String loreKey = !unlocked ? "cosmetic-locked" : active ? "cosmetic-selected" : "cosmetic-select";
                var item = ItemBuilder.of(unlocked ? cosmetic.icon() : Material.GRAY_DYE)
                        .name(plugin.messages().parse(cosmetic.displayName()))
                        .lore(menus.lore(loreKey))
                        .glow(active)
                        .hideFlags()
                        .build();
                buttons.add(new Button(item, (p, c) -> {
                    if (!unlocked) {
                        plugin.messages().send(p, "cosmetics.locked");
                        return;
                    }
                    plugin.api().cosmetics().select(p, type, cosmetic.id());
                    plugin.messages().send(p, "cosmetics.selected", MessageService.c("cosmetic", plugin.messages().parse(cosmetic.displayName())));
                    refresh();
                }));
            }
            return buttons;
        }

        @Override
        protected void decorate() {
            set(31, new Button(plugin.menus().item("back", Material.ARROW), (p, c) -> new CosmeticsMenu(plugin, p).open()));
        }
    }
}
