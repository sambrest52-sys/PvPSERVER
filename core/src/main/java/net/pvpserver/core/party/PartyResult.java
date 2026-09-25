package net.pvpserver.core.party;

/**
 * Outcome of a party operation. Pure value, no Bukkit dependency.
 */
public enum PartyResult {
    SUCCESS,
    NOT_LEADER,
    NOT_MEMBER,
    ALREADY_MEMBER,
    ALREADY_INVITED,
    NOT_INVITED,
    FULL,
    SELF,
    DISBANDED,
    /** Leaving member was the leader; leadership passed to the next member. */
    NEW_LEADER,
    /** Last member left; the party no longer exists. */
    EMPTY
}
