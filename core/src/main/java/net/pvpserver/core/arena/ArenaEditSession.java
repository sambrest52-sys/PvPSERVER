package net.pvpserver.core.arena;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.HashSet;
import java.util.Set;

/**
 * Mutable, absolute-coordinate editing state of one admin. Converted to a relative {@link Arena} on save.
 */
public final class ArenaEditSession {

    final String name;
    Location pos1;
    Location pos2;
    Location spawnA;
    Location spawnB;
    Location spectator;
    Location goalA;
    Location goalB;
    Integer buildLimitY;
    Integer voidY;
    double goalRadius = 1.6;
    String displayName;
    Material icon = Material.GRASS_BLOCK;
    Set<String> tags = new HashSet<>(Set.of("standard"));
    boolean enabled = true;

    ArenaEditSession(String name) {
        this.name = name;
        this.displayName = "<white>" + name;
    }

    /** @return arena name */
    public String name() {
        return name;
    }
}
