package net.pvpserver.lobby;

import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.api.bridge.LobbyBridge;
import net.pvpserver.core.config.ConfigFile;
import net.pvpserver.core.kit.KitService;
import net.pvpserver.core.profile.PlayerProfile;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.core.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Lobby spawn handling and the canonical "reset and send to lobby" routine used by every gamemode.
 */
public final class LobbyService implements LobbyBridge {

    private final PracticeApi api;
    private final ConfigFile config;
    private final LobbyHotbar hotbar;
    private final LobbyVisibility visibility;
    private Location spawn;

    /**
     * @param api practice api
     * @param config lobby config.yml
     * @param hotbar hotbar layouts
     * @param visibility visibility rules
     */
    public LobbyService(PracticeApi api, ConfigFile config, LobbyHotbar hotbar, LobbyVisibility visibility) {
        this.api = api;
        this.config = config;
        this.hotbar = hotbar;
        this.visibility = visibility;
        reload();
    }

    /** Re-reads the spawn from config. */
    public void reload() {
        Location configured = LocationUtil.deserialize(config.get().getString("spawn", ""));
        if (configured == null || configured.getWorld() == null) {
            World world = Bukkit.getWorlds().get(0);
            configured = world.getSpawnLocation().add(0.5, 0, 0.5);
        }
        spawn = configured;
        if (config.get().getBoolean("tune-world", true)) {
            api.worlds().tune(spawn.getWorld(), false);
        }
    }

    @Override
    public Location spawn() {
        return spawn.clone();
    }

    /**
     * Stores a new spawn.
     *
     * @param location spawn
     */
    public void setSpawn(Location location) {
        spawn = location.clone();
        config.get().set("spawn", LocationUtil.serialize(location));
        config.save();
    }

    /**
     * @return lobby world
     */
    public World world() {
        return spawn.getWorld();
    }

    @Override
    public void sendToLobby(Player player) {
        reset(player);
        api.states().set(player, PlayerState.LOBBY);
        player.teleport(spawn);
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
        boolean canFly = player.hasPermission("pvp.lobby.fly") || allowsDoubleJump(player);
        player.setAllowFlight(canFly);
        player.setFlying(false);
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
        if (!config.get().getBoolean("double-jump.enabled", true)) {
            return false;
        }
        PlayerProfile profile = api.profiles().get(player);
        String permission = config.get().getString("double-jump.permission", "");
        boolean permitted = permission == null || permission.isEmpty() || player.hasPermission(permission);
        return permitted && (profile == null || profile.settings().is(net.pvpserver.core.profile.Setting.DOUBLE_JUMP));
    }

    /** @return lobby config */
    public ConfigFile config() {
        return config;
    }
}
