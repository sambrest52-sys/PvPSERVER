package net.pvpserver.core.arena;

import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Applies block changes on the main thread under a per-tick block and time budget, so pasting or resetting large
 * arenas never causes a lag spike.
 */
public final class BlockPaster {

    private final Deque<Job> jobs = new ArrayDeque<>();
    /** Opportunistic work (pool pre-warming); only runs when no urgent job is waiting. */
    private final Deque<Job> background = new ArrayDeque<>();
    private int blocksPerTick;
    private long maxNanosPerTick;
    private BukkitTask task;

    /**
     * @param blocksPerTick block budget per tick
     * @param maxMillisPerTick time budget per tick
     */
    public BlockPaster(int blocksPerTick, int maxMillisPerTick) {
        configure(blocksPerTick, maxMillisPerTick);
    }

    /**
     * @param blocksPerTick block budget per tick
     * @param maxMillisPerTick time budget per tick
     */
    public void configure(int blocksPerTick, int maxMillisPerTick) {
        this.blocksPerTick = Math.max(500, blocksPerTick);
        this.maxNanosPerTick = Math.max(1, maxMillisPerTick) * 1_000_000L;
    }

    /** Starts the tick task. */
    public void start() {
        task = net.pvpserver.core.util.Tasks.timer(this::tick, 1L, 1L);
    }

    /** Stops the task; pending jobs are completed synchronously (used on shutdown). */
    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (Deque<Job> queue : List.of(jobs, background)) {
            while (!queue.isEmpty()) {
                Job job = queue.poll();
                while (!job.run(Integer.MAX_VALUE)) {
                    // drain
                }
                job.future.complete(null);
            }
        }
    }

    /** @return queued job count (urgent and background) */
    public int pending() {
        return jobs.size() + background.size();
    }

    private void tick() {
        long start = System.nanoTime();
        int budget = blocksPerTick;
        while (budget > 0 && (!jobs.isEmpty() || !background.isEmpty()) && System.nanoTime() - start < maxNanosPerTick) {
            Deque<Job> queue = jobs.isEmpty() ? background : jobs;
            Job job = queue.peek();
            int chunk = Math.min(budget, 2048);
            int before = job.done;
            boolean finished;
            try {
                finished = job.run(chunk);
            } catch (RuntimeException e) {
                queue.poll();
                job.future.completeExceptionally(e);
                continue;
            }
            budget -= Math.max(1, job.done - before);
            if (finished) {
                queue.poll();
                job.future.complete(null);
            }
        }
    }

    /**
     * Pastes a template in the background: only while no match, FFA, editor or reset job is waiting (pool
     * pre-warming).
     *
     * @param template template
     * @param world world
     * @param ox origin x
     * @param oy origin y
     * @param oz origin z
     * @return completion future
     */
    public CompletableFuture<Void> pasteInBackground(ArenaTemplate template, World world, int ox, int oy, int oz) {
        Job job = pasteJob(template, world, ox, oy, oz);
        background.add(job);
        return job.future;
    }

    /**
     * Pastes a template's non-air blocks at an origin.
     *
     * @param template template
     * @param world world
     * @param ox origin x
     * @param oy origin y
     * @param oz origin z
     * @return completion future
     */
    public CompletableFuture<Void> paste(ArenaTemplate template, World world, int ox, int oy, int oz) {
        Job job = pasteJob(template, world, ox, oy, oz);
        jobs.add(job);
        return job.future;
    }

    private Job pasteJob(ArenaTemplate template, World world, int ox, int oy, int oz) {
        BlockData[] palette = template.parsedPalette();
        int[] solid = template.solidIndices();
        short[] blocks = template.blocks();
        return new Job(solid.length) {
            @Override
            void apply(int i) {
                int index = solid[i];
                world.getBlockAt(ox + template.xOf(index), oy + template.yOf(index), oz + template.zOf(index))
                        .setBlockData(palette[blocks[index]], false);
            }
        };
    }

    /**
     * Restores journaled blocks.
     *
     * @param world world
     * @param entries packed position → original data
     * @return completion future
     */
    public CompletableFuture<Void> restore(World world, List<Map.Entry<Long, BlockData>> entries) {
        Job job = new Job(entries.size()) {
            @Override
            void apply(int i) {
                Map.Entry<Long, BlockData> entry = entries.get(i);
                long key = entry.getKey();
                world.getBlockAt(ArenaInstance.keyX(key), ArenaInstance.keyY(key), ArenaInstance.keyZ(key))
                        .setBlockData(entry.getValue(), false);
            }
        };
        jobs.add(job);
        return job.future;
    }

    /**
     * Sets every non-air template block to air (instance destruction).
     *
     * @param template template
     * @param world world
     * @param ox origin x
     * @param oy origin y
     * @param oz origin z
     * @return completion future
     */
    public CompletableFuture<Void> clear(ArenaTemplate template, World world, int ox, int oy, int oz) {
        int[] solid = template.solidIndices();
        BlockData air = org.bukkit.Material.AIR.createBlockData();
        Job job = new Job(solid.length) {
            @Override
            void apply(int i) {
                int index = solid[i];
                world.getBlockAt(ox + template.xOf(index), oy + template.yOf(index), oz + template.zOf(index))
                        .setBlockData(air, false);
            }
        };
        jobs.add(job);
        return job.future;
    }

    /**
     * Sets every non-air block in a box to air (clearing an area before re-pasting).
     *
     * @param world world
     * @param ox min x
     * @param oy min y
     * @param oz min z
     * @param sizeX size x (0 = nothing to clear)
     * @param sizeY size y
     * @param sizeZ size z
     * @return completion future
     */
    public CompletableFuture<Void> clearBox(World world, int ox, int oy, int oz, int sizeX, int sizeY, int sizeZ) {
        BlockData air = org.bukkit.Material.AIR.createBlockData();
        int total = Math.max(0, sizeX) * Math.max(0, sizeY) * Math.max(0, sizeZ);
        Job job = new Job(total) {
            @Override
            void apply(int i) {
                int x = i % sizeX;
                int z = (i / sizeX) % sizeZ;
                int y = i / (sizeX * sizeZ);
                var block = world.getBlockAt(ox + x, oy + y, oz + z);
                if (!block.getType().isAir()) {
                    block.setBlockData(air, false);
                }
            }
        };
        jobs.add(job);
        return job.future;
    }

    private abstract static class Job {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        final int total;
        int done;

        Job(int total) {
            this.total = total;
        }

        abstract void apply(int i);

        boolean run(int budget) {
            int end = (int) Math.min((long) done + budget, total);
            for (; done < end; done++) {
                apply(done);
            }
            return done >= total;
        }
    }
}
