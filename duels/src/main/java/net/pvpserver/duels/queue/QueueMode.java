package net.pvpserver.duels.queue;

/**
 * Team size of a queue.
 */
public enum QueueMode {
    ONE_V_ONE(1, "1v1"),
    TWO_V_TWO(2, "2v2");

    private final int teamSize;
    private final String label;

    QueueMode(int teamSize, String label) {
        this.teamSize = teamSize;
        this.label = label;
    }

    /** @return players per team */
    public int teamSize() {
        return teamSize;
    }

    /** @return display label, e.g. "1v1" */
    public String label() {
        return label;
    }
}
