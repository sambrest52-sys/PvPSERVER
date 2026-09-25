package net.pvpserver.core.knockback;

import org.bukkit.util.Vector;

/**
 * Knockback tuning values. The formula mirrors 1.8's {@code EntityLiving#a}: the victim's current velocity is
 * divided by {@code friction}, then {@code horizontal}/{@code vertical} are added along the attacker→victim
 * direction; sprint hits and the knockback enchantment add {@code extraHorizontal}/{@code extraVertical} per level
 * along the attacker's facing. Vertical velocity is capped at {@code verticalLimit}.
 *
 * @param name profile id
 * @param horizontal base horizontal knockback
 * @param vertical base vertical knockback
 * @param friction divisor applied to the victim's existing velocity (1 = keep, 2 = vanilla 1.8)
 * @param extraHorizontal additional horizontal knockback per sprint/enchant level
 * @param extraVertical additional vertical knockback per sprint/enchant level
 * @param verticalLimit maximum resulting vertical velocity
 * @param airHorizontalMultiplier multiplier on horizontal knockback while the victim is airborne
 */
public record KnockbackProfile(String name, double horizontal, double vertical, double friction, double extraHorizontal,
                               double extraVertical, double verticalLimit, double airHorizontalMultiplier) {

    /** Editable field names used by {@code /kb set}. */
    public static final String[] FIELDS = {"horizontal", "vertical", "friction", "extra-horizontal", "extra-vertical",
            "vertical-limit", "air-horizontal-multiplier"};

    /**
     * Computes the base knockback velocity.
     *
     * @param current victim's current velocity
     * @param dx attacker→victim x direction (unnormalised)
     * @param dz attacker→victim z direction (unnormalised)
     * @param onGround whether the victim is on the ground
     * @return new absolute velocity
     */
    public Vector base(Vector current, double dx, double dz, boolean onGround) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0E-4) {
            dx = (Math.random() - Math.random()) * 0.01;
            dz = (Math.random() - Math.random()) * 0.01;
            distance = Math.sqrt(dx * dx + dz * dz);
        }
        double friction = this.friction <= 0 ? 1 : this.friction;
        double h = horizontal * (onGround ? 1.0 : airHorizontalMultiplier);
        double x = current.getX() / friction + dx / distance * h;
        double y = current.getY() / friction + vertical;
        double z = current.getZ() / friction + dz / distance * h;
        if (y > verticalLimit) {
            y = verticalLimit;
        }
        return new Vector(x, y, z);
    }

    /**
     * Computes the extra (sprint / knockback enchant) component to add on top of the base velocity.
     *
     * @param yawDegrees attacker yaw
     * @param level sprint bonus + knockback enchant level
     * @return velocity delta
     */
    public Vector extra(float yawDegrees, double level) {
        double yaw = Math.toRadians(yawDegrees);
        return new Vector(-Math.sin(yaw) * extraHorizontal * level, extraVertical * level, Math.cos(yaw) * extraHorizontal * level);
    }

    /**
     * @param field field name (see {@link #FIELDS})
     * @param value new value
     * @return updated copy, or null for unknown fields
     */
    public KnockbackProfile with(String field, double value) {
        return switch (field.toLowerCase(java.util.Locale.ROOT)) {
            case "horizontal" -> new KnockbackProfile(name, value, vertical, friction, extraHorizontal, extraVertical, verticalLimit, airHorizontalMultiplier);
            case "vertical" -> new KnockbackProfile(name, horizontal, value, friction, extraHorizontal, extraVertical, verticalLimit, airHorizontalMultiplier);
            case "friction" -> new KnockbackProfile(name, horizontal, vertical, value, extraHorizontal, extraVertical, verticalLimit, airHorizontalMultiplier);
            case "extra-horizontal" -> new KnockbackProfile(name, horizontal, vertical, friction, value, extraVertical, verticalLimit, airHorizontalMultiplier);
            case "extra-vertical" -> new KnockbackProfile(name, horizontal, vertical, friction, extraHorizontal, value, verticalLimit, airHorizontalMultiplier);
            case "vertical-limit" -> new KnockbackProfile(name, horizontal, vertical, friction, extraHorizontal, extraVertical, value, airHorizontalMultiplier);
            case "air-horizontal-multiplier" -> new KnockbackProfile(name, horizontal, vertical, friction, extraHorizontal, extraVertical, verticalLimit, value);
            default -> null;
        };
    }

    /**
     * @param newName new id
     * @return renamed copy
     */
    public KnockbackProfile rename(String newName) {
        return new KnockbackProfile(newName, horizontal, vertical, friction, extraHorizontal, extraVertical, verticalLimit, airHorizontalMultiplier);
    }
}
