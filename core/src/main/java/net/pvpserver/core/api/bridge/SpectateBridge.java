package net.pvpserver.core.api.bridge;

import org.bukkit.entity.Player;

/**
 * Implemented by PvPDuels: spectator mode.
 */
public interface SpectateBridge {

    /**
     * Starts spectating the match the target plays in.
     *
     * @param spectator spectator
     * @param target match participant
     * @return whether spectating started
     */
    boolean spectate(Player spectator, Player target);

    /**
     * Opens a menu of live matches.
     *
     * @param player viewer
     */
    void openSpectateMenu(Player player);
}
