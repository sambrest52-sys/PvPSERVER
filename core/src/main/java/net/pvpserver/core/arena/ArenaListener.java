package net.pvpserver.core.arena;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.util.List;

/**
 * Journals every block change inside arena instances so they can be rolled back, and stops fluids, fire and
 * redstone from affecting anything outside an instance.
 */
public final class ArenaListener implements Listener {

    private final ArenaService arenas;

    /**
     * @param arenas arena service
     */
    public ArenaListener(ArenaService arenas) {
        this.arenas = arenas;
    }

    private boolean inArenaWorld(World world) {
        return world == arenas.world();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ArenaInstance instance = arenas.instanceAt(event.getBlock().getLocation());
        if (instance != null) {
            instance.journal(event.getBlock(), event.getBlockReplacedState().getBlockData());
            instance.markPlaced(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ArenaInstance instance = arenas.instanceAt(event.getBlock().getLocation());
        if (instance != null) {
            instance.journal(event.getBlock());
            instance.unmarkPlaced(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        journal(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        journal(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (!inArenaWorld(event.getBlock().getWorld())) {
            return;
        }
        ArenaInstance instance = arenas.instanceAt(event.getToBlock().getLocation());
        if (instance == null || !instance.canBuildAt(event.getToBlock().getX(), event.getToBlock().getY(), event.getToBlock().getZ())) {
            event.setCancelled(true);
            return;
        }
        instance.journal(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        journal(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        journal(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (inArenaWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (inArenaWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        journal(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        if (inArenaWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        journal(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        journalAll(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        journalAll(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRedstone(BlockRedstoneEvent event) {
        if (inArenaWorld(event.getBlock().getWorld())) {
            event.setNewCurrent(0);
        }
    }

    private void journalAll(List<Block> blocks) {
        for (Block block : blocks) {
            journal(block);
        }
    }

    private void journal(Block block) {
        if (!inArenaWorld(block.getWorld())) {
            return;
        }
        ArenaInstance instance = arenas.instanceAt(block.getLocation());
        if (instance != null) {
            instance.journal(block);
        }
    }
}
