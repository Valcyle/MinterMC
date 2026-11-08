package example.nav;

import net.mcsmash.mintermc.api.Bot;
import net.mcsmash.mintermc.api.MinterPlugin;
import net.mcsmash.mintermc.api.PluginContext;
import net.mcsmash.mintermc.api.block.Block;
import net.mcsmash.mintermc.api.math.Vector3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Example pathfinding plugin.
 * <br>
 * Adds a {@code .goto <x> <z>} command that walks the bot to a position on the
 * current Y level. The search runs on a worker thread so the bot's event loop
 * stays responsive; the resulting waypoints are followed with
 * {@link Bot#moveTo(Vector3)}.
 */
public final class NavPlugin implements MinterPlugin {

    private static final int MAX_NODES = 4096;
    private static final int MAX_RANGE = 48;

    private final ExecutorService worker =
            Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "nav-worker");
                thread.setDaemon(true);
                return thread;
            });

    @Override
    public void onEnable(PluginContext context) {
        context.registerCommand("goto", this::onGoto);
        context.getLogger().info("Nav plugin enabled");
    }

    @Override
    public void onDisable() {
        worker.shutdownNow();
    }

    private void onGoto(Bot bot, String command, List<String> args) {
        if (args.size() < 2) {
            bot.chat("usage: .goto <x> <z>");
            return;
        }
        int targetX;
        int targetZ;
        try {
            targetX = Integer.parseInt(args.get(0));
            targetZ = Integer.parseInt(args.get(1));
        } catch (NumberFormatException e) {
            bot.chat("coordinates must be integers");
            return;
        }

        Vector3 position = bot.getLocation().getPosition();
        int startX = (int) Math.floor(position.getX());
        int startY = (int) Math.floor(position.getY());
        int startZ = (int) Math.floor(position.getZ());
        bot.chat("searching a path to " + targetX + ", " + targetZ);

        worker.submit(() -> {
            List<int[]> path = findPath(bot, startX, startY, startZ, targetX, targetZ);
            bot.after(0, () -> {
                if (path == null) {
                    bot.chat("no path found");
                    return;
                }
                bot.chat("navigating, " + path.size() + " steps");
                follow(bot, path, 0);
            });
        });
    }

    private void follow(Bot bot, List<int[]> path, int index) {
        if (index >= path.size()) {
            bot.chat("arrived");
            return;
        }
        int[] node = path.get(index);
        bot.moveTo(new Vector3(node[0] + 0.5, node[1], node[2] + 0.5))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        bot.chat("navigation stopped");
                        return;
                    }
                    bot.after(1, () -> follow(bot, path, index + 1));
                });
    }

    /**
     * Runs an A* search on the current Y level.
     *
     * @return the waypoints from start to goal, or {@code null} when no path
     *         exists within the cached area
     */
    private List<int[]> findPath(Bot bot, int startX, int startY, int startZ,
            int targetX, int targetZ) {
        if (Math.abs(targetX - startX) > MAX_RANGE || Math.abs(targetZ - startZ) > MAX_RANGE) {
            return null;
        }
        if (!walkable(bot, startX, startY, startZ)
                && !walkable(bot, startX, startY + 1, startZ)) {
            return null;
        }
        if (walkable(bot, startX, startY + 1, startZ)) {
            startY += 1;
        }
        if (!walkable(bot, targetX, startY, targetZ)) {
            return null;
        }

        Map<Long, Integer> cost = new HashMap<>();
        Map<Long, Long> cameFrom = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(Node::score));

        long startKey = key(startX, startZ);
        long goalKey = key(targetX, targetZ);
        cost.put(startKey, 0);
        open.add(new Node(startX, startZ, heuristic(startX, startZ, targetX, targetZ)));

        while (!open.isEmpty()) {
            Node current = open.poll();
            long currentKey = key(current.x, current.z);
            if (currentKey == goalKey) {
                return buildPath(cameFrom, currentKey, startY);
            }
            if (!closed.add(currentKey)) {
                continue;
            }
            if (closed.size() > MAX_NODES) {
                return null;
            }

            int[][] neighbours = {
                    { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }
            };
            for (int[] offset : neighbours) {
                int nx = current.x + offset[0];
                int nz = current.z + offset[1];
                long neighbourKey = key(nx, nz);
                if (closed.contains(neighbourKey) || !walkable(bot, nx, startY, nz)) {
                    continue;
                }
                int nextCost = cost.get(currentKey) + 1;
                Integer known = cost.get(neighbourKey);
                if (known != null && known <= nextCost) {
                    continue;
                }
                cost.put(neighbourKey, nextCost);
                cameFrom.put(neighbourKey, currentKey);
                open.add(new Node(nx, nz,
                        nextCost + heuristic(nx, nz, targetX, targetZ)));
            }
        }
        return null;
    }

    private static boolean walkable(Bot bot, int x, int y, int z) {
        Block ground = bot.getLoadedBlockAt(new Vector3(x, y - 1, z));
        Block feet = bot.getLoadedBlockAt(new Vector3(x, y, z));
        Block head = bot.getLoadedBlockAt(new Vector3(x, y + 1, z));
        return ground != null && feet != null && head != null
                && !ground.isPassable() && feet.isPassable() && head.isPassable();
    }

    private static int heuristic(int x, int z, int targetX, int targetZ) {
        return Math.abs(targetX - x) + Math.abs(targetZ - z);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static List<int[]> buildPath(Map<Long, Long> cameFrom, long goalKey, int y) {
        List<int[]> reversed = new ArrayList<>();
        Long cursor = goalKey;
        while (cursor != null) {
            reversed.add(new int[] { (int) (cursor >> 32), y, (int) (long) cursor });
            cursor = cameFrom.get(cursor);
        }
        Collections.reverse(reversed);
        // Drop the start node and collinear intermediate points.
        List<int[]> path = new ArrayList<>();
        for (int index = 1; index < reversed.size(); index++) {
            int[] node = reversed.get(index);
            if (index + 1 < reversed.size()) {
                int[] next = reversed.get(index + 1);
                int[] previous = reversed.get(index - 1);
                if (node[0] - previous[0] == next[0] - node[0]
                        && node[2] - previous[2] == next[2] - node[2]) {
                    continue;
                }
            }
            path.add(node);
        }
        return path;
    }

    /**
     * A search node with its heuristic score.
     */
    private static final class Node {
        private final int x;
        private final int z;
        private final int score;

        private Node(int x, int z, int score) {
            this.x = x;
            this.z = z;
            this.score = score;
        }

        private int score() {
            return score;
        }
    }
}
