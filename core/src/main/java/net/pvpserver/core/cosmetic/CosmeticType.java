package net.pvpserver.core.cosmetic;

/**
 * Cosmetic categories.
 */
public enum CosmeticType {
    KILL_EFFECT("kill-effects"),
    DEATH_ANIMATION("death-animations"),
    JOIN_MESSAGE("join-messages");

    private final String section;

    CosmeticType(String section) {
        this.section = section;
    }

    /** @return cosmetics.yml section */
    public String section() {
        return section;
    }
}
