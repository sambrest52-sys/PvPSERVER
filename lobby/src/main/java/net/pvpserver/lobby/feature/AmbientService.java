package net.pvpserver.lobby.feature;

import net.pvpserver.lobby.PvPLobby;
import net.pvpserver.lobby.config.LobbySettings;
import net.pvpserver.lobby.layout.BlockPos;
import net.pvpserver.lobby.layout.LobbyLayout;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Ambient particles (fountain spray, falling blossoms, sparkles, smoke...) and the coloured curtains inside portals.
 * Emitters are spread over {@code ambient.interval-ticks}, a few per tick, and only players within the view distance
 * receive them, so an empty corner of the lobby costs nothing.
 */
public final class AmbientService {

    private record Emitter(Location at, LobbySettings.EmitterType type) {
    }

    private final PvPLobby plugin;
    private final List<Emitter> emitters = new ArrayList<>();
    private List<LobbyLayout.Portal> portals = List.of();
    private long tick;

    /**
     * @param plugin lobby plugin
     */
    public AmbientService(PvPLobby plugin) {
        this.plugin = plugin;
    }

    /** Re-reads emitters and portals from the layout. */
    public void rebuild() {
        emitters.clear();
        World world = plugin.lobbyWorld().world();
        LobbySettings.Ambient settings = plugin.settings().ambient();
        for (LobbyLayout.Emitter emitter : plugin.lobbyWorld().layout().emitters()) {
            LobbySettings.EmitterType type = settings.emitters().get(emitter.type());
            if (type == null) {
                plugin.getLogger().warning("layout.yml emitter type " + emitter.type() + " is not defined in config.yml ambient.emitters");
                continue;
            }
            emitters.add(new Emitter(emitter.at().at(world), type));
        }
        portals = List.copyOf(plugin.lobbyWorld().layout().portals());
    }

    /** Called every tick. */
    public void tick() {
        LobbySettings.Ambient settings = plugin.settings().ambient();
        if (!settings.enabled()) {
            return;
        }
        int interval = settings.intervalTicks();
        int bucket = (int) (tick++ % interval);
        double range = settings.viewDistance();
        for (int i = bucket; i < emitters.size(); i += interval) {
            Emitter emitter = emitters.get(i);
            Collection<Player> viewers = emitter.at().getNearbyPlayers(range);
            if (viewers.isEmpty()) {
                continue;
            }
            LobbySettings.EmitterType type = emitter.type();
            Object data = data(type.particle(), type.color());
            for (Player viewer : viewers) {
                viewer.spawnParticle(type.particle(), emitter.at(), type.count(), type.offsetX(), type.offsetY(), type.offsetZ(),
                        type.speed(), data);
            }
        }
        if (bucket == 0 && plugin.settings().portals().particles()) {
            for (LobbyLayout.Portal portal : portals) {
                curtain(portal, range);
            }
        }
    }

    private void curtain(LobbyLayout.Portal portal, double range) {
        BlockPos min = portal.box().min();
        BlockPos max = portal.box().max();
        Location center = portal.box().center().at(plugin.lobbyWorld().world());
        Collection<Player> viewers = center.getNearbyPlayers(range);
        if (viewers.isEmpty()) {
            return;
        }
        Color color;
        try {
            color = Color.fromRGB(Integer.parseInt(portal.color().replace("#", ""), 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            color = Color.WHITE;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.4f);
        double sx = (max.x() - min.x() + 1) / 2.0;
        double sy = (max.y() - min.y() + 1) / 2.0;
        double sz = (max.z() - min.z() + 1) / 2.0;
        for (Player viewer : viewers) {
            viewer.spawnParticle(Particle.DUST, center, 14, sx * 0.6, sy * 0.6, sz * 0.6, 0, dust);
        }
    }

    private static Object data(Particle particle, Color color) {
        if (particle.getDataType() == Particle.DustOptions.class) {
            return new Particle.DustOptions(color == null ? Color.WHITE : color, 1f);
        }
        if (particle.getDataType() == Color.class) {
            return color == null ? Color.WHITE : color;
        }
        return null;
    }

    /** @return emitters in use */
    public int size() {
        return emitters.size();
    }
}
