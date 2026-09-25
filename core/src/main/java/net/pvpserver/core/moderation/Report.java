package net.pvpserver.core.moderation;

import java.util.UUID;

/**
 * Player report.
 *
 * @param id database id
 * @param reporter reporter id
 * @param reporterName reporter name
 * @param target reported player id
 * @param targetName reported player name
 * @param reason reason
 * @param createdAt epoch millis
 */
public record Report(long id, UUID reporter, String reporterName, UUID target, String targetName, String reason, long createdAt) {
}
