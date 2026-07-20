package com.georoute.routing;

import com.georoute.model.RoadEdge;
import com.georoute.model.RoadNode;
import com.georoute.util.GeoUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the two routing engines on a small hand-built graph:
 *
 * <pre>
 *   A(0,0) --- B(0.01,0) --- C(0.02,0)      (bottom path, shorter)
 *     \                         /
 *      D(0.01,0.01) ------------            (top path, longer)
 *   with a B-D connector.
 * </pre>
 */
class RoadGraphTest {

    private static final long A = 1, B = 2, C = 3, D = 4;
    private static final double SPEED_KMH = 50;

    private RoadGraph buildGraph() {
        List<RoadNode> nodes = List.of(
                new RoadNode(A, 0.00, 0.00),
                new RoadNode(B, 0.01, 0.00),
                new RoadNode(C, 0.02, 0.00),
                new RoadNode(D, 0.01, 0.01));

        List<RoadEdge> edges = new ArrayList<>();
        long id = 1;
        addBidirectional(edges, id++, nodes, A, B);
        addBidirectional(edges, id++, nodes, B, C);
        addBidirectional(edges, id++, nodes, A, D);
        addBidirectional(edges, id++, nodes, D, C);
        addBidirectional(edges, id++, nodes, B, D);

        return new RoadGraph(nodes, edges, SPEED_KMH);
    }

    private void addBidirectional(List<RoadEdge> edges, long id, List<RoadNode> nodes, long a, long b) {
        RoadNode na = find(nodes, a);
        RoadNode nb = find(nodes, b);
        double length = GeoUtils.haversineMeters(na.getLat(), na.getLng(), nb.getLat(), nb.getLng());
        double cost = length / (SPEED_KMH / 3.6);
        edges.add(new RoadEdge(id, a, b, "road" + id, "primary", length, SPEED_KMH, false, cost, false));
        edges.add(new RoadEdge(id, b, a, "road" + id, "primary", length, SPEED_KMH, false, cost, true));
    }

    private RoadNode find(List<RoadNode> nodes, long id) {
        return nodes.stream().filter(n -> n.getId() == id).findFirst().orElseThrow();
    }

    @Test
    void astarAndDijkstraFindIdenticalShortestPath() {
        RoadGraph g = buildGraph();

        Optional<Path> astar = g.shortestPath(A, C, Algorithm.ASTAR);
        Optional<Path> dijkstra = g.shortestPath(A, C, Algorithm.DIJKSTRA);

        assertTrue(astar.isPresent(), "A* should find a path");
        assertTrue(dijkstra.isPresent(), "Dijkstra should find a path");

        assertEquals(dijkstra.get().nodes, astar.get().nodes,
                "A* and Dijkstra must return the same node sequence");
        assertEquals(dijkstra.get().cost, astar.get().cost, 1e-6,
                "A* and Dijkstra must return the same optimal cost");

        // The shortest route is the bottom path A -> B -> C.
        assertEquals(List.of(A, B, C), astar.get().nodes);
    }

    @Test
    void kPathsReturnsDistinctAlternates() {
        RoadGraph g = buildGraph();

        List<Path> paths = g.kShortestPaths(A, C, 3, Algorithm.ASTAR);

        assertTrue(paths.size() >= 2, "Expected at least 2 distinct routes");
        // First is the optimal bottom path.
        assertEquals(List.of(A, B, C), paths.get(0).nodes);
        // Costs must be non-decreasing (sorted by true cost).
        for (int i = 1; i < paths.size(); i++) {
            assertTrue(paths.get(i).cost >= paths.get(i - 1).cost - 1e-9,
                    "Paths must be ordered by non-decreasing cost");
        }
        // All distinct.
        for (int i = 0; i < paths.size(); i++) {
            for (int j = i + 1; j < paths.size(); j++) {
                assertFalse(paths.get(i).signature().equals(paths.get(j).signature()),
                        "Routes must be distinct");
            }
        }
    }

    @Test
    void dijkstraEqualsAstarCostForAllPairs() {
        RoadGraph g = buildGraph();
        long[] all = {A, B, C, D};
        for (long s : all) {
            for (long t : all) {
                Optional<Path> a = g.shortestPath(s, t, Algorithm.ASTAR);
                Optional<Path> d = g.shortestPath(s, t, Algorithm.DIJKSTRA);
                assertEquals(d.isPresent(), a.isPresent());
                if (a.isPresent()) {
                    assertEquals(d.get().cost, a.get().cost, 1e-6,
                            "Cost mismatch for " + s + "->" + t);
                }
            }
        }
    }
}
