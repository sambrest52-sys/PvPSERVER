package net.pvpserver.core.party;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyTest {

    private final UUID leader = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private Party party;
    private static final long NOW = 1_000_000L;

    @BeforeEach
    void setUp() {
        party = new Party(UUID.randomUUID(), leader, 3);
    }

    @Test
    void leaderIsFirstMember() {
        assertTrue(party.contains(leader));
        assertTrue(party.isLeader(leader));
        assertEquals(1, party.size());
    }

    @Test
    void invitedPlayerCanJoin() {
        assertEquals(PartyResult.SUCCESS, party.invite(leader, alice, NOW + 60_000, NOW));
        assertTrue(party.hasInvite(alice, NOW));
        assertEquals(PartyResult.SUCCESS, party.join(alice, NOW + 1));
        assertTrue(party.contains(alice));
        assertFalse(party.hasInvite(alice, NOW + 1), "invite is consumed");
    }

    @Test
    void uninvitedPlayerCannotJoinClosedParty() {
        assertEquals(PartyResult.NOT_INVITED, party.join(alice, NOW));
    }

    @Test
    void openPartyAcceptsAnyone() {
        assertEquals(PartyResult.SUCCESS, party.setOpen(leader, true));
        assertEquals(PartyResult.SUCCESS, party.join(alice, NOW));
    }

    @Test
    void expiredInviteIsRejectedAndPurged() {
        party.invite(leader, alice, NOW + 10, NOW);
        assertEquals(PartyResult.NOT_INVITED, party.join(alice, NOW + 11));
        assertEquals(List.of(alice), party.expireInvites(NOW + 11));
        assertFalse(party.hasInvite(alice, NOW));
    }

    @Test
    void onlyLeaderInvitesKicksAndPromotes() {
        party.setOpen(leader, true);
        party.join(alice, NOW);
        assertEquals(PartyResult.NOT_LEADER, party.invite(alice, bob, NOW + 100, NOW));
        assertEquals(PartyResult.NOT_LEADER, party.kick(alice, leader));
        assertEquals(PartyResult.NOT_LEADER, party.promote(alice, alice));
        assertEquals(PartyResult.NOT_LEADER, party.disband(alice));
    }

    @Test
    void cannotTargetSelfOrDuplicateInvites() {
        assertEquals(PartyResult.SELF, party.invite(leader, leader, NOW + 100, NOW));
        assertEquals(PartyResult.SUCCESS, party.invite(leader, alice, NOW + 100, NOW));
        assertEquals(PartyResult.ALREADY_INVITED, party.invite(leader, alice, NOW + 100, NOW));
    }

    @Test
    void sizeLimitIsEnforced() {
        party.setOpen(leader, true);
        party.join(alice, NOW);
        party.join(bob, NOW);
        assertEquals(PartyResult.FULL, party.join(carol, NOW));
        assertEquals(PartyResult.FULL, party.invite(leader, carol, NOW + 100, NOW));
    }

    @Test
    void leaderLeavingPromotesOldestMember() {
        party.setOpen(leader, true);
        party.join(alice, NOW);
        party.join(bob, NOW);
        assertEquals(PartyResult.NEW_LEADER, party.leave(leader));
        assertTrue(party.isLeader(alice));
        assertEquals(PartyResult.SUCCESS, party.leave(bob));
        assertEquals(PartyResult.EMPTY, party.leave(alice));
        assertTrue(party.isDisbanded());
        assertEquals(PartyResult.DISBANDED, party.join(carol, NOW));
    }

    @Test
    void kickAndPromote() {
        party.setOpen(leader, true);
        party.join(alice, NOW);
        assertEquals(PartyResult.NOT_MEMBER, party.kick(leader, bob));
        assertEquals(PartyResult.SUCCESS, party.promote(leader, alice));
        assertTrue(party.isLeader(alice));
        assertEquals(PartyResult.SUCCESS, party.kick(alice, leader));
        assertFalse(party.contains(leader));
    }

    @Test
    void leaveOfNonMemberIsReported() {
        assertEquals(PartyResult.NOT_MEMBER, party.leave(alice));
    }

    @Test
    void splitAlternatesMembersIntoBalancedTeams() {
        List<UUID> members = new ArrayList<>(List.of(leader, alice, bob, carol, UUID.randomUUID()));
        List<List<UUID>> teams = Party.split(members);
        assertEquals(3, teams.get(0).size());
        assertEquals(2, teams.get(1).size());
        assertTrue(teams.get(0).contains(leader) && teams.get(1).contains(alice));
    }
}
