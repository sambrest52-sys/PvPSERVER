package net.pvpserver.core.state;

import net.pvpserver.core.api.event.PlayerStateChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative player state machine shared by every gamemode plugin.
 */
public final class PlayerStateService implements Listener {

    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();

    /**
     * @param player player
     * @return current state, {@link PlayerState#LOBBY} if unknown
     */
    public PlayerState get(Player player) {
        return get(player.getUniqueId());
    }

    /**
     * @param uuid player id
     * @return current state, {@link PlayerState#LOBBY} if unknown
     */
    public PlayerState get(UUID uuid) {
        return states.getOrDefault(uuid, PlayerState.LOBBY);
    }

    /**
     * @param player player
     * @param candidates states
     * @return whether the player is in any of the states
     */
    public boolean is(Player player, PlayerState... candidates) {
        PlayerState current = get(player);
        for (PlayerState candidate : candidates) {
            if (candidate == current) {
                return true;
            }
        }
        return false;
    }

    /**
     * Changes a player's state and fires {@link PlayerStateChangeEvent} (main thread only).
     *
     * @param player player
     * @param state new state
     */
    public void set(Player player, PlayerState state) {
        PlayerState previous = states.put(player.getUniqueId(), state);
        if (previous == null) {
            previous = PlayerState.LOBBY;
        }
        if (previous != state) {
            Bukkit.getPluginManager().callEvent(new PlayerStateChangeEvent(player, previous, state));
        }
    }

    /**
     * @param state state
     * @return number of online players in the state
     */
    public int count(PlayerState state) {
        int count = 0;
        for (PlayerState value : states.values()) {
            if (value == state) {
                count++;
            }
        }
        return count;
    }

    /** @return counts for every state */
    public Map<PlayerState, Integer> counts() {
        Map<PlayerState, Integer> counts = new EnumMap<>(PlayerState.class);
        for (PlayerState value : states.values()) {
            counts.merge(value, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * @param uuids players to test
     * @return the first busy player id, or null if everybody is in the lobby
     */
    public UUID firstBusy(Collection<UUID> uuids) {
        for (UUID uuid : uuids) {
            if (get(uuid).busy()) {
                return uuid;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Removed after every other quit handler (they run at lower priorities and may still read the state).
        states.remove(event.getPlayer().getUniqueId());
    }
}
