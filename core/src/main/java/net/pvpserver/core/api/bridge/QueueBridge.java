package net.pvpserver.core.api.bridge;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Implemented by PvPDuels: matchmaking queues.
 */
public interface QueueBridge {

    /**
     * Opens the kit selection menu for a queue.
     *
     * @param player player (party leaders queue their party for 2v2)
     * @param ranked ranked or unranked
     */
    void openQueueMenu(Player player, boolean ranked);

    /**
     * Removes the player (or their party) from any queue.
     *
     * @param player player
     */
    void leaveQueue(Player player);

    /**
     * @param uuid player id
     * @return whether the player is queued
     */
    boolean isQueued(UUID uuid);

    /** @return total queued players */
    int queuedPlayers();

    /**
     * @param kit kit id
     * @param ranked ranked flag
     * @return players queued for the kit
     */
    int queuedPlayers(String kit, boolean ranked);

    /**
     * @param uuid player id
     * @return details of the player's queue entry, or null when not queued
     */
    QueueInfo info(UUID uuid);

    /**
     * Snapshot of a queue entry for sidebars.
     *
     * @param kit kit display name (MiniMessage)
     * @param ranked ranked flag
     * @param mode "1v1" / "2v2"
     * @param waitedMillis time in queue
     * @param minElo lower end of the current search range (ranked only)
     * @param maxElo upper end of the current search range (ranked only)
     */
    record QueueInfo(String kit, boolean ranked, String mode, long waitedMillis, int minElo, int maxElo) {
    }
}
