package net.pvpserver.lobby;

import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.Setting;
import net.pvpserver.core.state.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Applies the "lobby players" setting: players who disable it do not see other lobby players (staff and party
 * members stay visible).
 */
public final class LobbyVisibility {

    private final Plugin plugin;
    private final PracticeApi api;

    /**
     * @param plugin lobby plugin
     * @param api practice api
     */
    public LobbyVisibility(Plugin plugin, PracticeApi api) {
        this.plugin = plugin;
        this.api = api;
    }

    /**
     * Recomputes visibility between the player and everybody else in the lobby world.
     *
     * @param player player that entered the lobby or changed the setting
     */
    public void update(Player player) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == player) {
                continue;
            }
            apply(player, other);
            apply(other, player);
        }
    }

    private void apply(Player viewer, Player target) {
        // Bukkit tracks hides per plugin, so this only manages the lobby's own hides; vanish (core) and
        // spectator hiding (duels) stay in force independently.
        PlayerProfile profile = api.profiles().get(viewer);
        boolean wantsPlayers = profile == null || profile.settings().is(Setting.LOBBY_PLAYERS);
        boolean important = target.hasPermission("pvp.staff") || sameParty(viewer, target);
        boolean hide = isLobbySide(viewer) && isLobbySide(target) && !wantsPlayers && !important;
        if (hide) {
            viewer.hidePlayer(plugin, target);
        } else {
            viewer.showPlayer(plugin, target);
        }
    }

    private boolean isLobbySide(Player player) {
        PlayerState state = api.states().get(player);
        return state == PlayerState.LOBBY || state == PlayerState.QUEUE || state == PlayerState.EDITING;
    }

    private boolean sameParty(Player a, Player b) {
        return api.parties().partyOf(a).map(party -> party.contains(b.getUniqueId())).orElse(false);
    }
}
