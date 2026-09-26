package net.pvpserver.lobby;

import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.api.bridge.LobbyBridge;
import net.pvpserver.core.kit.KitService;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.profile.Setting;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.lobby.config.LobbySettings;
import net.pvpserver.lobby.layout.Point;
import net.pvpserver.lobby.world.LobbyWorld;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Lobby spawn handling and the canonical "reset and send to lobby" routine used by every gamemode. The spawn comes
 * from the lobby world's layout.yml.
 */
public final class LobbyService implements LobbyBridge {

    private final PracticeApi api;
    private final PvPLobby plugin;
    private final LobbyHotbar hotbar;
    private final LobbyVisibility visibility;
    private final LobbyWorld lobbyWorld;

    /**
     * @param api practice api
     * @param plugin lobby plugin (settings)
     * @param hotbar hotbar layouts
     * @param visibility visibility rules
     * @param lobbyWorld lobby world
     */
    public LobbyService(PracticeApi api, PvPLobby plugin, LobbyHotbar hotbar, LobbyVisibility visibility, LobbyWorld lobbyWorld) {
        this.api = api;
        this.plugin = plugin;
        this.hotbar = hotbar;
        this.visibility = visibility;
        this.lobbyWorld = lobbyWorld;
    }

    @Override
    public Location spawn() {
        return lobbyWorld.spawn();
    }

    /**
     * Stores a new spawn in layout.yml.
     *
     * @param location spawn (must be in the lobby world)
     */
    public void setSpawn(Location location) {
        lobbyWorld.layouts().save(lobbyWorld.layout().withSpawn(Point.from(location)));
        lobbyWorld.applyWorldSettings();
    }

    /**
     * @return lobby world
     */
    public World world() {
        return lobbyWorld.world();
    }

    @Override
    public void sendToLobby(Player player) {
        plugin.features().parkour().cancel(player, false);
        reset(player);
        api.states().set(player, PlayerState.LOBBY);
        player.teleport(spawn());
        applyPreferences(player);
        hotbar.give(player);
        visibility.update(player);
        api.sidebars().refresh(player);
    }

    @Override
    public void refreshHotbar(Player player) {
        PlayerState state = api.states().get(player);
        if (state == PlayerState.LOBBY || state == PlayerState.QUEUE) {
            hotbar.give(player);
        }
    }

    /**
     * Clears inventory, effects, combat settings and restores health/food; sets adventure mode and flight.
     *
     * @param player player
     */
    public void reset(Player player) {
        api.combat().reset(player);
        api.sidebars().sidebar(player).healthBelowName(false);
        player.getInventory().clear();
        player.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[4]);
        player.setItemOnCursor(null);
        player.closeInventory();
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setGameMode(GameMode.ADVENTURE);
        KitService.heal(player);
        player.setLevel(0);
        player.setExp(0f);
        player.setGlowing(false);
        player.setInvisible(false);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setFallDistance(0);
        applyFlight(player);
        player.setFlying(false);
    }

    /**
     * Allows flight for /fly users and double jumpers (not during a parkour run).
     *
     * @param player player
     */
    public void applyFlight(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (plugin.features() != null && plugin.features().parkour().running(player)) {
            player.setAllowFlight(false);
            return;
        }
        boolean flying = plugin.flying(player) && player.hasPermission("pvp.lobby.fly");
        player.setAllowFlight(flying || player.hasPermission("pvp.lobby.fly") || allowsDoubleJump(player));
    }

    /**
     * Applies client-side time preference.
     *
     * @param player player
     */
    public void applyPreferences(Player player) {
        PlayerProfile profile = api.profiles().get(player);
        if (profile != null) {
            player.setPlayerTime(profile.settings().timeOfDay().ticks(), false);
        }
    }

    /**
     * @param player player
     * @return whether double jump applies (config + setting + permission)
     */
    public boolean allowsDoubleJump(Player player) {
        LobbySettings.DoubleJump settings = plugin.settings().doubleJump();
        if (!settings.enabled()) {
            return false;
        }
        PlayerProfile profile = api.profiles().get(player);
        String permission = settings.permission();
        boolean permitted = permission == null || permission.isEmpty() || player.hasPermission(permission);
        return permitted && (profile == null || profile.settings().is(Setting.DOUBLE_JUMP));
    }
}
