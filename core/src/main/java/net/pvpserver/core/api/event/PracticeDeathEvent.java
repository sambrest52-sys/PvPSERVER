package net.pvpserver.core.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired instead of a vanilla death when a player in a combat state (match or FFA) takes lethal damage.
 * The lethal damage has already been cancelled; listeners decide what "dying" means (respawn, elimination ...).
 */
public final class PracticeDeathEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player killer;
    private final EntityDamageEvent.DamageCause cause;

    /**
     * @param player victim
     * @param killer last attacker (may be null)
     * @param cause final damage cause
     */
    public PracticeDeathEvent(Player player, @Nullable Player killer, EntityDamageEvent.DamageCause cause) {
        super(player);
        this.killer = killer;
        this.cause = cause;
    }

    /** @return killer, possibly the last attacker for void/fall deaths, or null */
    public @Nullable Player killer() {
        return killer;
    }

    /** @return damage cause of the lethal hit */
    public EntityDamageEvent.DamageCause cause() {
        return cause;
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
