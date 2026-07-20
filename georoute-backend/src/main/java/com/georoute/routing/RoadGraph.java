package com.georoute.routing;

import com.georoute.model.RoadEdge;
import com.georoute.model.RoadNode;
import com.georoute.util.GeoUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * In-memory directed road graph with two interchangeable shortest-path engines.
 *
 * <p>Both engines share the same best-first machinery; they differ only in the heuristic:
 * <ul>
 *   <li><b>A*</b> uses an admissible Haversine heuristic {@code h(n) = straightLine(n,target) / maxSpeed},
 *       a lower bound on remaining travel time.</li>
 *   <li><b>Dijkstra</b> uses {@code h(n) = 0} (uniform-cost search).</li>
 * </ul>
 *
 * <p>Edge cost is travel time in seconds. Alternates use iterative edge penalties so routes
 * take different corridors (Yen's alone often only detours one segment on fine road graphs).
 */
public class RoadGraph {

    /** Multiply cost of used edges when searching for the next alternate. */
    private static final double EDGE_PENALTY = 2.5;
    /** Reject an alternate if Jaccard overlap of undirected edge ids exceeds this. */
    private static final double MAX_EDGE_OVERLAP = 0.55;

    private final Map<Long, RoadNode> nodes;
    private final Map<Long, List<RoadEdge>> adjacency;
    /** Lower bound divisor for the A* heuristic: max speed across the network, in m/s. */
    private final double maxSpeedMs;

    public RoadGraph(List<RoadNode> nodeList, List<RoadEdge> directedEdges, double maxSpeedKmh) {
        this.nodes = new HashMap<>(nodeList.size() * 2);
        for (RoadNode n : nodeList) {
            nodes.put(n.getId(), n);
        }
        this.adjacency = new HashMap<>();
        for (RoadEdge e : directedEdges) {
            adjacency.computeIfAbsent(e.getSourceId(), k -> new ArrayList<>()).add(e);
        }
        this.maxSpeedMs = Math.max(maxSpeedKmh, 1) / 3.6;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public boolean hasNode(long id) {
        return nodes.containsKey(id);
    }

    private static String edgeKey(RoadEdge e) {
        return e.getSourceId() + ":" + e.getTargetId() + ":" + e.getId() + ":" + (e.isReversed() ? 1 : 0);
    }

    // ─── Shortest path (A* / Dijkstra) ──────────────────────────────────────────

    public Optional<Path> shortestPath(long source, long target, Algorithm algorithm) {
        return search(source, target, algorithm, Collections.emptyMap());
    }

    private Optional<Path> search(long source, long target, Algorithm algorithm,
                                  Map<String, Double> penalties) {
        if (!nodes.containsKey(source) || !nodes.containsKey(target)) {
            return Optional.empty();
        }
        if (source == target) {
            return Optional.of(new Path(new ArrayList<>(List.of(source)), new ArrayList<>(), 0));
        }

        Map<Long, Double> gScore = new HashMap<>();
        Map<Long, Long> cameFromNode = new HashMap<>();
        Map<Long, RoadEdge> cameFromEdge = new HashMap<>();
        PriorityQueue<NodeState> open = new PriorityQueue<>();

        gScore.put(source, 0.0);
        open.add(new NodeState(source, 0.0, heuristic(source, target, algorithm)));

        while (!open.isEmpty()) {
            NodeState current = open.poll();
            long node = current.node;

            if (node == target) {
                return Optional.of(reconstruct(source, target, cameFromNode, cameFromEdge));
            }
            Double best = gScore.get(node);
            if (best != null && current.g > best + 1e-9) {
                continue;
            }

            for (RoadEdge edge : adjacency.getOrDefault(node, Collections.emptyList())) {
                long next = edge.getTargetId();
                double penalty = penalties.getOrDefault(edgeKey(edge), 1.0);
                double tentativeG = current.g + edge.getCost() * penalty;
                Double known = gScore.get(next);
                if (known == null || tentativeG < known - 1e-9) {
                    gScore.put(next, tentativeG);
                    cameFromNode.put(next, node);
                    cameFromEdge.put(next, edge);
                    open.add(new NodeState(next, tentativeG, tentativeG + heuristic(next, target, algorithm)));
                }
            }
        }
        return Optional.empty();
    }

    private double heuristic(long node, long target, Algorithm algorithm) {
        if (algorithm == Algorithm.DIJKSTRA) {
            return 0;
        }
        RoadNode a = nodes.get(node);
        RoadNode b = nodes.get(target);
        double meters = GeoUtils.haversineMeters(a.getLat(), a.getLng(), b.getLat(), b.getLng());
        return meters / maxSpeedMs;
    }

    /** Reconstruct path; cost is true travel time (penalties are search-only). */
    private Path reconstruct(long source, long target,
                             Map<Long, Long> cameFromNode, Map<Long, RoadEdge> cameFromEdge) {
        List<Long> nodeIds = new ArrayList<>();
        List<RoadEdge> edges = new ArrayList<>();
        long cur = target;
        while (cur != source) {
            nodeIds.add(cur);
            RoadEdge e = cameFromEdge.get(cur);
            if (e == null) {
                break;
            }
            edges.add(e);
            Long prev = cameFromNode.get(cur);
            if (prev == null) {
                break;
            }
            cur = prev;
        }
        nodeIds.add(source);
        Collections.reverse(nodeIds);
        Collections.reverse(edges);
        double trueCost = 0;
        for (RoadEdge e : edges) {
            trueCost += e.getCost();
        }
        return new Path(nodeIds, edges, trueCost);
    }

    /**
     * K diverse paths: shortest path, then repeatedly penalize used edges and re-search
     * so alternates use different corridors (not near-identical Yen detours).
     */
    public List<Path> kShortestPaths(long source, long target, int k, Algorithm algorithm) {
        List<Path> result = new ArrayList<>();
        if (k <= 0) {
            return result;
        }

        Map<String, Double> penalties = new HashMap<>();
        Set<String> seen = new HashSet<>();
        int attempts = 0;
        int maxAttempts = Math.max(k * 8, 8);

        while (result.size() < k && attempts < maxAttempts) {
            attempts++;
            Optional<Path> found = search(source, target, algorithm, penalties);
            if (found.isEmpty()) {
                break;
            }
            Path path = found.get();
            if (!seen.add(path.signature())) {
                applyPenalty(path, penalties, EDGE_PENALTY * 2);
                continue;
            }
            if (!result.isEmpty() && maxJaccardOverlap(path, result) > MAX_EDGE_OVERLAP) {
                applyPenalty(path, penalties, EDGE_PENALTY);
                continue;
            }
            result.add(path);
            applyPenalty(path, penalties, EDGE_PENALTY);
        }

        result.sort((a, b) -> Double.compare(a.cost, b.cost));
        return result;
    }

    private void applyPenalty(Path path, Map<String, Double> penalties, double factor) {
        for (RoadEdge e : path.edges) {
            String key = edgeKey(e);
            penalties.put(key, penalties.getOrDefault(key, 1.0) * factor);
            // Also penalize the opposite direction of the same undirected segment.
            String opp = e.getTargetId() + ":" + e.getSourceId() + ":" + e.getId() + ":"
                    + (e.isReversed() ? 0 : 1);
            penalties.put(opp, penalties.getOrDefault(opp, 1.0) * factor);
        }
    }

    /** Max Jaccard similarity of undirected edge ids vs any path already chosen. */
    private double maxJaccardOverlap(Path candidate, List<Path> existing) {
        Set<Long> cand = undirectedEdgeIds(candidate);
        double max = 0;
        for (Path p : existing) {
            Set<Long> other = undirectedEdgeIds(p);
            int shared = 0;
            for (Long id : cand) {
                if (other.contains(id)) {
                    shared++;
                }
            }
            int union = cand.size() + other.size() - shared;
            double jaccard = union == 0 ? 1.0 : (double) shared / union;
            max = Math.max(max, jaccard);
        }
        return max;
    }

    private Set<Long> undirectedEdgeIds(Path path) {
        Set<Long> ids = new HashSet<>();
        for (RoadEdge e : path.edges) {
            ids.add(e.getId());
        }
        return ids;
    }

    /** Open-set entry, ordered by f = g + h. */
    private static final class NodeState implements Comparable<NodeState> {
        final long node;
        final double g;
        final double f;

        NodeState(long node, double g, double f) {
            this.node = node;
            this.g = g;
            this.f = f;
        }

        @Override
        public int compareTo(NodeState o) {
            return Double.compare(this.f, o.f);
        }
    }
}
