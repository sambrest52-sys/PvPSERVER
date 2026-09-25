package net.pvpserver.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Menu with a scrolling content area and previous/next buttons in the bottom row.
 */
public abstract class PaginatedMenu extends Menu {

    private int page;

    /**
     * @param viewer viewer
     */
    protected PaginatedMenu(Player viewer) {
        super(viewer);
    }

    /** @return all content buttons across pages */
    protected abstract List<Button> content();

    /** Hook to add static buttons to the bottom row (slots rows*9-9 .. rows*9-1 except 45/53 style corners). */
    protected void decorate() {
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected final void build() {
        List<Button> content = content();
        int perPage = (rows() - 1) * 9;
        int pages = Math.max(1, (content.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < content.size(); i++) {
            set(i, content.get(start + i));
        }
        int bottom = (rows() - 1) * 9;
        if (page > 0) {
            set(bottom, ItemBuilder.of(Material.ARROW).name(Component.text("« Previous page", NamedTextColor.YELLOW)).build(),
                    (p, c) -> {
                        page--;
                        refresh();
                    });
        }
        if (page < pages - 1) {
            set(bottom + 8, ItemBuilder.of(Material.ARROW).name(Component.text("Next page »", NamedTextColor.YELLOW)).build(),
                    (p, c) -> {
                        page++;
                        refresh();
                    });
        }
        decorate();
    }

    /** @return current page (0-based) */
    public int page() {
        return page;
    }
}
