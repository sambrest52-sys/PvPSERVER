package net.pvpserver.core.storage.repository;

import net.pvpserver.core.profile.PlayerProfile;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence for {@link PlayerProfile} core data (name, rank, settings, cosmetics, ignores).
 */
public interface ProfileRepository {

    /**
     * Loads a profile synchronously. Only call from async contexts such as pre-login.
     *
     * @param uuid player id
     * @param name current name (used when creating a new profile)
     * @return loaded or freshly created profile
     */
    PlayerProfile loadOrCreateBlocking(UUID uuid, String name);

    /**
     * @param profile profile to save (snapshot taken immediately on the calling thread)
     * @return completion
     */
    CompletableFuture<Void> save(PlayerProfile profile);

    /**
     * Saves synchronously, used during shutdown.
     *
     * @param profile profile
     */
    void saveBlocking(PlayerProfile profile);

    /**
     * @param name case-insensitive name
     * @return uuid of the last player seen with that name
     */
    CompletableFuture<Optional<UUID>> findUuid(String name);

    /**
     * @param uuid player id
     * @return last known name
     */
    CompletableFuture<Optional<String>> findName(UUID uuid);

    /**
     * Updates only the rank column of a (possibly offline) player.
     *
     * @param uuid player id
     * @param rankId rank id
     * @return completion
     */
    CompletableFuture<Void> setRank(UUID uuid, String rankId);
}
