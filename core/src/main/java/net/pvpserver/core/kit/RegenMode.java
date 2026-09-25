package net.pvpserver.core.kit;

/**
 * Natural health regeneration behaviour of a kit.
 */
public enum RegenMode {
    /** Modern saturation based regeneration. */
    VANILLA,
    /** 1.8 style: 1 HP every 4 seconds while food is at least 18. */
    LEGACY,
    /** No natural regeneration (UHC style). */
    NONE
}
