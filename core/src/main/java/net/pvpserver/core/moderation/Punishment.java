package net.pvpserver.core.moderation;

import java.util.UUID;

/**
 * Immutable punishment record.
 *
 * @param id database id (0 before insert)
 * @param target punished player
 * @param targetName name at the time
 * @param type type
 * @param reason reason
 * @param issuer staff id, null for console
 * @param issuerName staff name
 * @param createdAt epoch millis
 * @param expiresAt epoch millis, {@link #PERMANENT} for no expiry, 0 for instantaneous types
 * @param active whether the punishment is in force (not removed)
 * @param removedBy name of the remover, if removed
 * @param removedAt removal time
 * @param removedReason removal reason
 */
public record Punishment(long id, UUID target, String targetName, PunishmentType type, String reason, UUID issuer,
                         String issuerName, long createdAt, long expiresAt, boolean active, String removedBy,
                         long removedAt, String removedReason) {

    /** Marker for permanent punishments. */
    public static final long PERMANENT = -1L;

    /** @return whether the punishment has no end */
    public boolean permanent() {
        return expiresAt == PERMANENT;
    }

    /**
     * @param now current epoch millis
     * @return whether it is active and not yet expired
     */
    public boolean inForce(long now) {
        return active && type.lasting() && (permanent() || expiresAt > now);
    }

    /**
     * @param newId id assigned by the database
     * @return copy with id
     */
    public Punishment withId(long newId) {
        return new Punishment(newId, target, targetName, type, reason, issuer, issuerName, createdAt, expiresAt, active,
                removedBy, removedAt, removedReason);
    }
}
