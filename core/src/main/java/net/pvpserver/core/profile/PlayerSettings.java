package net.pvpserver.core.profile;

import java.util.EnumMap;
import java.util.Map;

/**
 * Mutable per-player preferences. Serialised to JSON in the players table.
 */
public final class PlayerSettings {

    private final EnumMap<Setting, Boolean> toggles = new EnumMap<>(Setting.class);
    private TimeOfDay timeOfDay = TimeOfDay.DAY;

    /**
     * @param setting setting
     * @return current value (default when never set)
     */
    public boolean is(Setting setting) {
        return toggles.getOrDefault(setting, setting.defaultValue());
    }

    /**
     * @param setting setting
     * @param value new value
     */
    public void set(Setting setting, boolean value) {
        toggles.put(setting, value);
    }

    /**
     * Flips a toggle.
     *
     * @param setting setting
     * @return new value
     */
    public boolean toggle(Setting setting) {
        boolean value = !is(setting);
        set(setting, value);
        return value;
    }

    /** @return time preference */
    public TimeOfDay timeOfDay() {
        return timeOfDay;
    }

    /** @param timeOfDay new time preference */
    public void timeOfDay(TimeOfDay timeOfDay) {
        this.timeOfDay = timeOfDay == null ? TimeOfDay.DAY : timeOfDay;
    }

    /** @return snapshot of explicitly set toggles */
    public Map<Setting, Boolean> toggles() {
        return Map.copyOf(toggles);
    }
}
