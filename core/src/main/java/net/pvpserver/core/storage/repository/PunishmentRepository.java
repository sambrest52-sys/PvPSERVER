package net.pvpserver.core.storage.repository;

import net.pvpserver.core.moderation.Punishment;
import net.pvpserver.core.moderation.PunishmentType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Punishment persistence.
 */
public interface PunishmentRepository {

    /**
     * @param punishment record without id
     * @return record with generated id
     */
    CompletableFuture<Punishment> insert(Punishment punishment);

    /**
     * Blocking lookup used during pre-login.
     *
     * @param target player id
     * @param type type
     * @param now current time
     * @return the in-force punishment with the latest expiry
     */
    Optional<Punishment> findActiveBlocking(UUID target, PunishmentType type, long now);

    /**
     * @param target player id
     * @param type type
     * @param now current time
     * @return the in-force punishment with the latest expiry
     */
    CompletableFuture<Optional<Punishment>> findActive(UUID target, PunishmentType type, long now);

    /**
     * @param target player id
     * @param limit max rows
     * @return newest first
     */
    CompletableFuture<List<Punishment>> history(UUID target, int limit);

    /**
     * Deactivates every active punishment of a type for a player.
     *
     * @param target player id
     * @param type type
     * @param removedBy remover name
     * @param reason removal reason
     * @return number of rows changed
     */
    CompletableFuture<Integer> deactivate(UUID target, PunishmentType type, String removedBy, String reason);
}
