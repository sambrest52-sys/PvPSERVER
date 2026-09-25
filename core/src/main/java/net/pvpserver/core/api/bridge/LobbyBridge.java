package net.pvpserver.core.api.bridge;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Implemented by PvPLobby.
 */
public interface LobbyBridge {

    /**
     * Resets a player (inventory, effects, health, flight), teleports them to spawn, sets state LOBBY and gives the
     * lobby hotbar.
     *
     * @param player player
     */
    void sendToLobby(Player player);

    /**
     * Re-applies the hotbar for the player's current lobby-side state (lobby, queue, party) without teleporting.
     *
     * @param player player
     */
    void refreshHotbar(Player player);

    /** @return lobby spawn */
    Location spawn();
}
