package net.pvpserver.duels.queue;

/**
 * Identifies one queue.
 *
 * @param kit kit id
 * @param ranked ranked flag
 * @param mode team size
 */
public record QueueKey(String kit, boolean ranked, QueueMode mode) {
}
