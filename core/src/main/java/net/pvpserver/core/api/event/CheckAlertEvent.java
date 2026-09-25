package net.pvpserver.core.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired (main thread) when a built-in check flags a player. Cancel to suppress the staff alert. External anticheats
 * can listen to forward alerts elsewhere, or call {@code CheckService#alert} to reuse the alert pipeline.
 */
public final class CheckAlertEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final String check;
    private final String details;
    private final int violations;
    private boolean cancelled;

    /**
     * @param player flagged player
     * @param check check name
     * @param details human readable details
     * @param violations violation level
     */
    public CheckAlertEvent(Player player, String check, String details, int violations) {
        super(player);
        this.check = check;
        this.details = details;
        this.violations = violations;
    }

    /** @return check name */
    public String check() {
        return check;
    }

    /** @return details */
    public String details() {
        return details;
    }

    /** @return violation level */
    public int violations() {
        return violations;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
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
