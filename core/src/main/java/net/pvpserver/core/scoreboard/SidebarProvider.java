package net.pvpserver.core.scoreboard;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Supplies sidebar content for players in a given state. Called on the main thread at the sidebar refresh rate,
 * so implementations should be cheap (no IO).
 */
public interface SidebarProvider {

    /**
     * @param player viewer
     * @return title
     */
    Component title(Player player);

    /**
     * @param player viewer
     * @return lines (max 15)
     */
    List<Component> lines(Player player);
}
