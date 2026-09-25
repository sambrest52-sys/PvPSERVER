package net.pvpserver.duels.match;

import java.util.UUID;

/**
 * Per-player match statistics (hits, combos, potions).
 */
public final class MatchParticipant {

    private final UUID uuid;
    private final String name;
    private final MatchTeam team;
    private boolean alive = true;
    private boolean disconnected;
    private int hits;
    private int currentCombo;
    private int longestCombo;
    private int potionsThrown;
    private int potionsMissed;
    private int kills;

    /**
     * @param uuid player id
     * @param name name
     * @param team team
     */
    public MatchParticipant(UUID uuid, String name, MatchTeam team) {
        this.uuid = uuid;
        this.name = name;
        this.team = team;
    }

    /** Registers a landed hit. */
    public void hit() {
        hits++;
        currentCombo++;
        longestCombo = Math.max(longestCombo, currentCombo);
    }

    /** Resets the combo after being hit. */
    public void breakCombo() {
        currentCombo = 0;
    }

    /**
     * @param missed whether the potion barely affected the thrower
     */
    public void potion(boolean missed) {
        potionsThrown++;
        if (missed) {
            potionsMissed++;
        }
    }

    /** Adds a kill. */
    public void kill() {
        kills++;
    }

    /** Resets per-round state (alive). */
    public void revive() {
        alive = !disconnected;
        currentCombo = 0;
    }

    /** @return player id */
    public UUID uuid() {
        return uuid;
    }

    /** @return name */
    public String name() {
        return name;
    }

    /** @return team */
    public MatchTeam team() {
        return team;
    }

    /** @return whether alive in the current round */
    public boolean alive() {
        return alive;
    }

    /** @param alive alive flag */
    public void alive(boolean alive) {
        this.alive = alive;
    }

    /** @return whether the player left the match */
    public boolean disconnected() {
        return disconnected;
    }

    /** Marks the player as gone for the rest of the match. */
    public void disconnect() {
        disconnected = true;
        alive = false;
    }

    /** @return hits */
    public int hits() {
        return hits;
    }

    /** @return current combo */
    public int currentCombo() {
        return currentCombo;
    }

    /** @return longest combo */
    public int longestCombo() {
        return longestCombo;
    }

    /** @return potions thrown */
    public int potionsThrown() {
        return potionsThrown;
    }

    /** @return potions missed */
    public int potionsMissed() {
        return potionsMissed;
    }

    /** @return kills */
    public int kills() {
        return kills;
    }

    /** @return potion accuracy percentage */
    public String potionAccuracy() {
        return potionsThrown == 0 ? "-" : Math.round((potionsThrown - potionsMissed) * 100.0 / potionsThrown) + "%";
    }
}
