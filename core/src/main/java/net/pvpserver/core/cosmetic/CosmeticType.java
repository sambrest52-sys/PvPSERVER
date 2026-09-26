package net.pvpserver.core.cosmetic;

/**
 * Cosmetic categories.
 */
public enum CosmeticType {
    KILL_EFFECT("kill-effects"),
    DEATH_ANIMATION("death-animations"),
    JOIN_MESSAGE("join-messages"),
    /** Lobby-only particle trail while walking. */
    TRAIL("trails"),
    /** Lobby-only effect played when the player joins. */
    JOIN_EFFECT("join-effects");

    private final String section;

    CosmeticType(String section) {
        this.section = section;
    }

    /** @return cosmetics.yml section */
    public String section() {
        return section;
    }
}
