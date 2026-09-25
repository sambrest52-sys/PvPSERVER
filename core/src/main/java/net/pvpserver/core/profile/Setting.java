package net.pvpserver.core.profile;

/**
 * Boolean player settings shown in the settings menu. Keys are stable identifiers used in storage and menus.yml.
 */
public enum Setting {
    SCOREBOARD("scoreboard", true),
    DUEL_REQUESTS("duel-requests", true),
    PRIVATE_MESSAGES("private-messages", true),
    PARTY_INVITES("party-invites", true),
    ALLOW_SPECTATORS("allow-spectators", true),
    LOBBY_PLAYERS("lobby-players", true),
    SHOW_COSMETICS("show-cosmetics", true),
    JOIN_MESSAGES("join-messages", true),
    DOUBLE_JUMP("double-jump", true);

    private final String key;
    private final boolean defaultValue;

    Setting(String key, boolean defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    /** @return storage / config key */
    public String key() {
        return key;
    }

    /** @return value for new players */
    public boolean defaultValue() {
        return defaultValue;
    }

    /**
     * @param key storage key
     * @return matching setting or null
     */
    public static Setting byKey(String key) {
        for (Setting setting : values()) {
            if (setting.key.equalsIgnoreCase(key)) {
                return setting;
            }
        }
        return null;
    }
}
