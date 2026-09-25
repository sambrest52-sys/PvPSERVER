package net.pvpserver.core.moderation;

/**
 * Kinds of punishment. Bans and mutes may be temporary; kicks and warnings are instantaneous records.
 */
public enum PunishmentType {
    BAN(true),
    MUTE(true),
    KICK(false),
    WARN(false);

    private final boolean lasting;

    PunishmentType(boolean lasting) {
        this.lasting = lasting;
    }

    /** @return whether the punishment stays active over time */
    public boolean lasting() {
        return lasting;
    }
}
