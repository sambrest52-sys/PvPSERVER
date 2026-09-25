package net.pvpserver.duels.request;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.party.Party;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.Setting;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.core.util.Tasks;
import net.pvpserver.duels.PvPDuels;
import net.pvpserver.duels.match.Match;
import net.pvpserver.duels.match.MatchParticipant;
import net.pvpserver.duels.match.MatchType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Duel and party-duel challenges with clickable accept/deny, expiry, and rematches.
 */
public final class RequestManager implements Listener {

    private final PvPDuels plugin;
    private final PracticeApi api;
    private final MessageService messages;
    /** target → (sender → request) */
    private final Map<UUID, Map<UUID, DuelRequest>> requests = new ConcurrentHashMap<>();
    private final Map<UUID, Rematch> rematches = new ConcurrentHashMap<>();
    private long expireMillis = 30_000;
    private long rematchMillis = 30_000;
    private int maxRounds = 5;

    /**
     * @param plugin duels plugin
     */
    public RequestManager(PvPDuels plugin) {
        this.plugin = plugin;
        this.api = plugin.api();
        this.messages = plugin.messages();
        Tasks.timer(this::expire, 40L, 40L);
    }

    /**
     * @param section {@code requests} section of config.yml
     */
    public void configure(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        expireMillis = section.getInt("expire-seconds", 30) * 1000L;
        rematchMillis = section.getInt("rematch-seconds", 30) * 1000L;
        maxRounds = Math.max(1, section.getInt("max-rounds", 5));
    }

    /** @return maximum rounds selectable in the duel menu */
    public int maxRounds() {
        return maxRounds;
    }

    /**
     * Validates and sends a challenge. Party leaders challenging another party leader create a party duel.
     *
     * @param sender challenger
     * @param target challenged
     * @param kit kit
     * @param arena arena or null
     * @param rounds rounds to win
     * @return whether it was sent
     */
    public boolean send(Player sender, Player target, Kit kit, String arena, int rounds) {
        if (sender == target) {
            messages.send(sender, "duel.self");
            return false;
        }
        if (!api.states().is(sender, PlayerState.LOBBY, PlayerState.QUEUE)) {
            messages.send(sender, "duel.busy");
            return false;
        }
        if (!api.states().is(target, PlayerState.LOBBY, PlayerState.QUEUE) || !sender.canSee(target)) {
            messages.send(sender, "duel.target-busy", MessageService.p("player", target.getName()));
            return false;
        }
        PlayerProfile targetProfile = api.profiles().get(target);
        if (targetProfile != null && (!targetProfile.settings().is(Setting.DUEL_REQUESTS) || targetProfile.ignored().contains(sender.getUniqueId()))) {
            messages.send(sender, "duel.disabled", MessageService.p("player", target.getName()));
            return false;
        }
        Optional<Party> senderParty = api.parties().partyOf(sender);
        Optional<Party> targetParty = api.parties().partyOf(target);
        boolean party = senderParty.isPresent() && targetParty.isPresent();
        if (party) {
            if (senderParty.get() == targetParty.get()) {
                messages.send(sender, "duel.same-party");
                return false;
            }
            if (!senderParty.get().isLeader(sender.getUniqueId()) || !targetParty.get().isLeader(target.getUniqueId())) {
                messages.send(sender, "duel.party-leaders-only");
                return false;
            }
        } else if (senderParty.isPresent() || targetParty.isPresent()) {
            messages.send(sender, "duel.party-mismatch");
            return false;
        }
        Map<UUID, DuelRequest> pending = requests.computeIfAbsent(target.getUniqueId(), k -> new ConcurrentHashMap<>());
        DuelRequest existing = pending.get(sender.getUniqueId());
        if (existing != null && !existing.expired(System.currentTimeMillis())) {
            messages.send(sender, "duel.already-sent", MessageService.p("player", target.getName()));
            return false;
        }
        int clampedRounds = Math.max(1, Math.min(maxRounds, rounds));
        DuelRequest request = new DuelRequest(sender.getUniqueId(), target.getUniqueId(), kit.id(), arena, clampedRounds, party,
                System.currentTimeMillis() + expireMillis);
        pending.put(sender.getUniqueId(), request);
        String arenaName = arena == null ? "Random" : MessageService.mini().stripTags(
                api.arenas().arena(arena) == null ? arena : api.arenas().arena(arena).displayName());
        messages.send(sender, party ? "duel.party-sent" : "duel.sent", MessageService.p("player", target.getName()),
                MessageService.c("kit", kit.name()));
        Component accept = messages.get("duel.accept-button").clickEvent(ClickEvent.runCommand("/duel accept " + sender.getName()))
                .hoverEvent(messages.get("duel.accept-hover"));
        Component deny = messages.get("duel.deny-button").clickEvent(ClickEvent.runCommand("/duel deny " + sender.getName()))
                .hoverEvent(messages.get("duel.deny-hover"));
        messages.send(target, party ? "duel.party-received" : "duel.received", MessageService.p("player", sender.getName()),
                MessageService.c("kit", kit.name()), MessageService.p("arena", arenaName),
                MessageService.p("rounds", clampedRounds), MessageService.p("seconds", expireMillis / 1000),
                MessageService.p("size", senderParty.map(Party::size).orElse(1)),
                MessageService.c("accept", accept), MessageService.c("deny", deny));
        return true;
    }

    /**
     * Accepts the request from {@code senderName}.
     *
     * @param target accepting player
     * @param senderName challenger name
     */
    public void accept(Player target, String senderName) {
        Player sender = Bukkit.getPlayerExact(senderName);
        Map<UUID, DuelRequest> pending = requests.get(target.getUniqueId());
        DuelRequest request = sender == null || pending == null ? null : pending.get(sender.getUniqueId());
        if (request == null || request.expired(System.currentTimeMillis())) {
            messages.send(target, "duel.no-request", MessageService.p("player", senderName));
            return;
        }
        pending.remove(sender.getUniqueId());
        Optional<Kit> kit = api.kits().get(request.kit());
        if (kit.isEmpty()) {
            messages.send(target, "duel.kit-gone");
            return;
        }
        List<List<UUID>> teams = new ArrayList<>();
        if (request.party()) {
            Optional<Party> senderParty = api.parties().partyOf(sender);
            Optional<Party> targetParty = api.parties().partyOf(target);
            if (senderParty.isEmpty() || targetParty.isEmpty() || !senderParty.get().isLeader(sender.getUniqueId())) {
                messages.send(target, "duel.party-changed");
                return;
            }
            if (!allAvailable(target, senderParty.get()) || !allAvailable(target, targetParty.get())) {
                return;
            }
            teams.add(new ArrayList<>(senderParty.get().members()));
            teams.add(new ArrayList<>(targetParty.get().members()));
        } else {
            if (!available(sender) || !available(target)) {
                messages.send(target, "duel.target-busy", MessageService.p("player", sender.getName()));
                return;
            }
            teams.add(List.of(sender.getUniqueId()));
            teams.add(List.of(target.getUniqueId()));
        }
        messages.send(sender, "duel.accepted", MessageService.p("player", target.getName()));
        plugin.matches().create(request.party() ? MatchType.PARTY_VS_PARTY : MatchType.DUEL, kit.get(), false, teams,
                request.rounds(), request.arena());
    }

    private boolean available(Player player) {
        return player.isOnline() && api.states().is(player, PlayerState.LOBBY, PlayerState.QUEUE);
    }

    private boolean allAvailable(Player notify, Party party) {
        for (UUID member : party.members()) {
            Player player = Bukkit.getPlayer(member);
            if (player == null || !available(player)) {
                messages.send(notify, "duel.party-member-busy", MessageService.p("player", player == null ? "?" : player.getName()));
                return false;
            }
        }
        return true;
    }

    /**
     * @param target denying player
     * @param senderName challenger name
     */
    public void deny(Player target, String senderName) {
        Player sender = Bukkit.getPlayerExact(senderName);
        Map<UUID, DuelRequest> pending = requests.get(target.getUniqueId());
        if (sender == null || pending == null || pending.remove(sender.getUniqueId()) == null) {
            messages.send(target, "duel.no-request", MessageService.p("player", senderName));
            return;
        }
        messages.send(target, "duel.denied-self", MessageService.p("player", sender.getName()));
        messages.send(sender, "duel.denied", MessageService.p("player", target.getName()));
    }

    /**
     * @param target player
     * @return names of players with pending requests to the target
     */
    public List<String> pendingSenders(Player target) {
        Map<UUID, DuelRequest> pending = requests.get(target.getUniqueId());
        List<String> names = new ArrayList<>();
        if (pending != null) {
            for (UUID sender : pending.keySet()) {
                Player player = Bukkit.getPlayer(sender);
                if (player != null) {
                    names.add(player.getName());
                }
            }
        }
        return names;
    }

    /**
     * Drops requests involving a player (joined a match, quit).
     *
     * @param uuid player
     */
    public void clearFor(UUID uuid) {
        requests.remove(uuid);
        for (Map<UUID, DuelRequest> pending : requests.values()) {
            pending.remove(uuid);
        }
    }

    // ------------------------------------------------------------------ rematch

    /**
     * Remembers both players of a finished 1v1 for {@code /rematch}.
     *
     * @param match finished match
     */
    public void rememberRematch(Match match) {
        if (match.participants().size() != 2 || match.cancelled()) {
            return;
        }
        List<MatchParticipant> list = new ArrayList<>(match.participants());
        long expires = System.currentTimeMillis() + rematchMillis;
        String arena = match.arena() == null ? null : match.arena().arena().name();
        rematches.put(list.get(0).uuid(), new Rematch(list.get(1).uuid(), match.kit().id(), arena, match.roundsToWin(), expires));
        rematches.put(list.get(1).uuid(), new Rematch(list.get(0).uuid(), match.kit().id(), arena, match.roundsToWin(), expires));
    }

    /**
     * Sends (or accepts) a rematch.
     *
     * @param player player
     */
    public void rematch(Player player) {
        Rematch rematch = rematches.get(player.getUniqueId());
        if (rematch == null || System.currentTimeMillis() > rematch.expiresAt) {
            messages.send(player, "duel.no-rematch");
            return;
        }
        Player opponent = Bukkit.getPlayer(rematch.opponent);
        if (opponent == null) {
            messages.send(player, "duel.no-rematch");
            return;
        }
        Map<UUID, DuelRequest> incoming = requests.get(player.getUniqueId());
        if (incoming != null && incoming.containsKey(opponent.getUniqueId())) {
            accept(player, opponent.getName());
            return;
        }
        Optional<Kit> kit = api.kits().get(rematch.kit);
        if (kit.isEmpty()) {
            messages.send(player, "duel.kit-gone");
            return;
        }
        if (send(player, opponent, kit.get(), rematch.arena, rematch.rounds)) {
            rematches.remove(player.getUniqueId());
        }
    }

    private void expire() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<UUID, DuelRequest>> entry : requests.entrySet()) {
            entry.getValue().values().removeIf(request -> {
                if (request.expired(now)) {
                    Player sender = Bukkit.getPlayer(request.sender());
                    Player target = Bukkit.getPlayer(request.target());
                    if (sender != null) {
                        messages.send(sender, "duel.expired", MessageService.p("player", target == null ? "?" : target.getName()));
                    }
                    return true;
                }
                return false;
            });
        }
        rematches.values().removeIf(r -> now > r.expiresAt);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearFor(event.getPlayer().getUniqueId());
        rematches.remove(event.getPlayer().getUniqueId());
    }

    private record Rematch(UUID opponent, String kit, String arena, int rounds, long expiresAt) {
    }
}
