package com.georoute.routing;

import com.georoute.model.RoadEdge;
import com.georoute.model.RoadNode;
import com.georoute.util.GeoUtils;
import org.springframework.jdbc.core.JdbcTemplate;

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
 * DB-backed road graph for A-star and Dijkstra. Neighbors are fetched per node from PostGIS so
 * multi-million-node networks do not need to be loaded entirely into RAM.
 */
public class JdbcRoadGraph {

    private final JdbcTemplate jdbc;
    private final double maxSpeedMs;
    private final Map<Long, RoadNode> nodeCache = new HashMap<>();

    private static final String OUTGOING_SQL = """
            SELECT id, source_id, target_id, road_name, road_type, length_m, speed_kmh, oneway, cost, false AS reversed
            FROM road_edges WHERE source_id = ?
            UNION ALL
            SELECT id, target_id AS source_id, source_id AS target_id,
                   road_name, road_type, length_m, speed_kmh, oneway, cost, true AS reversed
            FROM road_edges WHERE target_id = ? AND oneway = false
            """;

    public JdbcRoadGraph(JdbcTemplate jdbc, double maxSpeedKmh) {
        this.jdbc = jdbc;
        this.maxSpeedMs = Math.max(maxSpeedKmh, 1) / 3.6;
    }

    public long nodeCount() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM road_nodes", Long.class);
        return n == null ? 0 : n;
    }

    public boolean hasNode(long id) {
        if (nodeCache.containsKey(id)) {
            return true;
        }
        List<Long> found = jdbc.query("SELECT id FROM road_nodes WHERE id = ? LIMIT 1",
                (rs, i) -> rs.getLong("id"), id);
        if (!found.isEmpty()) {
            loadNode(id);
            return true;
        }
        return false;
    }

    private RoadNode loadNode(long id) {
        RoadNode cached = nodeCache.get(id);
        if (cached != null) {
            return cached;
        }
        List<RoadNode> rows = jdbc.query(
                "SELECT id, ST_X(geom) AS lng, ST_Y(geom) AS lat FROM road_nodes WHERE id = ?",
                (rs, i) -> new RoadNode(rs.getLong("id"), rs.getDouble("lng"), rs.getDouble("lat")),
                id);
        if (rows.isEmpty()) {
            return null;
        }
        nodeCache.put(id, rows.get(0));
        return rows.get(0);
    }

    private List<RoadEdge> outgoing(long nodeId) {
        return jdbc.query(OUTGOING_SQL, (rs, i) -> new RoadEdge(
                rs.getLong("id"),
                rs.getLong("source_id"),
                rs.getLong("target_id"),
                rs.getString("road_name"),
                rs.getString("road_type"),
                rs.getDouble("length_m"),
                rs.getDouble("speed_kmh"),
                rs.getBoolean("oneway"),
                rs.getDouble("cost"),
                rs.getBoolean("reversed")
        ), nodeId, nodeId);
    }

    private static String edgeKey(RoadEdge e) {
        return e.getSourceId() + ":" + e.getTargetId() + ":" + e.getId() + ":" + (e.isReversed() ? 1 : 0);
    }

    public Optional<Path> shortestPath(long source, long target, Algorithm algorithm) {
        return search(source, target, algorithm, Collections.emptySet(), Collections.emptySet());
    }

    private Optional<Path> search(long source, long target, Algorithm algorithm,
                                  Set<String> blockedEdges, Set<Long> blockedNodes) {
        if (!hasNode(source) || !hasNode(target)) {
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

        int explored = 0;
        final int maxExplore = 10_000_000;

        while (!open.isEmpty()) {
            NodeState current = open.poll();
            long node = current.node;

            if (node == target) {
                return Optional.of(reconstruct(source, target, cameFromNode, cameFromEdge, gScore.get(target)));
            }
            Double best = gScore.get(node);
            if (best != null && current.g > best + 1e-9) {
                continue;
            }

            explored++;
            if (explored > maxExplore) {
                return Optional.empty();
            }

            for (RoadEdge edge : outgoing(node)) {
                long next = edge.getTargetId();
                if (blockedNodes.contains(next)) {
                    continue;
                }
                if (blockedEdges.contains(edgeKey(edge))) {
                    continue;
                }
                double tentativeG = current.g + edge.getCost();
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
        RoadNode a = loadNode(node);
        RoadNode b = loadNode(target);
        if (a == null || b == null) {
            return 0;
        }
        double meters = GeoUtils.haversineMeters(a.getLat(), a.getLng(), b.getLat(), b.getLng());
        return meters / maxSpeedMs;
    }

    private Path reconstruct(long source, long target,
                           Map<Long, Long> cameFromNode, Map<Long, RoadEdge> cameFromEdge,
                           double cost) {
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
        return new Path(nodeIds, edges, cost);
    }

    public List<Path> kShortestPaths(long source, long target, int k, Algorithm algorithm) {
        List<Path> result = new ArrayList<>();
        Optional<Path> first = shortestPath(source, target, algorithm);
        if (first.isEmpty()) {
            return result;
        }
        result.add(first.get());
        if (k <= 1) {
            return result;
        }

        PriorityQueue<Path> candidates = new PriorityQueue<>(
                (p1, p2) -> p1.cost != p2.cost ? Double.compare(p1.cost, p2.cost)
                        : Integer.compare(p1.nodes.size(), p2.nodes.size()));
        Set<String> seen = new HashSet<>();
        seen.add(first.get().signature());

        for (int i = 1; i < k; i++) {
            Path prev = result.get(i - 1);

            for (int j = 0; j < prev.nodes.size() - 1; j++) {
                long spurNode = prev.nodes.get(j);
                List<Long> rootNodes = new ArrayList<>(prev.nodes.subList(0, j + 1));
                List<RoadEdge> rootEdges = new ArrayList<>(prev.edges.subList(0, j));

                Set<String> blockedEdges = new HashSet<>();
                for (Path p : result) {
                    if (p.edges.size() > j && samePrefix(p.nodes, rootNodes)) {
                        blockedEdges.add(edgeKey(p.edges.get(j)));
                    }
                }
                Set<Long> blockedNodes = new HashSet<>(rootNodes.subList(0, rootNodes.size() - 1));

                Optional<Path> spur = search(spurNode, target, algorithm, blockedEdges, blockedNodes);
                if (spur.isPresent()) {
                    Path total = Path.concat(rootNodes, rootEdges, spur.get());
                    if (seen.add(total.signature())) {
                        candidates.add(total);
                    }
                }
            }

            if (candidates.isEmpty()) {
                break;
            }
            result.add(candidates.poll());
        }
        return result;
    }

    private boolean samePrefix(List<Long> path, List<Long> prefix) {
        if (path.size() < prefix.size()) {
            return false;
        }
        for (int i = 0; i < prefix.size(); i++) {
            if (!path.get(i).equals(prefix.get(i))) {
                return false;
            }
        }
        return true;
    }

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
