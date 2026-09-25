package net.pvpserver.ffa;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.pvpserver.core.api.PracticeApi;
import net.pvpserver.core.api.bridge.FfaBridge;
import net.pvpserver.core.api.bridge.LobbyBridge;
import net.pvpserver.core.api.bridge.QueueBridge;
import net.pvpserver.core.arena.Arena;
import net.pvpserver.core.arena.ArenaTemplate;
import net.pvpserver.core.arena.RelativePosition;
import net.pvpserver.core.gui.ItemTemplates;
import net.pvpserver.core.kit.Kit;
import net.pvpserver.core.kit.KitService;
import net.pvpserver.core.message.MessageService;
import net.pvpserver.core.state.PlayerState;
import net.pvpserver.core.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FFA arenas: setup (pasting templates into the FFA world), joining, leaving, deaths with instant respawn,
 * killstreak rewards and combat-log punishment.
 */
public final class FfaManager implements FfaBridge {

    private final PvPFFA plugin;
    private final PracticeApi api;
    private final MessageService messages;
    private final Map<String, FfaArena> arenas = new LinkedHashMap<>();
    private final Map<UUID, FfaArena> byPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, FfaStats> stats = new ConcurrentHashMap<>();
    private World world;
    private boolean ready;

    /**
     * @param plugin FFA plugin
     */
    public FfaManager(PvPFFA plugin) {
        this.plugin = plugin;
        this.api = plugin.api();
        this.messages = plugin.messages();
    }

    /**
     * Creates the FFA world and pastes every configured arena. Waits for core templates first.
     */
    public void setup() {
        api.arenas().ready().thenRun(() -> Tasks.sync(this::build));
    }

    private void build() {
        ConfigurationSection config = plugin.config().get();
        world = api.worlds().voidWorld(config.getString("world", "pvp_ffa"), true);
        ConfigurationSection root = config.getConfigurationSection("arenas");
        if (root == null) {
            plugin.getLogger().warning("No FFA arenas configured");
            ready = true;
            return;
        }
        int index = 0;
        List<java.util.concurrent.CompletableFuture<Void>> pastes = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null || !s.getBoolean("enabled", true)) {
                continue;
            }
            Optional<Kit> kit = api.kits().get(s.getString("kit", ""));
            String templateName = s.getString("template", "classic");
            Arena definition = api.arenas().arena(templateName);
            ArenaTemplate template = api.arenas().template(templateName);
            if (kit.isEmpty() || definition == null || template == null) {
                plugin.getLogger().warning("FFA arena " + id + " skipped: unknown kit or template '" + templateName + "'");
                continue;
            }
            int ox = (index++ + 1) * 1000;
            int oy = 64;
            int oz = 1000;
            List<Location> spawns = new ArrayList<>();
            for (String raw : s.getStringList("spawns")) {
                RelativePosition position = RelativePosition.parse(raw);
                if (position != null) {
                    spawns.add(position.toLocation(world, ox, oy, oz));
                }
            }
            if (spawns.isEmpty()) {
                spawns.add(definition.spawnA().toLocation(world, ox, oy, oz));
                spawns.add(definition.spawnB().toLocation(world, ox, oy, oz));
            }
            FfaArena arena = new FfaArena(id.toLowerCase(Locale.ROOT), s.getString("display-name", "<white>" + id),
                    ItemTemplates.material(s.getString("icon"), kit.get().icon().getType()), kit.get(),
                    s.getBoolean("ranked", false), s.getString("knockback", ""), s.getDouble("safe-zone-radius", 6),
                    spawns, oy + definition.voidY());
            arenas.put(arena.id(), arena);
            pastes.add(api.arenas().paster().paste(template, world, ox, oy, oz));
            int cx1 = ox >> 4;
            int cx2 = (ox + template.sizeX()) >> 4;
            int cz1 = oz >> 4;
            int cz2 = (oz + template.sizeZ()) >> 4;
            for (int cx = cx1; cx <= cx2; cx++) {
                for (int cz = cz1; cz <= cz2; cz++) {
                    world.addPluginChunkTicket(cx, cz, plugin);
                }
            }
        }
        java.util.concurrent.CompletableFuture.allOf(pastes.toArray(new java.util.concurrent.CompletableFuture[0]))
                .whenComplete((v, error) -> Tasks.sync(() -> {
                    ready = true;
                    plugin.getLogger().info("FFA ready with " + arenas.size() + " arenas");
                }));
    }

    /** @return FFA world (null until set up) */
    public World world() {
        return world;
    }

    /** @return arenas */
    public Collection<FfaArena> arenas() {
        return arenas.values();
    }

    /**
     * @param id arena id
     * @return arena or null
     */
    public FfaArena arena(String id) {
        return id == null ? null : arenas.get(id.toLowerCase(Locale.ROOT));
    }

    /**
     * @param player player
     * @return arena the player is in, or null
     */
    public FfaArena arenaOf(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    /**
     * @param player player
     * @return session stats
     */
    public FfaStats stats(Player player) {
        return stats.computeIfAbsent(player.getUniqueId(), k -> new FfaStats());
    }

    // ------------------------------------------------------------------ join / leave

    /**
     * Joins an arena.
     *
     * @param player player
     * @param arena arena
     */
    public void join(Player player, FfaArena arena) {
        if (!ready) {
            messages.send(player, "ffa.not-ready");
            return;
        }
        PlayerState state = api.states().get(player);
        if (state == PlayerState.QUEUE) {
            api.bridges().get(QueueBridge.class).ifPresent(q -> q.leaveQueue(player));
        } else if (state != PlayerState.LOBBY && state != PlayerState.FFA) {
            messages.send(player, "ffa.busy");
            return;
        }
        if (api.parties().partyOf(player).isPresent() && !plugin.config().get().getBoolean("allow-parties", true)) {
            messages.send(player, "ffa.leave-party");
            return;
        }
        FfaArena previous = byPlayer.put(player.getUniqueId(), arena);
        if (previous != null) {
            previous.players().remove(player.getUniqueId());
        }
        arena.players().add(player.getUniqueId());
        api.states().set(player, PlayerState.FFA);
        stats.put(player.getUniqueId(), new FfaStats());
        messages.send(player, "ffa.joined", MessageService.p("arena", MessageService.mini().stripTags(arena.displayName())),
                MessageService.c("kit", arena.kit().name()), MessageService.p("players", arena.players().size()));
        respawn(player, arena);
    }

    /**
     * Leaves FFA and returns to the lobby.
     *
     * @param player player
     */
    @Override
    public void leave(Player player) {
        FfaArena arena = byPlayer.remove(player.getUniqueId());
        if (arena == null) {
            return;
        }
        arena.players().remove(player.getUniqueId());
        stats.remove(player.getUniqueId());
        api.combat().overrideKnockback(player, null);
        api.bridges().get(LobbyBridge.class).ifPresentOrElse(lobby -> lobby.sendToLobby(player), () -> {
            api.combat().reset(player);
            api.states().set(player, PlayerState.LOBBY);
        });
        messages.send(player, "ffa.left");
    }

    /**
     * Respawns (or first-spawns) a player with a fresh kit and spawn protection.
     *
     * @param player player
     * @param arena arena
     */
    public void respawn(Player player, FfaArena arena) {
        player.closeInventory();
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        api.combat().tags().clear(player);
        api.combat().applyKit(player, arena.kit());
        api.combat().overrideKnockback(player, arena.knockback());
        api.sidebars().sidebar(player).healthBelowName(arena.kit().rules().healthDisplay());
        KitService.heal(player);
        player.teleport(arena.randomSpawn());
        player.setFallDistance(0);
        long protection = plugin.config().get().getLong("respawn.protection-seconds", 2) * 1000L;
        stats(player).protectedUntil(System.currentTimeMillis() + protection);
        api.sidebars().refresh(player);
    }

    // ------------------------------------------------------------------ deaths

    /**
     * Handles a death (fake death, void or combat log) with instant respawn.
     *
     * @param victim victim
     * @param killer killer or null
     * @param combatLog whether the victim logged out in combat
     */
    public void death(Player victim, Player killer, boolean combatLog) {
        FfaArena arena = byPlayer.get(victim.getUniqueId());
        if (arena == null) {
            return;
        }
        if (killer != null && byPlayer.get(killer.getUniqueId()) != arena) {
            killer = null;
        }
        int lostStreak = stats(victim).death();
        int streak = 0;
        if (killer != null && killer != victim) {
            streak = stats(killer).kill();
        }
        int ratingChange = api.stats().recordFfaKill(killer == null ? null : killer.getUniqueId(), victim.getUniqueId(),
                arena.kit(), arena.ranked(), streak);
        if (combatLog && arena.ranked()) {
            int penalty = plugin.config().get().getInt("combat-log.rating-penalty", 10);
            var profile = api.profiles().get(victim);
            if (profile != null && penalty > 0) {
                profile.stats(arena.kit().id()).ffaElo(profile.stats(arena.kit().id()).ffaElo() - penalty);
                profile.markDirty();
            }
        }
        api.cosmetics().playDeathAnimation(victim, victim.getLocation());
        TagResolver[] resolvers = {MessageService.p("victim", victim.getName()),
                MessageService.p("killer", killer == null ? "-" : killer.getName()),
                MessageService.p("streak", streak), MessageService.p("lost_streak", lostStreak),
                MessageService.p("health", killer == null ? "0" : String.format(Locale.ROOT, "%.1f", killer.getHealth() / 2.0)),
                MessageService.p("rating", ratingChange)};
        String key = combatLog ? "ffa.combat-logged" : killer == null ? "ffa.died" : arena.ranked() ? "ffa.killed-ranked" : "ffa.killed";
        broadcast(arena, key, resolvers);
        if (killer != null) {
            api.cosmetics().playKillEffect(killer, victim.getLocation());
            reward(killer, arena, streak);
        }
        if (lostStreak >= plugin.config().get().getInt("killstreaks.announce-ended-at", 5)) {
            broadcast(arena, "ffa.streak-ended", resolvers);
        }
        if (!combatLog) {
            respawn(victim, arena);
        }
    }

    private void reward(Player killer, FfaArena arena, int streak) {
        ConfigurationSection rewards = plugin.config().get().getConfigurationSection("kill-rewards");
        if (rewards != null) {
            if (rewards.getBoolean("heal", true)) {
                KitService.heal(killer);
            }
            if (rewards.getBoolean("refill-kit", false)) {
                refill(killer, arena.kit());
            }
            giveItems(killer, rewards.getMapList("items"));
        }
        ConfigurationSection streaks = plugin.config().get().getConfigurationSection("killstreaks.rewards." + streak);
        if (streaks != null) {
            giveItems(killer, streaks.getMapList("items"));
            List<PotionEffect> effects = new net.pvpserver.core.kit.KitItemParser(plugin.getLogger(),
                    new org.bukkit.NamespacedKey(plugin, "special")).parseEffects("killstreak", streaks.getStringList("effects"));
            effects.forEach(killer::addPotionEffect);
            for (String command : streaks.getStringList("commands")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("<player>", killer.getName()));
            }
        }
        int every = plugin.config().get().getInt("killstreaks.broadcast-every", 5);
        if (every > 0 && streak > 0 && streak % every == 0) {
            broadcast(arena, "ffa.streak", MessageService.p("player", killer.getName()), MessageService.p("streak", streak));
        }
    }

    private void refill(Player player, Kit kit) {
        // Top the inventory up with the kit's items that were used (potions, pearls, food) without moving anything.
        ItemStack[] layout = api.kits().layoutFor(player, kit).apply(kit.contents());
        for (int slot = 0; slot < layout.length; slot++) {
            ItemStack wanted = layout[slot];
            ItemStack current = player.getInventory().getItem(slot);
            if (wanted != null && (current == null || current.getType().isAir())) {
                player.getInventory().setItem(slot, wanted.clone());
            }
        }
    }

    private void giveItems(Player player, List<Map<?, ?>> items) {
        var parser = new net.pvpserver.core.kit.KitItemParser(plugin.getLogger(), new org.bukkit.NamespacedKey(plugin, "special"));
        for (Map<?, ?> map : items) {
            ItemStack item = parser.parse("ffa-reward", map);
            if (item != null) {
                player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
            }
        }
    }

    /**
     * @param arena arena
     * @param key message key
     * @param resolvers placeholders
     */
    public void broadcast(FfaArena arena, String key, TagResolver... resolvers) {
        for (UUID uuid : arena.players()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                messages.send(player, key, resolvers);
            }
        }
    }

    /**
     * Removes a disconnecting player; punishes combat logging.
     *
     * @param player quitting player
     */
    public void quit(Player player) {
        FfaArena arena = byPlayer.get(player.getUniqueId());
        if (arena == null) {
            return;
        }
        if (api.combat().tags().isTagged(player) && plugin.config().get().getBoolean("combat-log.punish", true)) {
            UUID attacker = api.combat().tags().lastAttacker(player);
            Player killer = attacker == null ? null : Bukkit.getPlayer(attacker);
            death(player, killer, true);
        }
        byPlayer.remove(player.getUniqueId());
        arena.players().remove(player.getUniqueId());
        stats.remove(player.getUniqueId());
    }

    /** Sends everybody back to the lobby (disable). */
    public void shutdown() {
        for (UUID uuid : List.copyOf(byPlayer.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                leave(player);
            }
        }
    }

    @Override
    public void openFfaMenu(Player player) {
        new FfaMenu(plugin, player).open();
    }

    @Override
    public int playerCount() {
        return byPlayer.size();
    }

    /** @return whether arenas are pasted and usable */
    public boolean ready() {
        return ready;
    }

    /**
     * @param material material for block decay checks
     * @return whether it is a fluid source placed by buckets
     */
    static boolean isFluid(Material material) {
        return material == Material.WATER || material == Material.LAVA;
    }
}
