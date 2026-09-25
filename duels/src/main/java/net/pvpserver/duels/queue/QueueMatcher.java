package net.pvpserver.duels.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pure matchmaking. Oldest entries are served first. Ranked matching only pairs sides whose rating difference fits
 * inside <em>both</em> sides' current {@link EloRange}; among valid opponents the closest rating wins. Unranked
 * matching pairs in join order.
 * <p>
 * For team queues, entries that already fill a team (parties) form a team on their own and solo entries are
 * combined into teams before teams are matched against each other.
 */
public final class QueueMatcher {

    private QueueMatcher() {
    }

    /**
     * A matched pair of teams, each made of one or more queue entries.
     *
     * @param teamA first team entries
     * @param teamB second team entries
     */
    public record Pairing(List<QueueEntry> teamA, List<QueueEntry> teamB) {

        /** @return all entries in the pairing */
        public List<QueueEntry> entries() {
            List<QueueEntry> all = new ArrayList<>(teamA);
            all.addAll(teamB);
            return all;
        }
    }

    private record Team(List<QueueEntry> entries, int elo, long joinedAt, int range) {
    }

    /**
     * Finds as many pairings as possible.
     *
     * @param entries entries of a single queue
     * @param teamSize players per team
     * @param ranked whether ratings must be close
     * @param range search window
     * @param now current time
     * @return pairings (entries never appear twice)
     */
    public static List<Pairing> match(List<QueueEntry> entries, int teamSize, boolean ranked, EloRange range, long now) {
        List<QueueEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(QueueEntry::joinedAt));
        List<Team> teams = formTeams(sorted, teamSize, ranked, range, now);
        teams.sort(Comparator.comparingLong(Team::joinedAt));

        List<Pairing> pairings = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < teams.size(); i++) {
            if (used.contains(i)) {
                continue;
            }
            Team a = teams.get(i);
            int best = -1;
            int bestDiff = Integer.MAX_VALUE;
            for (int j = i + 1; j < teams.size(); j++) {
                if (used.contains(j)) {
                    continue;
                }
                Team b = teams.get(j);
                if (!ranked) {
                    best = j;
                    break;
                }
                int diff = Math.abs(a.elo - b.elo);
                if (diff <= a.range && diff <= b.range && diff < bestDiff) {
                    best = j;
                    bestDiff = diff;
                }
            }
            if (best >= 0) {
                used.add(i);
                used.add(best);
                pairings.add(new Pairing(a.entries, teams.get(best).entries));
            }
        }
        return pairings;
    }

    private static List<Team> formTeams(List<QueueEntry> sorted, int teamSize, boolean ranked, EloRange range, long now) {
        List<Team> teams = new ArrayList<>();
        List<QueueEntry> solos = new ArrayList<>();
        for (QueueEntry entry : sorted) {
            if (entry.size() == teamSize) {
                teams.add(new Team(List.of(entry), entry.elo(), entry.joinedAt(), range.range(entry.waited(now))));
            } else if (entry.size() < teamSize) {
                solos.add(entry);
            }
            // Entries larger than the team size can never be matched in this queue and are ignored.
        }
        if (teamSize == 1) {
            return teams;
        }
        // Greedy fill: oldest solo first, add the closest-rated compatible solos until the team is full.
        Set<UUID> taken = new HashSet<>();
        for (QueueEntry anchor : solos) {
            if (taken.contains(anchor.id())) {
                continue;
            }
            List<QueueEntry> members = new ArrayList<>();
            members.add(anchor);
            int size = anchor.size();
            int anchorRange = range.range(anchor.waited(now));
            while (size < teamSize) {
                QueueEntry pick = null;
                int pickDiff = Integer.MAX_VALUE;
                for (QueueEntry candidate : solos) {
                    if (candidate == anchor || taken.contains(candidate.id()) || members.contains(candidate)
                            || size + candidate.size() > teamSize) {
                        continue;
                    }
                    int diff = Math.abs(candidate.elo() - anchor.elo());
                    if (ranked && (diff > anchorRange || diff > range.range(candidate.waited(now)))) {
                        continue;
                    }
                    if (!ranked) {
                        pick = candidate;
                        break;
                    }
                    if (diff < pickDiff) {
                        pick = candidate;
                        pickDiff = diff;
                    }
                }
                if (pick == null) {
                    break;
                }
                members.add(pick);
                size += pick.size();
            }
            if (size == teamSize) {
                members.forEach(m -> taken.add(m.id()));
                int total = 0;
                int count = 0;
                long oldest = Long.MAX_VALUE;
                int minRange = Integer.MAX_VALUE;
                for (QueueEntry member : members) {
                    total += member.elo() * member.size();
                    count += member.size();
                    oldest = Math.min(oldest, member.joinedAt());
                    minRange = Math.min(minRange, range.range(member.waited(now)));
                }
                teams.add(new Team(List.copyOf(members), total / count, oldest, minRange));
            }
        }
        return teams;
    }
}
