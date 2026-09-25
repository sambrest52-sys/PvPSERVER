package net.pvpserver.core.api.bridge;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Implemented by PvPDuels: running matches, duel requests and party fights.
 */
public interface MatchBridge {

    /** @return players currently fighting in matches */
    int playersInMatches();

    /** @return live matches for spectator menus */
    List<MatchInfo> activeMatches();

    /**
     * @param uuid player id
     * @return match the player participates in, or null
     */
    MatchInfo matchOf(UUID uuid);

    /**
     * Starts the /duel flow (kit + arena menus) from sender to target.
     *
     * @param sender challenger
     * @param target challenged player
     */
    void openDuelMenu(Player sender, Player target);

    /**
     * Opens the party fight menu (split teams / party FFA / duel other party) for a party leader.
     *
     * @param leader party leader
     */
    void openPartyFightMenu(Player leader);

    /**
     * Summary of a live match.
     *
     * @param id match id
     * @param kit kit display name
     * @param ranked ranked flag
     * @param description e.g. "Steve vs Alex"
     * @param participants participant ids
     * @param spectators spectator count
     * @param durationMillis elapsed time
     */
    record MatchInfo(UUID id, String kit, boolean ranked, String description, List<UUID> participants, int spectators,
                     long durationMillis) {
    }
}
