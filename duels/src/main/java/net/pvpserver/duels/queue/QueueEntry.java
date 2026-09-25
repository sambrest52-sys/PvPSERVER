package net.pvpserver.duels.queue;

import java.util.List;
import java.util.UUID;

/**
 * A solo player or a party waiting in a queue. Pure value object.
 *
 * @param id entry id (the solo player or party leader)
 * @param members all players in the entry
 * @param elo rating used for matchmaking (party average)
 * @param joinedAt epoch millis when the entry joined
 */
public record QueueEntry(UUID id, List<UUID> members, int elo, long joinedAt) {

    /**
     * @param id entry id
     * @param members members
     * @param elo rating
     * @param joinedAt join time
     */
    public QueueEntry {
        members = List.copyOf(members);
        if (members.isEmpty()) {
            throw new IllegalArgumentException("Queue entry needs at least one member");
        }
    }

    /** @return member count */
    public int size() {
        return members.size();
    }

    /**
     * @param now current time
     * @return waited milliseconds
     */
    public long waited(long now) {
        return Math.max(0, now - joinedAt);
    }
}
