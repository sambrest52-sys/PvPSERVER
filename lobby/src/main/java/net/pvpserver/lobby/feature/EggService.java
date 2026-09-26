package net.pvpserver.lobby.feature;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.lobby.PvPLobby;
import net.pvpserver.lobby.config.LobbySettings;
import net.pvpserver.lobby.layout.LobbyLayout;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hidden eggs: right-click one to find it. Finding them all grants the configured permissions (lobby-only cosmetic
 * unlocks) for as long as the player is online, re-applied on every join.
 */
public final class EggService {

    private final PvPLobby plugin;
    private final LobbyData data;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    /**
     * @param plugin lobby plugin
     * @param data progress
     */
    public EggService(PvPLobby plugin, LobbyData data) {
        this.plugin = plugin;
        this.data = data;
    }

    private LobbySettings.Eggs settings() {
        return plugin.settings().eggs();
    }

    private int total() {
        return plugin.lobbyWorld().layout().eggs().size();
    }

    /**
     * @param player player
     * @return eggs of the current layout the player has found
     */
    public int found(Player player) {
        Set<String> found = data.eggs(player.getUniqueId());
        return (int) plugin.lobbyWorld().layout().eggs().stream().filter(egg -> found.contains(egg.id())).count();
    }

    /**
     * @param player player
     * @param egg clicked egg
     */
    public void click(Player player, LobbyLayout.Egg egg) {
        if (!settings().enabled()) {
            return;
        }
        int total = total();
        if (!data.findEgg(player.getUniqueId(), egg.id())) {
            plugin.messages().actionBar(player, "eggs.already-found", MessageService.p("found", found(player)), MessageService.p("total", total));
            return;
        }
        int found = found(player);
        player.playSound(Sound.sound(Key.key(settings().sound()), Sound.Source.MASTER, 1f, 1.6f));
        player.spawnParticle(Particle.TOTEM_OF_UNDYING, egg.at().x() + 0.5, egg.at().y() + 0.8, egg.at().z() + 0.5, 25, 0.3, 0.4, 0.3, 0.2);
        plugin.messages().send(player, "eggs.found", MessageService.p("found", found), MessageService.p("total", total));
        for (String command : settings().rewardCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), fill(command, player, egg.id(), found, total));
        }
        if (found >= total) {
            plugin.messages().title(player, "eggs.all-found-title", 10, 60, 20, MessageService.p("total", total));
            plugin.messages().send(player, "eggs.all-found", MessageService.p("total", total));
            for (String command : settings().completeCommands()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), fill(command, player, egg.id(), found, total));
            }
            grant(player);
        }
    }

    private static String fill(String command, Player player, String egg, int found, int total) {
        return command.replace("{player}", player.getName()).replace("{egg}", egg).replace("{found}", String.valueOf(found))
                .replace("{total}", String.valueOf(total));
    }

    /**
     * Grants the completion permissions to players who found every egg (join and reload).
     *
     * @param player player
     */
    public void grant(Player player) {
        int total = total();
        if (total == 0 || found(player) < total || settings().completePermissions().isEmpty()) {
            return;
        }
        PermissionAttachment attachment = attachments.computeIfAbsent(player.getUniqueId(), id -> player.addAttachment(plugin));
        for (String permission : settings().completePermissions()) {
            attachment.setPermission(permission, true);
        }
    }

    /**
     * @param player player leaving
     */
    public void revoke(Player player) {
        PermissionAttachment attachment = attachments.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                player.removeAttachment(attachment);
            } catch (IllegalArgumentException ignored) {
                // already gone
            }
        }
    }
}
