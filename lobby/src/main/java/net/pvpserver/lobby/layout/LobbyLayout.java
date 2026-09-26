package net.pvpserver.lobby.layout;

import java.util.List;
import java.util.Map;

/**
 * Where everything in the lobby is: spawn, NPCs, holograms, portals, launch pads, the parkour course, easter eggs,
 * zones, particle emitters and the leaderboard wall. Coordinates are in the lobby world. Written by the generator
 * or an import into layout.yml and editable by hand or with {@code /lobby set}.
 *
 * @param spawn spawn point
 * @param voidY players below this height are rescued
 * @param border world border (null = leave the border alone)
 * @param npcs NPC id to position
 * @param holograms hologram id to position
 * @param portals walk-in portals
 * @param pads launch pads
 * @param buttons clickable blocks (anvils, lecterns...) that run an action
 * @param parkour parkour course (null = none)
 * @param eggs easter eggs
 * @param zones named areas (entry messages)
 * @param emitters ambient particle emitters
 * @param wall leaderboard wall (null = none)
 */
public record LobbyLayout(Point spawn, int voidY, Border border, Map<String, Point> npcs, Map<String, Point> holograms,
                          List<Portal> portals, List<LaunchPad> pads, List<Button> buttons, Parkour parkour, List<Egg> eggs,
                          List<Zone> zones,
                          List<Emitter> emitters, Wall wall) {

    /**
     * Square world border.
     *
     * @param centerX centre x
     * @param centerZ centre z
     * @param size side length
     */
    public record Border(double centerX, double centerZ, double size) {
    }

    /**
     * Walk-in portal.
     *
     * @param id id (also used for its name in messages)
     * @param box trigger box
     * @param action action run on entry (see npcs.yml for the list)
     * @param color particle colour, e.g. {@code #FFAA00}
     */
    public record Portal(String id, Box box, String action, String color) {
    }

    /**
     * Launch pad: stepping on the block launches the player with the velocity.
     *
     * @param at pad block (the plate, or the block players stand on)
     * @param vx velocity x
     * @param vy velocity y
     * @param vz velocity z
     */
    public record LaunchPad(BlockPos at, double vx, double vy, double vz) {
    }

    /**
     * A block that runs an action when right-clicked.
     *
     * @param at block
     * @param action action (same list as NPCs)
     */
    public record Button(BlockPos at, String action) {
    }

    /**
     * Parkour course: plates are blocks players step into (pressure plates) or onto.
     *
     * @param start start plate
     * @param checkpoints checkpoint plates in order
     * @param finish finish plate
     * @param fallY falling below this height returns runners to their last checkpoint
     */
    public record Parkour(BlockPos start, List<BlockPos> checkpoints, BlockPos finish, int fallY) {
    }

    /**
     * Hidden easter egg found by right-clicking its block.
     *
     * @param id stable id (progress is stored per id)
     * @param at block
     */
    public record Egg(String id, BlockPos at) {
    }

    /**
     * Named circular area, announced when entered.
     *
     * @param id id (texts live in config.yml under zones)
     * @param x centre x
     * @param z centre z
     * @param radius radius
     */
    public record Zone(String id, double x, double z, double radius) {

        /**
         * @param px x
         * @param pz z
         * @return whether the column is inside
         */
        public boolean contains(double px, double pz) {
            double dx = px - x;
            double dz = pz - z;
            return dx * dx + dz * dz <= radius * radius;
        }
    }

    /**
     * Ambient particle emitter.
     *
     * @param type emitter type from config.yml ambient.emitters
     * @param at position
     */
    public record Emitter(String type, Point at) {
    }

    /**
     * Leaderboard wall: a grid of panels. {@code at} is the top-left panel; its yaw is the direction the panels face.
     *
     * @param at top-left panel position and facing
     * @param columns panels per row
     * @param spacingX horizontal distance between panel centres
     * @param spacingY vertical distance between rows
     */
    public record Wall(Point at, int columns, double spacingX, double spacingY) {

        /**
         * @param index panel index (row major)
         * @return panel position, facing the same way as the wall
         */
        public Point panel(int index) {
            int row = index / Math.max(1, columns);
            int column = index % Math.max(1, columns);
            double yaw = Math.toRadians(at.yaw());
            // The wall faces (-sin, cos); a viewer standing in front looks the other way and has (cos, sin) on their right.
            double rightX = Math.cos(yaw);
            double rightZ = Math.sin(yaw);
            return new Point(at.x() + rightX * column * spacingX, at.y() - row * spacingY, at.z() + rightZ * column * spacingX,
                    at.yaw(), 0f);
        }
    }

    /** @return an empty layout with a spawn at 0 65 0 */
    public static LobbyLayout empty() {
        return new LobbyLayout(Point.of(0.5, 65, 0.5), 0, null, Map.of(), Map.of(), List.of(), List.of(), List.of(), null,
                List.of(), List.of(), List.of(), null);
    }

    /**
     * @param newSpawn spawn
     * @return copy with a new spawn
     */
    public LobbyLayout withSpawn(Point newSpawn) {
        return new LobbyLayout(newSpawn, voidY, border, npcs, holograms, portals, pads, buttons, parkour, eggs, zones, emitters, wall);
    }

    /**
     * @param newNpcs NPC positions
     * @param newHolograms hologram positions
     * @return copy with new NPC and hologram positions
     */
    public LobbyLayout withPlacements(Map<String, Point> newNpcs, Map<String, Point> newHolograms) {
        return new LobbyLayout(spawn, voidY, border, Map.copyOf(newNpcs), Map.copyOf(newHolograms), portals, pads, buttons,
                parkour, eggs, zones, emitters, wall);
    }

    /**
     * @param newPortals portals
     * @param newPads pads
     * @param newButtons buttons
     * @param newParkour parkour
     * @param newEggs eggs
     * @return copy with new triggers
     */
    public LobbyLayout withTriggers(List<Portal> newPortals, List<LaunchPad> newPads, List<Button> newButtons, Parkour newParkour,
                                    List<Egg> newEggs) {
        return new LobbyLayout(spawn, voidY, border, npcs, holograms, List.copyOf(newPortals), List.copyOf(newPads),
                List.copyOf(newButtons), newParkour, List.copyOf(newEggs), zones, emitters, wall);
    }

    /**
     * @param newWall wall
     * @return copy with a new leaderboard wall
     */
    public LobbyLayout withWall(Wall newWall) {
        return new LobbyLayout(spawn, voidY, border, npcs, holograms, portals, pads, buttons, parkour, eggs, zones, emitters, newWall);
    }

    /**
     * @return copy with every coordinate rounded to a thousandth of a block, exactly as layout.yml stores it
     */
    public LobbyLayout rounded() {
        java.util.function.UnaryOperator<Point> p = point -> new Point(round(point.x()), round(point.y()), round(point.z()),
                point.yaw(), point.pitch());
        Map<String, Point> roundedNpcs = new java.util.LinkedHashMap<>();
        npcs.forEach((id, point) -> roundedNpcs.put(id, p.apply(point)));
        Map<String, Point> roundedHolograms = new java.util.LinkedHashMap<>();
        holograms.forEach((id, point) -> roundedHolograms.put(id, p.apply(point)));
        return new LobbyLayout(p.apply(spawn), voidY,
                border == null ? null : new Border(round(border.centerX()), round(border.centerZ()), round(border.size())),
                roundedNpcs, roundedHolograms, portals,
                pads.stream().map(pad -> new LaunchPad(pad.at(), round(pad.vx()), round(pad.vy()), round(pad.vz()))).toList(),
                buttons, parkour, eggs,
                zones.stream().map(zone -> new Zone(zone.id(), round(zone.x()), round(zone.z()), round(zone.radius()))).toList(),
                emitters.stream().map(emitter -> new Emitter(emitter.type(), p.apply(emitter.at()))).toList(),
                wall == null ? null : new Wall(p.apply(wall.at()), wall.columns(), round(wall.spacingX()), round(wall.spacingY())));
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    /**
     * Moves every position by an offset (templates are generated at 0,0,0 and pasted at an origin).
     *
     * @param dx x offset
     * @param dy y offset
     * @param dz z offset
     * @return moved copy
     */
    public LobbyLayout translate(int dx, int dy, int dz) {
        java.util.function.UnaryOperator<Point> p = point -> point.add(dx, dy, dz);
        java.util.function.UnaryOperator<BlockPos> b = pos -> pos.add(dx, dy, dz);
        Map<String, Point> movedNpcs = new java.util.LinkedHashMap<>();
        npcs.forEach((id, point) -> movedNpcs.put(id, p.apply(point)));
        Map<String, Point> movedHolograms = new java.util.LinkedHashMap<>();
        holograms.forEach((id, point) -> movedHolograms.put(id, p.apply(point)));
        return new LobbyLayout(p.apply(spawn), voidY + dy,
                border == null ? null : new Border(border.centerX() + dx, border.centerZ() + dz, border.size()),
                movedNpcs, movedHolograms,
                portals.stream().map(portal -> new Portal(portal.id(), new Box(b.apply(portal.box().min()), b.apply(portal.box().max())),
                        portal.action(), portal.color())).toList(),
                pads.stream().map(pad -> new LaunchPad(b.apply(pad.at()), pad.vx(), pad.vy(), pad.vz())).toList(),
                buttons.stream().map(button -> new Button(b.apply(button.at()), button.action())).toList(),
                parkour == null ? null : new Parkour(b.apply(parkour.start()), parkour.checkpoints().stream().map(b).toList(),
                        b.apply(parkour.finish()), parkour.fallY() + dy),
                eggs.stream().map(egg -> new Egg(egg.id(), b.apply(egg.at()))).toList(),
                zones.stream().map(zone -> new Zone(zone.id(), zone.x() + dx, zone.z() + dz, zone.radius())).toList(),
                emitters.stream().map(emitter -> new Emitter(emitter.type(), p.apply(emitter.at()))).toList(),
                wall == null ? null : new Wall(p.apply(wall.at()), wall.columns(), wall.spacingX(), wall.spacingY()));
    }
}
