package net.pvpserver.duels.match;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.api.bridge.LobbyBridge;
import net.pvpserver.core.arena.ArenaInstance;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.kit.KitService;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.core.stats.MatchRecord;
import net.pvpserver.core.util.Tasks;
import net.pvpserver.core.util.TimeUtil;
import net.pvpserver.duels.PvPDuels;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Drives the match lifecycle: arena acquisition, countdown with freeze, rounds, eliminations, forfeits, results,
 * statistics, snapshots and clean-up back to the lobby.
 */
public final class MatchManager {

    private static final String TEAM_PREFIX = "pvpm_";

    private final PvPDuels plugin;
    private final PracticeApi api;
    private final MessageService messages;
    private final Map<UUID, Match> matches = new ConcurrentHashMap<>();
    private final Map<UUID, Match> byPlayer = new ConcurrentHashMap<>();
    private final SnapshotCache snapshots = new SnapshotCache(300);
    private int countdownSeconds = 5;
    private int roundDelaySeconds = 3;
    private int endDelaySeconds = 4;
    private int bridgeCountdownSeconds = 3;

    /**
     * @param plugin duels plugin
     */
    public MatchManager(PvPDuels plugin) {
        this.plugin = plugin;
        this.api = plugin.api();
        this.messages = plugin.messages();
        Tasks.timer(snapshots::purge, 1200L, 1200L);
        Tasks.timer(this::checkDurations, 20L, 20L);
    }

    /**
     * @param section {@code match} section of config.yml
     */
    public void configure(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        countdownSeconds = Math.max(0, section.getInt("countdown-seconds", 5));
        roundDelaySeconds = Math.max(1, section.getInt("round-delay-seconds", 3));
        endDelaySeconds = Math.max(1, section.getInt("end-delay-seconds", 4));
        bridgeCountdownSeconds = Math.max(0, section.getInt("bridge-countdown-seconds", 3));
        snapshots.ttl(section.getInt("snapshot-seconds", 300));
    }

    // ------------------------------------------------------------------ creation

    /**
     * Creates a match and starts acquiring an arena. All players must be online and not in another match.
     *
     * @param type match type
     * @param kit kit
     * @param ranked ranked flag
     * @param teams player ids per team (at least two teams)
     * @param roundsToWin rounds to win
     * @param preferredArena preferred arena or null
     * @return the match, or null when a player is unavailable
     */
    public Match create(MatchType type, Kit kit, boolean ranked, List<List<UUID>> teams, int roundsToWin, String preferredArena) {
        for (List<UUID> team : teams) {
            for (UUID uuid : team) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || byPlayer.containsKey(uuid)) {
                    abortCreation(teams, uuid);
                    return null;
                }
            }
        }
        Match match = new Match(type, kit, ranked, teams, roundsToWin, preferredArena);
        matches.put(match.id(), match);
        for (MatchParticipant participant : match.participants()) {
            byPlayer.put(participant.uuid(), match);
            Player player = Bukkit.getPlayer(participant.uuid());
            plugin.queues().leave(player, false);
            plugin.requests().clearFor(player.getUniqueId());
            api.states().set(player, PlayerState.MATCH);
            player.closeInventory();
            messages.send(player, "match.finding-arena");
        }
        api.arenas().acquire(arena -> kit.allowsArena(arena.name(), arena.tags()), preferredArena)
                .whenComplete((instance, error) -> Tasks.sync(() -> onArena(match, instance, error)));
        return match;
    }

    private void abortCreation(List<List<UUID>> teams, UUID unavailable) {
        Player missing = Bukkit.getPlayer(unavailable);
        for (List<UUID> team : teams) {
            for (UUID uuid : team) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && !byPlayer.containsKey(uuid)) {
                    messages.send(player, "match.player-unavailable", MessageService.p("player", missing == null ? "A player" : missing.getName()));
                    if (api.states().is(player, PlayerState.QUEUE)) {
                        plugin.queues().leave(player, false);
                    }
                }
            }
        }
    }

    private void onArena(Match match, ArenaInstance instance, Throwable error) {
        if (error != null || instance == null) {
            if (error != null && !(error.getCause() instanceof net.pvpserver.core.arena.NoArenaAvailableException)
                    && !(error instanceof net.pvpserver.core.arena.NoArenaAvailableException)) {
                plugin.getLogger().log(Level.WARNING, "Arena acquisition failed", error);
            }
            match.cancel();
            for (Player player : match.onlinePlayers()) {
                messages.send(player, "match.no-arena", MessageService.c("kit", match.kit().name()));
            }
            finish(match);
            return;
        }
        if (match.cancelled() || match.state() != MatchState.STARTING) {
            api.arenas().release(instance);
            return;
        }
        match.arena(instance);
        if (match.connectedTeams().size() < 2) {
            // Someone left while we were waiting for the arena.
            match.cancel();
            match.onlinePlayers().forEach(p -> messages.send(p, "match.cancelled-left"));
            finish(match);
            return;
        }
        applyTeamColors(match);
        startRound(match, countdownSeconds);
    }

    // ------------------------------------------------------------------ rounds

    private void startRound(Match match, int countdown) {
        match.state(MatchState.COUNTDOWN);
        for (MatchTeam team : match.teams()) {
            team.participants().forEach(MatchParticipant::revive);
        }
        spawnAll(match, true);
        if (match.round() == 1) {
            for (Player spectator : match.onlineSpectators()) {
                spectator.teleport(match.arena().spectatorSpawn());
            }
            announceStart(match);
        } else {
            broadcast(match, "match.round-start", MessageService.p("round", match.round()), MessageService.p("score", scoreLine(match)));
        }
        runCountdown(match, countdown);
    }

    private void runCountdown(Match match, int seconds) {
        if (seconds <= 0) {
            beginFight(match);
            return;
        }
        int[] remaining = {seconds};
        match.task(Tasks.timer(() -> {
            if (match.state() != MatchState.COUNTDOWN) {
                match.task(null);
                return;
            }
            if (remaining[0] <= 0) {
                beginFight(match);
                return;
            }
            for (Player player : match.audience()) {
                messages.title(player, "match.countdown", 0, 25, 5, MessageService.p("seconds", remaining[0]));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            }
            remaining[0]--;
        }, 0L, 20L));
    }

    private void beginFight(Match match) {
        match.task(null);
        match.state(MatchState.FIGHTING);
        match.markFightStart();
        for (Player player : match.audience()) {
            messages.title(player, "match.fight", 0, 20, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        }
    }

    private void spawnAll(Match match, boolean giveKit) {
        List<MatchTeam> teams = match.teams();
        int ffaIndex = 0;
        for (MatchTeam team : teams) {
            int memberIndex = 0;
            for (MatchParticipant participant : team.participants()) {
                Player player = Bukkit.getPlayer(participant.uuid());
                if (player == null || participant.disconnected()) {
                    continue;
                }
                Location spawn = spawnFor(match, team.index(), teams.size() > 2 ? ffaIndex++ : memberIndex++);
                showToParticipants(match, player);
                if (giveKit) {
                    prepare(player, match, team);
                }
                player.teleport(spawn);
                player.setFallDistance(0);
            }
        }
    }

    private Location spawnFor(Match match, int teamIndex, int slot) {
        ArenaInstance arena = match.arena();
        boolean sideA = teamIndex % 2 == 0;
        Location base = sideA ? arena.spawnA() : arena.spawnB();
        if (match.teams().size() > 2) {
            // Party FFA: spread players around the midpoint between the two spawns.
            Location a = arena.spawnA();
            Location b = arena.spawnB();
            double cx = (a.getX() + b.getX()) / 2;
            double cz = (a.getZ() + b.getZ()) / 2;
            double radius = Math.max(3, a.distance(b) / 2);
            double angle = (2 * Math.PI * slot) / Math.max(1, match.participants().size());
            Location spot = new Location(a.getWorld(), cx + Math.cos(angle) * radius, Math.max(a.getY(), b.getY()), cz + Math.sin(angle) * radius);
            spot.setDirection(new org.bukkit.util.Vector(cx - spot.getX(), 0, cz - spot.getZ()));
            return spot;
        }
        if (slot == 0) {
            return base;
        }
        // Team members stand side by side, perpendicular to the facing direction.
        Location spot = base.clone();
        org.bukkit.util.Vector side = base.getDirection().setY(0).normalize().crossProduct(new org.bukkit.util.Vector(0, 1, 0));
        double offset = ((slot + 1) / 2) * 1.5 * (slot % 2 == 0 ? 1 : -1);
        return spot.add(side.multiply(offset));
    }

    /**
     * Resets a player for fighting and gives the kit.
     *
     * @param player player
     * @param match match
     * @param team team
     */
    public void prepare(Player player, Match match, MatchTeam team) {
        player.closeInventory();
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setCollidable(true);
        player.setInvisible(false);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setFireTicks(0);
        player.setLevel(0);
        player.setExp(0);
        api.combat().applyKit(player, match.kit());
        if (match.kit().rules().bridge()) {
            dyeArmor(player, team.color());
            teamBlocks(player, team.index());
        }
        api.sidebars().sidebar(player).healthBelowName(match.kit().rules().healthDisplay());
        KitService.heal(player);
        api.sidebars().refresh(player);
    }

    /** Bridge: white terracotta and wool in the kit become the team's colour (red for team A, blue for team B). */
    private static void teamBlocks(Player player, int teamIndex) {
        boolean red = teamIndex % 2 == 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) {
                continue;
            }
            Material replacement = switch (item.getType()) {
                case WHITE_TERRACOTTA -> red ? Material.RED_TERRACOTTA : Material.BLUE_TERRACOTTA;
                case WHITE_WOOL -> red ? Material.RED_WOOL : Material.BLUE_WOOL;
                case WHITE_CONCRETE -> red ? Material.RED_CONCRETE : Material.BLUE_CONCRETE;
                default -> null;
            };
            if (replacement != null) {
                contents[i] = new ItemStack(replacement, item.getAmount());
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    private static void dyeArmor(Player player, NamedTextColor color) {
        Color bukkitColor = Color.fromRGB(color.value());
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && item.getItemMeta() instanceof LeatherArmorMeta) {
                item.editMeta(meta -> ((LeatherArmorMeta) meta).setColor(bukkitColor));
            }
        }
    }

    private void announceStart(Match match) {
        for (MatchParticipant participant : match.participants()) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player == null) {
                continue;
            }
            List<String> opponents = match.opponentsOf(participant.uuid()).stream().map(MatchParticipant::name).toList();
            String key = match.ranked() ? "match.starting-ranked" : "match.starting";
            int opponentElo = (int) match.opponentsOf(participant.uuid()).stream()
                    .mapToInt(p -> api.stats().eloOf(p.uuid(), match.kit().id())).average().orElse(0);
            messages.send(player, key, MessageService.c("kit", match.kit().name()),
                    MessageService.p("opponent", String.join(", ", opponents)), MessageService.p("elo", opponentElo),
                    MessageService.p("arena", MessageService.mini().stripTags(match.arena().arena().displayName())),
                    MessageService.p("rounds", match.roundsToWin()));
        }
    }

    // ------------------------------------------------------------------ deaths & scoring

    /**
     * Handles a participant dying (fake death, void, sumo water).
     *
     * @param match match
     * @param victim victim
     * @param killer killer or null
     * @param cause damage cause (null for void/water)
     */
    public void handleDeath(Match match, Player victim, Player killer, EntityDamageEvent.DamageCause cause) {
        MatchParticipant participant = match.participant(victim.getUniqueId());
        if (participant == null || !participant.alive() || match.state() != MatchState.FIGHTING) {
            if (participant != null && match.state() != MatchState.FIGHTING) {
                // Deaths outside the fight (e.g. falling during countdown) just respawn.
                victim.teleport(spawnFor(match, participant.team().index(), 0));
            }
            return;
        }
        MatchParticipant killerParticipant = killer == null ? null : match.participant(killer.getUniqueId());
        if (killerParticipant != null && killerParticipant.team() != participant.team()) {
            killerParticipant.kill();
            api.cosmetics().playKillEffect(killer, victim.getLocation());
        } else {
            killer = null;
        }
        api.cosmetics().playDeathAnimation(victim, victim.getLocation());
        if (killer != null) {
            broadcast(match, "match.killed", MessageService.p("victim", victim.getName()), MessageService.p("killer", killer.getName()),
                    MessageService.p("health", String.format(java.util.Locale.ROOT, "%.1f", killer.getHealth() / 2.0)));
        } else {
            broadcast(match, "match.died", MessageService.p("victim", victim.getName()));
        }
        if (match.kit().rules().bridge()) {
            // Bridge: no elimination, respawn at own spawn with a fresh kit.
            respawnBridge(match, victim, participant);
            return;
        }
        store(InventorySnapshot.capture(victim, participant));
        participant.alive(false);
        makeDeadSpectator(match, victim);
        checkRoundEnd(match);
    }

    private void respawnBridge(Match match, Player player, MatchParticipant participant) {
        prepare(player, match, participant.team());
        player.teleport(spawnFor(match, participant.team().index(), 0));
    }

    /**
     * Adds a point for the attacker's team (boxing hits, bridge goals) and checks the win condition.
     *
     * @param match match
     * @param scorer scoring player
     * @param goal whether the point is a bridge goal
     */
    public void score(Match match, Player scorer, boolean goal) {
        MatchParticipant participant = match.participant(scorer.getUniqueId());
        if (participant == null || match.state() != MatchState.FIGHTING) {
            return;
        }
        MatchTeam team = participant.team();
        team.addPoint();
        if (goal) {
            int needed = match.kit().rules().bridgeGoals();
            broadcast(match, "match.goal", MessageService.p("player", scorer.getName()), MessageService.p("team", team.name()),
                    MessageService.p("score", scoreLine(match)));
            for (Player player : match.audience()) {
                messages.title(player, "match.goal-title", 5, 30, 10, MessageService.p("player", scorer.getName()),
                        MessageService.p("score", scoreLine(match)));
            }
            if (team.points() >= needed) {
                end(match, team);
                return;
            }
            // Everyone back to their spawn with a fresh kit and a short freeze.
            match.state(MatchState.COUNTDOWN);
            spawnAll(match, true);
            runCountdown(match, bridgeCountdownSeconds);
            return;
        }
        if (match.kit().rules().boxing() && team.points() >= match.kit().rules().boxingHits()) {
            for (MatchParticipant other : match.participants()) {
                if (other.team() != team) {
                    other.alive(false);
                    Player loser = Bukkit.getPlayer(other.uuid());
                    if (loser != null) {
                        store(InventorySnapshot.capture(loser, other));
                    }
                }
            }
            roundWon(match, team);
        }
    }

    private void checkRoundEnd(Match match) {
        List<MatchTeam> alive = match.aliveTeams();
        if (alive.size() <= 1) {
            roundWon(match, alive.isEmpty() ? null : alive.get(0));
        }
    }

    private void roundWon(Match match, MatchTeam team) {
        if (match.state() != MatchState.FIGHTING) {
            return;
        }
        if (team == null) {
            end(match, null);
            return;
        }
        team.winRound();
        boolean decided = team.roundsWon() >= match.roundsToWin() || match.connectedTeams().size() <= 1;
        if (decided) {
            end(match, team);
            return;
        }
        match.state(MatchState.ROUND_END);
        broadcast(match, "match.round-won", MessageService.p("team", team.names()), MessageService.p("round", match.round()),
                MessageService.p("score", scoreLine(match)));
        match.task(Tasks.later(() -> {
            if (match.state() != MatchState.ROUND_END) {
                return;
            }
            api.arenas().resetInPlace(match.arena()).thenRun(() -> {
                if (match.state() != MatchState.ROUND_END) {
                    return;
                }
                match.nextRound();
                match.teams().forEach(MatchTeam::resetPoints);
                startRound(match, countdownSeconds);
            });
        }, roundDelaySeconds * 20L));
    }

    private String scoreLine(Match match) {
        List<String> parts = new ArrayList<>();
        for (MatchTeam team : match.teams()) {
            parts.add(String.valueOf(match.kit().rules().bridge() ? team.points() : team.roundsWon()));
        }
        return String.join(" - ", parts);
    }

    // ------------------------------------------------------------------ forfeit / quit

    /**
     * Removes a participant (quit or /leave). The match ends when only one team is left.
     *
     * @param player player
     * @param quit whether the player disconnected
     */
    public void forfeit(Player player, boolean quit) {
        Match match = byPlayer.get(player.getUniqueId());
        if (match == null) {
            return;
        }
        MatchParticipant participant = match.participant(player.getUniqueId());
        if (participant == null || participant.disconnected()) {
            return;
        }
        if (match.state() == MatchState.ENDING || match.state() == MatchState.ENDED) {
            if (!quit) {
                leaveEnded(match, player);
            }
            return;
        }
        if (participant.alive()) {
            store(InventorySnapshot.capture(player, participant));
        }
        participant.disconnect();
        byPlayer.remove(player.getUniqueId());
        broadcast(match, quit ? "match.player-quit" : "match.player-forfeit", MessageService.p("player", player.getName()));
        if (!quit) {
            sendToLobby(player);
        }
        if (match.state() == MatchState.STARTING) {
            if (match.connectedTeams().size() < 2) {
                match.cancel();
                match.onlinePlayers().forEach(p -> messages.send(p, "match.cancelled-left"));
                finish(match);
            }
            return;
        }
        List<MatchTeam> connected = match.connectedTeams();
        if (connected.size() <= 1) {
            if (match.state() == MatchState.ROUND_END || match.state() == MatchState.COUNTDOWN) {
                match.state(MatchState.FIGHTING);
            }
            end(match, connected.isEmpty() ? null : connected.get(0));
        } else if (match.state() == MatchState.FIGHTING) {
            checkRoundEnd(match);
        }
    }

    private void leaveEnded(Match match, Player player) {
        byPlayer.remove(player.getUniqueId());
        MatchParticipant participant = match.participant(player.getUniqueId());
        if (participant != null) {
            participant.disconnect();
        }
        sendToLobby(player);
    }

    // ------------------------------------------------------------------ end

    /**
     * Ends a match with a winner (null = draw) and schedules clean-up.
     *
     * @param match match
     * @param winner winning team or null
     */
    public void end(Match match, MatchTeam winner) {
        if (match.state() == MatchState.ENDING || match.state() == MatchState.ENDED) {
            return;
        }
        match.task(null);
        match.state(MatchState.ENDING);
        match.winner(winner);
        for (MatchParticipant participant : match.participants()) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player != null && !participant.disconnected() && participant.alive()) {
                store(InventorySnapshot.capture(player, participant));
            }
        }
        Map<UUID, Integer> eloChanges = Map.of();
        List<UUID> winners = new ArrayList<>();
        List<UUID> losers = new ArrayList<>();
        if (winner != null) {
            for (MatchParticipant participant : match.participants()) {
                (participant.team() == winner ? winners : losers).add(participant.uuid());
            }
        }
        if (match.type().recordsStats() && !match.cancelled() && winner != null) {
            eloChanges = api.stats().recordDuel(match.kit(), match.ranked(), winners, losers);
            int change = winners.isEmpty() ? 0 : eloChanges.getOrDefault(winners.get(0), 0);
            api.stats().saveHistory(new MatchRecord(match.kit().id(), match.type().name(), match.ranked(), winner.names(),
                    String.join(", ", match.teams().stream().filter(t -> t != winner).map(MatchTeam::names).toList()),
                    change, match.durationMillis(), System.currentTimeMillis()));
        }
        announceResult(match, winner, eloChanges);
        if (match.type() == MatchType.QUEUE || match.type() == MatchType.DUEL) {
            plugin.requests().rememberRematch(match);
        }
        match.task(Tasks.later(() -> finish(match), endDelaySeconds * 20L));
    }

    private void announceResult(Match match, MatchTeam winner, Map<UUID, Integer> eloChanges) {
        for (MatchParticipant participant : match.participants()) {
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player == null || participant.disconnected()) {
                continue;
            }
            String titleKey = winner == null ? "match.draw-title" : participant.team() == winner ? "match.victory-title" : "match.defeat-title";
            messages.title(player, titleKey, 5, 50, 15, MessageService.p("winner", winner == null ? "-" : winner.names()));
            player.playSound(player.getLocation(), winner != null && participant.team() == winner
                    ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
        }
        Component inventories = inventoryButtons(match);
        TagResolver[] base = {MessageService.c("kit", match.kit().name()),
                MessageService.p("winner", winner == null ? "Nobody" : winner.names()),
                MessageService.p("loser", winner == null ? "-" : String.join(", ", match.teams().stream().filter(t -> t != winner).map(MatchTeam::names).toList())),
                MessageService.p("duration", TimeUtil.formatClock(match.durationMillis())),
                MessageService.c("inventories", inventories)};
        for (Player player : match.audience()) {
            messages.send(player, winner == null ? "match.result-draw" : "match.result", base);
        }
        if (!eloChanges.isEmpty()) {
            for (Map.Entry<UUID, Integer> entry : eloChanges.entrySet()) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player == null) {
                    continue;
                }
                int now = api.stats().eloOf(entry.getKey(), match.kit().id());
                messages.send(player, entry.getValue() >= 0 ? "match.elo-gained" : "match.elo-lost",
                        MessageService.p("change", Math.abs(entry.getValue())), MessageService.p("elo", now));
            }
        }
        if (match.type() == MatchType.QUEUE || match.type() == MatchType.DUEL) {
            if (match.participants().size() == 2) {
                Component rematch = messages.get("match.rematch-button").clickEvent(ClickEvent.runCommand("/rematch"))
                        .hoverEvent(messages.get("match.rematch-hover"));
                for (Player player : match.onlinePlayers()) {
                    messages.send(player, "match.rematch-offer", MessageService.c("button", rematch));
                }
            }
        }
    }

    private Component inventoryButtons(Match match) {
        Component line = Component.empty();
        boolean first = true;
        for (MatchParticipant participant : match.participants()) {
            InventorySnapshot latest = latestSnapshot(participant.uuid());
            if (latest == null) {
                continue;
            }
            if (!first) {
                line = line.append(Component.text(" "));
            }
            first = false;
            line = line.append(messages.get("match.inventory-button", MessageService.p("player", participant.name()))
                    .color(participant.team().color())
                    .clickEvent(ClickEvent.runCommand("/inventory " + latest.id()))
                    .hoverEvent(messages.get("match.inventory-hover", MessageService.p("player", participant.name()))));
        }
        return line;
    }

    private final Map<UUID, UUID> latestSnapshotByPlayer = new ConcurrentHashMap<>();

    private InventorySnapshot latestSnapshot(UUID player) {
        UUID id = latestSnapshotByPlayer.get(player);
        return id == null ? null : snapshots.get(id);
    }

    /**
     * Sends every remaining player to the lobby and releases the arena.
     *
     * @param match match
     */
    public void finish(Match match) {
        if (match.state() == MatchState.ENDED) {
            return;
        }
        match.task(null);
        match.state(MatchState.ENDED);
        matches.remove(match.id());
        for (MatchParticipant participant : match.participants()) {
            if (byPlayer.get(participant.uuid()) == match) {
                byPlayer.remove(participant.uuid());
            }
            Player player = Bukkit.getPlayer(participant.uuid());
            if (player != null && !participant.disconnected()) {
                sendToLobby(player);
            }
        }
        for (Player spectator : match.onlineSpectators()) {
            plugin.spectators().stop(spectator, false);
        }
        clearTeamColors(match);
        if (match.arena() != null) {
            api.arenas().release(match.arena());
        }
    }

    /**
     * Cancels every match without recording stats (plugin disable / server stop).
     */
    public void shutdown() {
        for (Match match : List.copyOf(matches.values())) {
            match.cancel();
            match.audience().forEach(p -> messages.send(p, "match.cancelled-shutdown"));
            finish(match);
        }
    }

    /**
     * Admin cancel of a match.
     *
     * @param match match
     */
    public void cancel(Match match) {
        match.cancel();
        match.audience().forEach(p -> messages.send(p, "match.cancelled-admin"));
        if (match.state() == MatchState.STARTING) {
            finish(match);
        } else {
            match.state(MatchState.FIGHTING);
            end(match, null);
        }
    }

    private void checkDurations() {
        for (Match match : matches.values()) {
            int max = match.kit().rules().maxDuration();
            if (max > 0 && match.state() == MatchState.FIGHTING && match.durationMillis() > max * 1000L) {
                broadcast(match, "match.time-limit");
                end(match, null);
            }
        }
    }

    // ------------------------------------------------------------------ visibility & colours

    private void makeDeadSpectator(Match match, Player player) {
        if (match.aliveTeams().size() <= 1) {
            return;
        }
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
        api.combat().reset(player);
        for (Player other : match.onlinePlayers()) {
            MatchParticipant p = match.participant(other.getUniqueId());
            if (p != null && p.alive() && other != player) {
                other.hidePlayer(plugin, player);
            }
        }
        messages.send(player, "match.eliminated");
    }

    private void showToParticipants(Match match, Player player) {
        player.setCollidable(true);
        for (Player other : match.onlinePlayers()) {
            if (other != player) {
                other.showPlayer(plugin, player);
            }
        }
    }

    private void applyTeamColors(Match match) {
        if (match.teams().size() > 2 && match.type() == MatchType.PARTY_FFA) {
            return;
        }
        for (Player viewer : match.onlinePlayers()) {
            Scoreboard board = api.sidebars().sidebar(viewer).board();
            for (MatchTeam team : match.teams()) {
                String name = TEAM_PREFIX + team.index();
                Team scoreboardTeam = board.getTeam(name);
                if (scoreboardTeam == null) {
                    scoreboardTeam = board.registerNewTeam(name);
                }
                scoreboardTeam.color(team.color());
                scoreboardTeam.setAllowFriendlyFire(true);
                for (MatchParticipant participant : team.participants()) {
                    scoreboardTeam.addEntry(participant.name());
                }
            }
        }
    }

    private void clearTeamColors(Match match) {
        for (MatchParticipant participant : match.participants()) {
            Player viewer = Bukkit.getPlayer(participant.uuid());
            if (viewer == null) {
                continue;
            }
            Scoreboard board = api.sidebars().sidebar(viewer).board();
            for (MatchTeam team : match.teams()) {
                Team scoreboardTeam = board.getTeam(TEAM_PREFIX + team.index());
                if (scoreboardTeam != null) {
                    scoreboardTeam.unregister();
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private void sendToLobby(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
        }
        player.setCollidable(true);
        api.bridges().get(LobbyBridge.class).ifPresentOrElse(lobby -> lobby.sendToLobby(player), () -> {
            api.combat().reset(player);
            api.states().set(player, PlayerState.LOBBY);
        });
    }

    /**
     * Sends a message to participants and spectators.
     *
     * @param match match
     * @param key message key
     * @param resolvers placeholders
     */
    public void broadcast(Match match, String key, TagResolver... resolvers) {
        for (Player player : match.audience()) {
            messages.send(player, key, resolvers);
        }
    }

    /**
     * Stores a snapshot and remembers it as the player's latest.
     *
     * @param snapshot snapshot
     */
    private void store(InventorySnapshot snapshot) {
        snapshots.put(snapshot);
        latestSnapshotByPlayer.put(snapshot.owner(), snapshot.id());
    }

    /**
     * @param uuid player
     * @return match the player is in
     */
    public Match matchOf(UUID uuid) {
        return byPlayer.get(uuid);
    }

    /** @return live matches */
    public Collection<Match> matches() {
        return matches.values();
    }

    /** @return snapshot cache */
    public SnapshotCache snapshots() {
        return snapshots;
    }

    /** @return players currently in matches */
    public int playerCount() {
        return byPlayer.size();
    }

    /**
     * @param kit kit id
     * @param ranked ranked flag
     * @return players fighting with the kit
     */
    public int playersFighting(String kit, boolean ranked) {
        int count = 0;
        for (Match match : matches.values()) {
            if (match.kit().id().equals(kit) && match.ranked() == ranked) {
                count += match.onlinePlayers().size();
            }
        }
        return count;
    }

    /**
     * @param ids players
     * @return whether any is in a match
     */
    public boolean anyInMatch(Set<UUID> ids) {
        return ids.stream().anyMatch(byPlayer::containsKey);
    }
}
