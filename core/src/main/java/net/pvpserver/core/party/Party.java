package net.pvpserver.core.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pure party model: members, leader, invitations and open/closed state. Contains no Bukkit types so it can be unit
 * tested directly. Not thread safe; mutate on the main thread.
 */
public final class Party {

    private final UUID id;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Map<UUID, Long> invites = new HashMap<>();
    private final int maxSize;
    private UUID leader;
    private boolean open;
    private boolean disbanded;

    /**
     * @param id party id
     * @param leader founding leader
     * @param maxSize maximum members (at least 2)
     */
    public Party(UUID id, UUID leader, int maxSize) {
        this.id = id;
        this.leader = leader;
        this.maxSize = Math.max(2, maxSize);
        members.add(leader);
    }

    /**
     * Invites a player.
     *
     * @param actor inviting member (must be leader)
     * @param target invited player
     * @param expiresAt invite expiry (epoch millis)
     * @param now current time
     * @return result
     */
    public PartyResult invite(UUID actor, UUID target, long expiresAt, long now) {
        if (disbanded) return PartyResult.DISBANDED;
        if (!actor.equals(leader)) return PartyResult.NOT_LEADER;
        if (actor.equals(target)) return PartyResult.SELF;
        if (members.contains(target)) return PartyResult.ALREADY_MEMBER;
        if (members.size() >= maxSize) return PartyResult.FULL;
        if (hasInvite(target, now)) return PartyResult.ALREADY_INVITED;
        invites.put(target, expiresAt);
        return PartyResult.SUCCESS;
    }

    /**
     * @param player player
     * @param now current time
     * @return whether the player has a valid invite
     */
    public boolean hasInvite(UUID player, long now) {
        Long expiry = invites.get(player);
        return expiry != null && expiry > now;
    }

    /**
     * Joins the party via invite or because it is open.
     *
     * @param player joining player
     * @param now current time
     * @return result
     */
    public PartyResult join(UUID player, long now) {
        if (disbanded) return PartyResult.DISBANDED;
        if (members.contains(player)) return PartyResult.ALREADY_MEMBER;
        if (!open && !hasInvite(player, now)) return PartyResult.NOT_INVITED;
        if (members.size() >= maxSize) return PartyResult.FULL;
        invites.remove(player);
        members.add(player);
        return PartyResult.SUCCESS;
    }

    /**
     * Removes a member. If the leader leaves, the longest-standing member becomes leader.
     *
     * @param player leaving player
     * @return SUCCESS, NEW_LEADER, EMPTY or NOT_MEMBER
     */
    public PartyResult leave(UUID player) {
        if (!members.remove(player)) return PartyResult.NOT_MEMBER;
        if (members.isEmpty()) {
            disbanded = true;
            return PartyResult.EMPTY;
        }
        if (player.equals(leader)) {
            leader = members.iterator().next();
            return PartyResult.NEW_LEADER;
        }
        return PartyResult.SUCCESS;
    }

    /**
     * @param actor acting member
     * @param target member to kick
     * @return result
     */
    public PartyResult kick(UUID actor, UUID target) {
        if (disbanded) return PartyResult.DISBANDED;
        if (!actor.equals(leader)) return PartyResult.NOT_LEADER;
        if (actor.equals(target)) return PartyResult.SELF;
        if (!members.remove(target)) return PartyResult.NOT_MEMBER;
        return PartyResult.SUCCESS;
    }

    /**
     * @param actor current leader
     * @param target new leader
     * @return result
     */
    public PartyResult promote(UUID actor, UUID target) {
        if (disbanded) return PartyResult.DISBANDED;
        if (!actor.equals(leader)) return PartyResult.NOT_LEADER;
        if (actor.equals(target)) return PartyResult.SELF;
        if (!members.contains(target)) return PartyResult.NOT_MEMBER;
        leader = target;
        return PartyResult.SUCCESS;
    }

    /**
     * @param actor acting player (must be leader)
     * @return result
     */
    public PartyResult disband(UUID actor) {
        if (disbanded) return PartyResult.DISBANDED;
        if (!actor.equals(leader)) return PartyResult.NOT_LEADER;
        disbanded = true;
        return PartyResult.SUCCESS;
    }

    /**
     * @param actor acting player (must be leader)
     * @param open whether anyone may join without invite
     * @return result
     */
    public PartyResult setOpen(UUID actor, boolean open) {
        if (disbanded) return PartyResult.DISBANDED;
        if (!actor.equals(leader)) return PartyResult.NOT_LEADER;
        this.open = open;
        return PartyResult.SUCCESS;
    }

    /**
     * Drops expired invites.
     *
     * @param now current time
     * @return players whose invites expired
     */
    public List<UUID> expireInvites(long now) {
        List<UUID> expired = new ArrayList<>();
        invites.entrySet().removeIf(entry -> {
            if (entry.getValue() <= now) {
                expired.add(entry.getKey());
                return true;
            }
            return false;
        });
        return expired;
    }

    /**
     * Splits members into two teams as evenly as possible, preserving the given order (callers shuffle first).
     *
     * @param ordered members in the desired order
     * @return two teams
     */
    public static List<List<UUID>> split(List<UUID> ordered) {
        List<UUID> a = new ArrayList<>();
        List<UUID> b = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            (i % 2 == 0 ? a : b).add(ordered.get(i));
        }
        return List.of(a, b);
    }

    /** @return party id */
    public UUID id() {
        return id;
    }

    /** @return leader id */
    public UUID leader() {
        return leader;
    }

    /**
     * @param player player
     * @return whether the player leads the party
     */
    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    /**
     * @param player player
     * @return whether the player is a member
     */
    public boolean contains(UUID player) {
        return members.contains(player);
    }

    /** @return members in join order (leader not necessarily first after promotions) */
    public Set<UUID> members() {
        return Collections.unmodifiableSet(members);
    }

    /** @return member count */
    public int size() {
        return members.size();
    }

    /** @return maximum size */
    public int maxSize() {
        return maxSize;
    }

    /** @return whether the party is public */
    public boolean isOpen() {
        return open;
    }

    /** @return whether the party was disbanded */
    public boolean isDisbanded() {
        return disbanded;
    }
}
