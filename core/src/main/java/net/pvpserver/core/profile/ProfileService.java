package net.pvpserver.core.profile;

import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.storage.repository.KitLayoutRepository;
import net.pvpserver.core.storage.repository.ProfileRepository;
import net.pvpserver.core.storage.repository.StatsRepository;
import net.pvpserver.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads profiles during {@link AsyncPlayerPreLoginEvent} (off the main thread), caches them while the player is
 * online, autosaves dirty profiles and saves on quit.
 */
public final class ProfileService implements Listener {

    private final JavaPlugin plugin;
    private final ProfileRepository profileRepository;
    private final StatsRepository statsRepository;
    private final KitLayoutRepository layoutRepository;
    private final MessageService messages;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingSince = new ConcurrentHashMap<>();
    private BukkitTask autosave;

    /**
     * @param plugin core plugin
     * @param profileRepository profile storage
     * @param statsRepository stats storage
     * @param layoutRepository layout storage
     * @param messages messages
     */
    public ProfileService(JavaPlugin plugin, ProfileRepository profileRepository, StatsRepository statsRepository,
                          KitLayoutRepository layoutRepository, MessageService messages) {
        this.plugin = plugin;
        this.profileRepository = profileRepository;
        this.statsRepository = statsRepository;
        this.layoutRepository = layoutRepository;
        this.messages = messages;
    }

    /**
     * Starts the autosave timer.
     *
     * @param intervalSeconds seconds between autosaves
     */
    public void start(int intervalSeconds) {
        long ticks = Math.max(30, intervalSeconds) * 20L;
        autosave = Tasks.timer(this::autosave, ticks, ticks);
        // Profiles for players already online (plugin reload via /reload is unsupported but survive gracefully).
        for (Player player : Bukkit.getOnlinePlayers()) {
            Tasks.async(() -> {
                PlayerProfile profile = loadBlocking(player.getUniqueId(), player.getName());
                profiles.put(player.getUniqueId(), profile);
            });
        }
    }

    private PlayerProfile loadBlocking(UUID uuid, String name) {
        PlayerProfile profile = profileRepository.loadOrCreateBlocking(uuid, name);
        profile.allStats().putAll(statsRepository.loadBlocking(uuid));
        profile.kitLayouts().putAll(layoutRepository.loadBlocking(uuid));
        return profile;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        try {
            PlayerProfile profile = loadBlocking(uuid, event.getName());
            profile.name(event.getName());
            profile.lastJoin(System.currentTimeMillis());
            profiles.put(uuid, profile);
            pendingSince.put(uuid, System.currentTimeMillis());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load profile of " + event.getName(), e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, messages.get("profile.load-failed"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        pendingSince.remove(player.getUniqueId());
        if (!profiles.containsKey(player.getUniqueId())) {
            player.kick(messages.get("profile.load-failed"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerProfile profile = profiles.remove(event.getPlayer().getUniqueId());
        if (profile != null) {
            save(profile);
        }
    }

    private void autosave() {
        long now = System.currentTimeMillis();
        pendingSince.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > 60_000 && Bukkit.getPlayer(entry.getKey()) == null) {
                profiles.remove(entry.getKey());
                return true;
            }
            return false;
        });
        for (PlayerProfile profile : profiles.values()) {
            if (profile.consumeDirty()) {
                save(profile);
            }
        }
    }

    /**
     * Saves profile and stats asynchronously.
     *
     * @param profile profile
     * @return completion
     */
    public CompletableFuture<Void> save(PlayerProfile profile) {
        return CompletableFuture.allOf(profileRepository.save(profile),
                statsRepository.save(profile.uuid(), profile.allStats().values()));
    }

    /** Saves every cached profile synchronously; used on shutdown. */
    public void saveAllBlocking() {
        if (autosave != null) {
            autosave.cancel();
        }
        for (PlayerProfile profile : profiles.values()) {
            try {
                profileRepository.saveBlocking(profile);
                statsRepository.saveBlocking(profile.uuid(), profile.allStats().values());
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save " + profile.name(), e);
            }
        }
    }

    /**
     * @param player online player
     * @return cached profile (null only during the join window if loading failed)
     */
    public PlayerProfile get(Player player) {
        return profiles.get(player.getUniqueId());
    }

    /**
     * @param uuid player id
     * @return cached profile of an online player, or null
     */
    public PlayerProfile get(UUID uuid) {
        return profiles.get(uuid);
    }

    /**
     * Resolves a player name to a UUID: online players first, then the players table, then the server user cache.
     *
     * @param name name
     * @return uuid if known
     */
    public CompletableFuture<Optional<UUID>> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(online.getUniqueId()));
        }
        return profileRepository.findUuid(name).thenApply(found -> {
            if (found.isPresent()) {
                return found;
            }
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
            return cached == null ? Optional.<UUID>empty() : Optional.of(cached.getUniqueId());
        });
    }

    /**
     * Loads a (possibly offline) profile for read-only display, e.g. /stats of an offline player.
     *
     * @param uuid player id
     * @param name name hint
     * @return profile with stats
     */
    public CompletableFuture<PlayerProfile> loadOffline(UUID uuid, String name) {
        PlayerProfile online = profiles.get(uuid);
        if (online != null) {
            return CompletableFuture.completedFuture(online);
        }
        return CompletableFuture.supplyAsync(() -> loadBlocking(uuid, name));
    }

    /** @return profile repository */
    public ProfileRepository repository() {
        return profileRepository;
    }
}
