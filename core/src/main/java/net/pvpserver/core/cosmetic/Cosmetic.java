package net.pvpserver.core.cosmetic;

import org.bukkit.Material;

/**
 * A selectable cosmetic.
 *
 * @param type category
 * @param id identifier (also selects the built-in effect implementation for kill effects / death animations)
 * @param displayName MiniMessage name
 * @param icon menu icon
 * @param permission required permission (null = everyone)
 * @param message join message template (join messages only)
 */
public record Cosmetic(CosmeticType type, String id, String displayName, Material icon, String permission, String message) {
}
