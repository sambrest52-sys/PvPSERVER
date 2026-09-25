package net.pvpserver.core.api.bridge;

import org.bukkit.entity.Player;

/**
 * Implemented by PvPLobby.
 */
public interface KitEditorBridge {

    /**
     * Opens the kit editor kit selection.
     *
     * @param player player
     */
    void openKitEditor(Player player);
}
