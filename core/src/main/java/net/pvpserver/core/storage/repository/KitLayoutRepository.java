package net.pvpserver.core.storage.repository;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Per-player kit hotbar layouts, stored in serialised form (see {@link net.pvpserver.core.kit.KitLayout}).
 */
public interface KitLayoutRepository {

    /**
     * @param uuid player id
     * @return layouts keyed by kit id (blocking, for pre-login)
     */
    Map<String, String> loadBlocking(UUID uuid);

    /**
     * @param uuid player id
     * @param kit kit id
     * @param layout serialised layout, or null to delete
     * @return completion
     */
    CompletableFuture<Void> save(UUID uuid, String kit, String layout);
}
