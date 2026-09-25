package net.pvpserver.core.stats;

import java.util.UUID;

/**
 * One leaderboard row.
 *
 * @param uuid player id
 * @param name last known name
 * @param value statistic value
 */
public record LeaderboardEntry(UUID uuid, String name, int value) {
}
