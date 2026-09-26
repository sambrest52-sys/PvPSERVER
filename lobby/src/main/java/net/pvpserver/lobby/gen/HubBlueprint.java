package net.pvpserver.lobby.gen;

import net.pvpserver.core.arena.TemplateBuilder;
import net.pvpserver.lobby.layout.BlockPos;
import net.pvpserver.lobby.layout.LobbyLayout;

import java.util.List;

/**
 * A generated hub: its blocks and where everything is, both relative to the template's minimum corner.
 *
 * @param blocks blocks
 * @param layout layout in template coordinates
 * @param floorY template y of the plaza floor block
 * @param centerX template x of the hub centre
 * @param centerZ template z of the hub centre
 * @param parkourStones every parkour stone in course order (template coordinates), for validation and previews
 */
public record HubBlueprint(TemplateBuilder blocks, LobbyLayout layout, int floorY, int centerX, int centerZ,
                           List<BlockPos> parkourStones) {

    /**
     * Where to paste so that the hub centre lands on x = z = 0 and the plaza floor at {@code worldFloorY}.
     *
     * @param worldFloorY world height of the plaza floor
     * @return {x, y, z} paste origin
     */
    public int[] origin(int worldFloorY) {
        return new int[]{-centerX, worldFloorY - floorY, -centerZ};
    }

    /**
     * @param worldFloorY world height of the plaza floor
     * @return layout in world coordinates
     */
    public LobbyLayout worldLayout(int worldFloorY) {
        int[] origin = origin(worldFloorY);
        return layout.translate(origin[0], origin[1], origin[2]).rounded();
    }
}
