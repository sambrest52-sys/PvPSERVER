package net.pvpserver.lobby.feature;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.api.bridge.MatchBridge;
import net.pvpserver.core.api.bridge.QueueBridge;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.state.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Counts shown on NPC and portal holograms, taken on the main thread in one pass (a few map lookups, no player loops
 * beyond the online list) and then rendered off the main thread.
 *
 * @param online players online
 * @param lobby players in the lobby states
 * @param queued players queued
 * @param fighting players in matches
 * @param ffa players in FFA
 * @param spectating spectators
 * @param rankedQueued ranked queue size
 * @param unrankedQueued unranked queue size
 * @param rankedFighting players in ranked matches
 * @param unrankedFighting players in unranked matches
 * @param parkourRunners players running the parkour
 */
public record LiveStats(int online, int lobby, int queued, int fighting, int ffa, int spectating, int rankedQueued, int unrankedQueued,
                        int rankedFighting, int unrankedFighting, int parkourRunners) {

    /** @return all zeros */
    public static LiveStats empty() {
        return new LiveStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * @param api practice api
     * @param parkourRunners players running the parkour
     * @return snapshot
     */
    public static LiveStats capture(PracticeApi api, int parkourRunners) {
        int lobby = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (api.states().is(player, PlayerState.LOBBY, PlayerState.QUEUE, PlayerState.EDITING)) {
                lobby++;
            }
        }
        int rankedQueued = 0;
        int unrankedQueued = 0;
        QueueBridge queues = api.bridges().get(QueueBridge.class).orElse(null);
        if (queues != null) {
            for (Kit kit : api.kits().all()) {
                if (kit.ranked()) {
                    rankedQueued += queues.queuedPlayers(kit.id(), true);
                }
                if (kit.unranked()) {
                    unrankedQueued += queues.queuedPlayers(kit.id(), false);
                }
            }
        }
        int rankedFighting = 0;
        int unrankedFighting = 0;
        MatchBridge matches = api.bridges().get(MatchBridge.class).orElse(null);
        if (matches != null) {
            for (MatchBridge.MatchInfo match : matches.activeMatches()) {
                if (match.ranked()) {
                    rankedFighting += match.participants().size();
                } else {
                    unrankedFighting += match.participants().size();
                }
            }
        }
        return new LiveStats(api.counts().online(), lobby, api.counts().queued(), api.counts().fighting(), api.counts().ffa(),
                api.counts().spectating(), rankedQueued, unrankedQueued, rankedFighting, unrankedFighting, parkourRunners);
    }

    /** @return MiniMessage placeholders */
    public TagResolver resolver() {
        return TagResolver.resolver(
                MessageService.p("online", online), MessageService.p("lobby", lobby), MessageService.p("queued", queued),
                MessageService.p("fighting", fighting), MessageService.p("ffa", ffa), MessageService.p("spectating", spectating),
                MessageService.p("ranked_queued", rankedQueued), MessageService.p("unranked_queued", unrankedQueued),
                MessageService.p("ranked_fighting", rankedFighting), MessageService.p("unranked_fighting", unrankedFighting),
                MessageService.p("parkour_runners", parkourRunners));
    }
}
