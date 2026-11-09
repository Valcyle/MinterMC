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
 * Adds a {@code .goto <x> <z>} command that walks the bot to a position. The
 * search runs on a worker thread so the bot's event loop stays responsive; the
 * resulting waypoints are followed with {@link Bot#moveTo(Vector3)}.
 * <br>
 * Bedrock reports player positions at eye level, so the search works one player
 * height below the reported Y. Paths may walk on one level or drop down a block;
 * climbing up requires a jump and is not supported yet.
 */
public final class NavPlugin implements MinterPlugin {

    /** Distance between a player's feet and the reported position. */
    private static final double EYE_HEIGHT = 1.62;

    private static final int MAX_NODES = 8192;
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
        int startFeetY = (int) Math.floor(position.getY() - EYE_HEIGHT);
        int startZ = (int) Math.floor(position.getZ());
        bot.chat("searching a path to " + targetX + ", " + targetZ);

        worker.submit(() -> {
            List<int[]> path = findPath(bot, startX, startFeetY, startZ, targetX, targetZ);
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
        bot.moveTo(new Vector3(node[0] + 0.5, node[1] + EYE_HEIGHT, node[2] + 0.5))
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        bot.chat("navigation stopped");
                        return;
                    }
                    bot.after(1, () -> follow(bot, path, index + 1));
                });
    }

    /**
     * Runs an A* search over the loaded blocks.
     *
     * @return the waypoints from start to goal, or {@code null} when no path
     *         exists within the cached area
     */
    private List<int[]> findPath(Bot bot, int startX, int startFeetY, int startZ,
            int targetX, int targetZ) {
        if (Math.abs(targetX - startX) > MAX_RANGE || Math.abs(targetZ - startZ) > MAX_RANGE) {
            return null;
        }
        Integer startY = surfaceNear(bot, startX, startFeetY, startZ, 2);
        if (startY == null) {
            return null;
        }
        Integer goalY = surfaceNear(bot, targetX, startY, targetZ, 3);
        if (goalY == null) {
            return null;
        }

        Node start = new Node(startX, startY, startZ);
        Node goal = new Node(targetX, goalY, targetZ);
        Map<Node, Integer> cost = new HashMap<>();
        Map<Node, Node> cameFrom = new HashMap<>();
        Set<Node> closed = new HashSet<>();
        PriorityQueue<Scored> open = new PriorityQueue<>(Comparator.comparingInt(Scored::score));

        cost.put(start, 0);
        open.add(new Scored(start, heuristic(start, goal)));

        while (!open.isEmpty()) {
            Node current = open.poll().node();
            if (current.equals(goal)) {
                return buildPath(cameFrom, current);
            }
            if (!closed.add(current)) {
                continue;
            }
            if (closed.size() > MAX_NODES) {
                return null;
            }

            for (Node neighbour : neighbours(bot, current)) {
                if (closed.contains(neighbour)) {
                    continue;
                }
                int nextCost = cost.get(current) + 1;
                Integer known = cost.get(neighbour);
                if (known != null && known <= nextCost) {
                    continue;
                }
                cost.put(neighbour, nextCost);
                cameFrom.put(neighbour, current);
                open.add(new Scored(neighbour, nextCost + heuristic(neighbour, goal)));
            }
        }
        return null;
    }

    /**
     * Returns the walkable neighbours of a node.
     * <br>
     * A path may stay on the same level or drop down one block. Climbing up is
     * omitted because the bot cannot jump yet.
     */
    private static List<Node> neighbours(Bot bot, Node current) {
        List<Node> result = new ArrayList<>(4);
        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] direction : directions) {
            int x = current.x() + direction[0];
            int z = current.z() + direction[1];
            if (walkable(bot, x, current.y(), z)) {
                result.add(new Node(x, current.y(), z));
            } else if (walkable(bot, x, current.y() - 1, z)) {
                result.add(new Node(x, current.y() - 1, z));
            }
        }
        return result;
    }

    private static boolean walkable(Bot bot, int x, int y, int z) {
        Block ground = bot.getLoadedBlockAt(new Vector3(x, y - 1, z));
        Block feet = bot.getLoadedBlockAt(new Vector3(x, y, z));
        Block head = bot.getLoadedBlockAt(new Vector3(x, y + 1, z));
        return ground != null && feet != null && head != null
                && !ground.isPassable() && feet.isPassable() && head.isPassable();
    }

    private static Integer surfaceNear(Bot bot, int x, int baseY, int z, int range) {
        for (int offset = 0; offset <= range; offset++) {
            if (walkable(bot, x, baseY + offset, z)) {
                return baseY + offset;
            }
            if (offset > 0 && walkable(bot, x, baseY - offset, z)) {
                return baseY - offset;
            }
        }
        return null;
    }

    private static int heuristic(Node from, Node to) {
        return Math.abs(to.x() - from.x())
                + Math.abs(to.z() - from.z())
                + Math.abs(to.y() - from.y());
    }

    private static List<int[]> buildPath(Map<Node, Node> cameFrom, Node goal) {
        List<Node> reversed = new ArrayList<>();
        Node cursor = goal;
        while (cursor != null) {
            reversed.add(cursor);
            cursor = cameFrom.get(cursor);
        }
        Collections.reverse(reversed);

        List<int[]> path = new ArrayList<>();
        for (int index = 1; index < reversed.size(); index++) {
            Node node = reversed.get(index);
            if (index + 1 < reversed.size()) {
                Node next = reversed.get(index + 1);
                Node previous = reversed.get(index - 1);
                if (node.x() - previous.x() == next.x() - node.x()
                        && node.y() == next.y() && node.y() == previous.y()
                        && node.z() - previous.z() == next.z() - node.z()) {
                    continue;
                }
            }
            path.add(new int[] { node.x(), node.y(), node.z() });
        }
        return path;
    }

    /**
     * A grid position at the player's feet.
     */
    private record Node(int x, int y, int z) {
    }

    /**
     * A node together with its A* score.
     */
    private record Scored(Node node, int score) {
    }
}
