package net.pvpserver.core.party;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired on the main thread whenever a party's membership or leadership changes.
 */
public final class PartyUpdateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Party party;
    private final Type type;
    private final UUID player;

    /**
     * Kind of change.
     */
    public enum Type { CREATE, JOIN, LEAVE, KICK, PROMOTE, DISBAND }

    /**
     * @param party party (may already be disbanded)
     * @param type change type
     * @param player affected player
     */
    public PartyUpdateEvent(Party party, Type type, UUID player) {
        this.party = party;
        this.type = type;
        this.player = player;
    }

    /** @return party */
    public Party party() {
        return party;
    }

    /** @return change type */
    public Type type() {
        return type;
    }

    /** @return affected player */
    public UUID player() {
        return player;
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
