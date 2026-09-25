package net.pvpserver.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * An item in a {@link Menu} with an optional click action.
 *
 * @param item displayed item
 * @param action click handler (may be null for decoration)
 */
public record Button(ItemStack item, ClickAction action) {

    /**
     * Click callback.
     */
    @FunctionalInterface
    public interface ClickAction {
        /**
         * @param player clicker
         * @param click click type
         */
        void click(Player player, ClickType click);
    }

    /**
     * @param item decoration item
     * @return button without action
     */
    public static Button display(ItemStack item) {
        return new Button(item, null);
    }
}
