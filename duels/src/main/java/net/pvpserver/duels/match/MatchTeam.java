package net.pvpserver.duels.match;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * A side in a match. Party FFA matches have one team per player.
 */
public final class MatchTeam {

    private static final NamedTextColor[] COLORS = {NamedTextColor.RED, NamedTextColor.BLUE, NamedTextColor.GREEN,
            NamedTextColor.YELLOW, NamedTextColor.LIGHT_PURPLE, NamedTextColor.AQUA, NamedTextColor.GOLD, NamedTextColor.WHITE};
    private static final String[] NAMES = {"Red", "Blue", "Green", "Yellow", "Pink", "Aqua", "Gold", "White"};

    private final int index;
    private final List<MatchParticipant> participants = new ArrayList<>();
    private int roundsWon;
    private int points;

    /**
     * @param index team index (0 = spawn A, 1 = spawn B ...)
     */
    public MatchTeam(int index) {
        this.index = index;
    }

    /** @return index */
    public int index() {
        return index;
    }

    /** @return colour */
    public NamedTextColor color() {
        return COLORS[index % COLORS.length];
    }

    /** @return name, e.g. "Red" */
    public String name() {
        return NAMES[index % NAMES.length];
    }

    /** @return participants */
    public List<MatchParticipant> participants() {
        return participants;
    }

    /** @return whether any member is alive this round */
    public boolean anyAlive() {
        return participants.stream().anyMatch(MatchParticipant::alive);
    }

    /** @return whether every member disconnected */
    public boolean allDisconnected() {
        return participants.stream().allMatch(MatchParticipant::disconnected);
    }

    /** @return alive member count */
    public long aliveCount() {
        return participants.stream().filter(MatchParticipant::alive).count();
    }

    /** @return rounds won */
    public int roundsWon() {
        return roundsWon;
    }

    /** Adds a won round. */
    public void winRound() {
        roundsWon++;
    }

    /** @return points (boxing hits / bridge goals) in the current round or match */
    public int points() {
        return points;
    }

    /** Adds a point. */
    public void addPoint() {
        points++;
    }

    /** Resets points (new round). */
    public void resetPoints() {
        points = 0;
    }

    /** @return comma separated names */
    public String names() {
        return String.join(", ", participants.stream().map(MatchParticipant::name).toList());
    }
}
