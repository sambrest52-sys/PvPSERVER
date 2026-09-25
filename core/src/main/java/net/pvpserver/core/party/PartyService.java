package net.pvpserver.core.party;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.pvpserver.core.api.BridgeRegistry;
import net.pvpserver.core.api.bridge.LobbyBridge;
import net.pvpserver.core.api.bridge.QueueBridge;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.ProfileService;
import net.pvpserver.core.profile.Setting;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.core.state.PlayerStateService;
import net.pvpserver.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bukkit side of the party system: lookup, messaging, invites with clickable accept, quit handling.
 */
public final class PartyService implements Listener {

    private final MessageService messages;
    private final ProfileService profiles;
    private final PlayerStateService states;
    private final BridgeRegistry bridges;
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> memberToParty = new ConcurrentHashMap<>();
    private int maxSize = 8;
    private int maxSizeLarge = 16;
    private long inviteMillis = 60_000;

    /**
     * @param messages messages
     * @param profiles profiles
     * @param states states
     * @param bridges bridges (queue/lobby)
     */
    public PartyService(MessageService messages, ProfileService profiles, PlayerStateService states, BridgeRegistry bridges) {
        this.messages = messages;
        this.profiles = profiles;
        this.states = states;
        this.bridges = bridges;
        Tasks.timer(this::expireInvites, 100L, 100L);
    }

    /**
     * @param section {@code party} section of config.yml
     */
    public void configure(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        maxSize = section.getInt("max-size", 8);
        maxSizeLarge = section.getInt("max-size-large", 16);
        inviteMillis = section.getInt("invite-seconds", 60) * 1000L;
    }

    /**
     * @param player player
     * @return party the player is in
     */
    public Optional<Party> partyOf(Player player) {
        return partyOf(player.getUniqueId());
    }

    /**
     * @param uuid player id
     * @return party the player is in
     */
    public Optional<Party> partyOf(UUID uuid) {
        UUID partyId = memberToParty.get(uuid);
        return partyId == null ? Optional.empty() : Optional.ofNullable(parties.get(partyId));
    }

    /** @return all live parties */
    public Collection<Party> parties() {
        return parties.values();
    }

    /**
     * Creates a party led by the player.
     *
     * @param player leader
     * @return the party, or empty when the player already has one or is busy
     */
    public Optional<Party> create(Player player) {
        if (memberToParty.containsKey(player.getUniqueId())) {
            messages.send(player, "party.already-in-party");
            return Optional.empty();
        }
        if (!lobbySide(player)) {
            messages.send(player, "party.busy");
            return Optional.empty();
        }
        leaveQueue(player);
        int size = player.hasPermission("pvp.party.large") ? maxSizeLarge : maxSize;
        Party party = new Party(UUID.randomUUID(), player.getUniqueId(), size);
        parties.put(party.id(), party);
        memberToParty.put(player.getUniqueId(), party.id());
        messages.send(player, "party.created");
        fire(party, PartyUpdateEvent.Type.CREATE, player.getUniqueId());
        return Optional.of(party);
    }

    /**
     * Invites a player, creating a party first if needed.
     *
     * @param leader inviting player
     * @param target invited player
     */
    public void invite(Player leader, Player target) {
        Party party = partyOf(leader).orElseGet(() -> create(leader).orElse(null));
        if (party == null) {
            return;
        }
        PlayerProfile targetProfile = profiles.get(target);
        if (targetProfile != null && !targetProfile.settings().is(Setting.PARTY_INVITES) && !leader.hasPermission("pvp.staff")) {
            messages.send(leader, "party.invites-disabled", MessageService.p("player", target.getName()));
            return;
        }
        if (targetProfile != null && targetProfile.ignored().contains(leader.getUniqueId())) {
            messages.send(leader, "party.invites-disabled", MessageService.p("player", target.getName()));
            return;
        }
        long now = System.currentTimeMillis();
        PartyResult result = party.invite(leader.getUniqueId(), target.getUniqueId(), now + inviteMillis, now);
        if (result != PartyResult.SUCCESS) {
            sendResult(leader, result, target.getName());
            return;
        }
        broadcast(party, "party.invited", MessageService.p("player", target.getName()));
        Component accept = messages.get("party.invite-accept-button")
                .clickEvent(ClickEvent.runCommand("/party accept " + leader.getName()))
                .hoverEvent(messages.get("party.invite-accept-hover"));
        messages.send(target, "party.invite-received", MessageService.p("player", leader.getName()),
                MessageService.c("accept", accept), MessageService.p("seconds", inviteMillis / 1000));
    }

    /**
     * Accepts an invite to (or joins the open party of) the given leader.
     *
     * @param player joining player
     * @param leader leader of the party to join
     */
    public void join(Player player, Player leader) {
        if (memberToParty.containsKey(player.getUniqueId())) {
            messages.send(player, "party.already-in-party");
            return;
        }
        Optional<Party> optional = partyOf(leader);
        if (optional.isEmpty()) {
            messages.send(player, "party.not-found", MessageService.p("player", leader.getName()));
            return;
        }
        if (!lobbySide(player)) {
            messages.send(player, "party.busy");
            return;
        }
        Party party = optional.get();
        PartyResult result = party.join(player.getUniqueId(), System.currentTimeMillis());
        if (result != PartyResult.SUCCESS) {
            sendResult(player, result, leader.getName());
            return;
        }
        leaveQueue(player);
        memberToParty.put(player.getUniqueId(), party.id());
        broadcast(party, "party.joined", MessageService.p("player", player.getName()));
        fire(party, PartyUpdateEvent.Type.JOIN, player.getUniqueId());
    }

    /**
     * Leaves the current party. Leader leaving passes leadership on.
     *
     * @param player player
     * @param quitting true when called because the player disconnected
     */
    public void leave(Player player, boolean quitting) {
        Optional<Party> optional = partyOf(player);
        if (optional.isEmpty()) {
            if (!quitting) {
                messages.send(player, "party.not-in-party");
            }
            return;
        }
        Party party = optional.get();
        PartyResult result = party.leave(player.getUniqueId());
        memberToParty.remove(player.getUniqueId());
        if (!quitting) {
            messages.send(player, "party.left-self");
        }
        if (result == PartyResult.EMPTY) {
            parties.remove(party.id());
            fire(party, PartyUpdateEvent.Type.DISBAND, player.getUniqueId());
            return;
        }
        broadcast(party, quitting ? "party.left-quit" : "party.left", MessageService.p("player", player.getName()));
        if (result == PartyResult.NEW_LEADER) {
            broadcast(party, "party.new-leader", MessageService.p("player", nameOf(party.leader())));
        }
        fire(party, PartyUpdateEvent.Type.LEAVE, player.getUniqueId());
    }

    /**
     * @param leader leader
     * @param target member to kick
     */
    public void kick(Player leader, String target) {
        Optional<Party> optional = partyOf(leader);
        if (optional.isEmpty()) {
            messages.send(leader, "party.not-in-party");
            return;
        }
        Party party = optional.get();
        UUID targetId = findMember(party, target);
        if (targetId == null) {
            messages.send(leader, "party.not-member", MessageService.p("player", target));
            return;
        }
        PartyResult result = party.kick(leader.getUniqueId(), targetId);
        if (result != PartyResult.SUCCESS) {
            sendResult(leader, result, target);
            return;
        }
        memberToParty.remove(targetId);
        Player kicked = Bukkit.getPlayer(targetId);
        if (kicked != null) {
            messages.send(kicked, "party.kicked-self");
        }
        broadcast(party, "party.kicked", MessageService.p("player", nameOf(targetId)));
        fire(party, PartyUpdateEvent.Type.KICK, targetId);
    }

    /**
     * @param leader leader
     * @param target member to promote
     */
    public void promote(Player leader, String target) {
        Optional<Party> optional = partyOf(leader);
        if (optional.isEmpty()) {
            messages.send(leader, "party.not-in-party");
            return;
        }
        Party party = optional.get();
        UUID targetId = findMember(party, target);
        if (targetId == null) {
            messages.send(leader, "party.not-member", MessageService.p("player", target));
            return;
        }
        PartyResult result = party.promote(leader.getUniqueId(), targetId);
        if (result != PartyResult.SUCCESS) {
            sendResult(leader, result, target);
            return;
        }
        broadcast(party, "party.new-leader", MessageService.p("player", nameOf(targetId)));
        fire(party, PartyUpdateEvent.Type.PROMOTE, targetId);
    }

    /**
     * @param leader leader
     */
    public void disband(Player leader) {
        Optional<Party> optional = partyOf(leader);
        if (optional.isEmpty()) {
            messages.send(leader, "party.not-in-party");
            return;
        }
        Party party = optional.get();
        if (!party.isLeader(leader.getUniqueId())) {
            messages.send(leader, "party.not-leader");
            return;
        }
        if (states.firstBusy(party.members()) != null && anyInState(party, PlayerState.MATCH)) {
            messages.send(leader, "party.busy-members");
            return;
        }
        broadcast(party, "party.disbanded");
        party.disband(leader.getUniqueId());
        forceRemove(party);
        fire(party, PartyUpdateEvent.Type.DISBAND, leader.getUniqueId());
    }

    /**
     * @param leader leader
     * @param open new open state
     */
    public void setOpen(Player leader, boolean open) {
        Optional<Party> optional = partyOf(leader);
        if (optional.isEmpty()) {
            messages.send(leader, "party.not-in-party");
            return;
        }
        PartyResult result = optional.get().setOpen(leader.getUniqueId(), open);
        if (result != PartyResult.SUCCESS) {
            sendResult(leader, result, leader.getName());
            return;
        }
        broadcast(optional.get(), open ? "party.opened" : "party.closed");
    }

    /**
     * Sends party chat.
     *
     * @param player sender
     * @param message raw text
     */
    public void chat(Player player, String message) {
        Optional<Party> optional = partyOf(player);
        if (optional.isEmpty()) {
            messages.send(player, "party.not-in-party");
            return;
        }
        broadcast(optional.get(), "party.chat", MessageService.p("player", player.getName()), MessageService.p("message", message));
    }

    /**
     * Shows party info.
     *
     * @param viewer receiver
     * @param party party
     */
    public void info(Player viewer, Party party) {
        List<String> names = new ArrayList<>();
        for (UUID member : party.members()) {
            names.add((party.isLeader(member) ? "★" : "") + nameOf(member));
        }
        messages.send(viewer, "party.info", MessageService.p("leader", nameOf(party.leader())),
                MessageService.p("size", party.size()), MessageService.p("max", party.maxSize()),
                MessageService.p("members", String.join(", ", names)),
                MessageService.p("status", party.isOpen() ? "Open" : "Invite only"));
    }

    /**
     * Sends a message to all online members.
     *
     * @param party party
     * @param key message key
     * @param resolvers placeholders
     */
    public void broadcast(Party party, String key, TagResolver... resolvers) {
        for (UUID member : party.members()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                messages.send(player, key, resolvers);
            }
        }
    }

    /**
     * @param party party
     * @return online members
     */
    public List<Player> onlineMembers(Party party) {
        List<Player> players = new ArrayList<>();
        for (UUID member : party.members()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    private boolean anyInState(Party party, PlayerState state) {
        for (UUID member : party.members()) {
            if (states.get(member) == state) {
                return true;
            }
        }
        return false;
    }

    private void forceRemove(Party party) {
        parties.remove(party.id());
        for (UUID member : party.members()) {
            memberToParty.remove(member);
        }
    }

    private boolean lobbySide(Player player) {
        PlayerState state = states.get(player);
        return state == PlayerState.LOBBY || state == PlayerState.QUEUE;
    }

    private void leaveQueue(Player player) {
        if (states.get(player) == PlayerState.QUEUE) {
            bridges.get(QueueBridge.class).ifPresent(q -> q.leaveQueue(player));
        }
    }

    private UUID findMember(Party party, String name) {
        for (UUID member : party.members()) {
            if (nameOf(member).equalsIgnoreCase(name)) {
                return member;
            }
        }
        return null;
    }

    private String nameOf(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }
        PlayerProfile profile = profiles.get(uuid);
        return profile != null ? profile.name() : uuid.toString().substring(0, 8);
    }

    private void sendResult(Player player, PartyResult result, String target) {
        String key = switch (result) {
            case NOT_LEADER -> "party.not-leader";
            case NOT_MEMBER -> "party.not-member";
            case ALREADY_MEMBER -> "party.already-member";
            case ALREADY_INVITED -> "party.already-invited";
            case NOT_INVITED -> "party.not-invited";
            case FULL -> "party.full";
            case SELF -> "party.self";
            case DISBANDED -> "party.not-found";
            default -> "command.error";
        };
        messages.send(player, key, MessageService.p("player", target));
    }

    private void fire(Party party, PartyUpdateEvent.Type type, UUID player) {
        Bukkit.getPluginManager().callEvent(new PartyUpdateEvent(party, type, player));
        // Lobby hotbars depend on party membership.
        bridges.get(LobbyBridge.class).ifPresent(lobby -> {
            for (Player member : onlineMembers(party)) {
                if (states.get(member) == PlayerState.LOBBY || states.get(member) == PlayerState.QUEUE) {
                    lobby.refreshHotbar(member);
                }
            }
            Player affected = Bukkit.getPlayer(player);
            if (affected != null && !party.contains(player)
                    && (states.get(affected) == PlayerState.LOBBY || states.get(affected) == PlayerState.QUEUE)) {
                lobby.refreshHotbar(affected);
            }
        });
    }

    private void expireInvites() {
        long now = System.currentTimeMillis();
        for (Party party : parties.values()) {
            for (UUID expired : party.expireInvites(now)) {
                Player player = Bukkit.getPlayer(expired);
                if (player != null) {
                    messages.send(player, "party.invite-expired", MessageService.p("player", nameOf(party.leader())));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        leave(event.getPlayer(), true);
    }
}
