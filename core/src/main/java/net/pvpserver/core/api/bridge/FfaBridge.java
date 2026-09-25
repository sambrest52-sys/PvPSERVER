package net.pvpserver.core.api.bridge;

import org.bukkit.entity.Player;

/**
 * Implemented by PvPFFA.
 */
public interface FfaBridge {

    /**
     * Opens the FFA arena/kit selection menu.
     *
     * @param player player
     */
    void openFfaMenu(Player player);

    /**
     * Removes a player from FFA and sends them to the lobby.
     *
     * @param player player
     */
    void leave(Player player);

    /** @return players currently in FFA arenas */
    int playerCount();
}
