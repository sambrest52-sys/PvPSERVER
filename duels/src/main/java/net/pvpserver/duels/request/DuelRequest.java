package net.pvpserver.duels.request;

import java.util.UUID;

/**
 * Pending duel challenge.
 *
 * @param sender challenger (party leader for party duels)
 * @param target challenged player (party leader for party duels)
 * @param kit kit id
 * @param arena preferred arena or null for random
 * @param rounds rounds to win
 * @param party whether this is a party vs party challenge
 * @param expiresAt expiry epoch millis
 */
public record DuelRequest(UUID sender, UUID target, String kit, String arena, int rounds, boolean party, long expiresAt) {

    /**
     * @param now current time
     * @return whether the request expired
     */
    public boolean expired(long now) {
        return now > expiresAt;
    }
}
