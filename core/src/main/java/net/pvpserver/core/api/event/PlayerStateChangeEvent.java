package net.pvpserver.core.api.event;

import net.pvpserver.core.state.PlayerState;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a player's {@link PlayerState} changed.
 */
public final class PlayerStateChangeEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final PlayerState from;
    private final PlayerState to;

    /**
     * @param player player
     * @param from previous state
     * @param to new state
     */
    public PlayerStateChangeEvent(Player player, PlayerState from, PlayerState to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    /** @return previous state */
    public PlayerState from() {
        return from;
    }

    /** @return new state */
    public PlayerState to() {
        return to;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /** @return handler list */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
