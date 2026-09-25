package net.pvpserver.duels.match;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived store of post-match inventory snapshots (clickable from chat).
 */
public final class SnapshotCache {

    private final Map<UUID, InventorySnapshot> snapshots = new ConcurrentHashMap<>();
    private long ttlMillis;

    /**
     * @param ttlSeconds how long snapshots stay viewable
     */
    public SnapshotCache(int ttlSeconds) {
        this.ttlMillis = ttlSeconds * 1000L;
    }

    /**
     * @param ttlSeconds new lifetime
     */
    public void ttl(int ttlSeconds) {
        this.ttlMillis = ttlSeconds * 1000L;
    }

    /**
     * @param snapshot snapshot to store
     */
    public void put(InventorySnapshot snapshot) {
        snapshots.put(snapshot.id(), snapshot);
    }

    /**
     * @param id snapshot id
     * @return snapshot or null when unknown/expired
     */
    public InventorySnapshot get(UUID id) {
        InventorySnapshot snapshot = snapshots.get(id);
        if (snapshot != null && System.currentTimeMillis() - snapshot.createdAt() > ttlMillis) {
            snapshots.remove(id);
            return null;
        }
        return snapshot;
    }

    /** Removes expired snapshots. */
    public void purge() {
        long now = System.currentTimeMillis();
        snapshots.values().removeIf(s -> now - s.createdAt() > ttlMillis);
    }
}
