package com.georoute.service;

import com.georoute.model.RoadEdge;
import com.georoute.model.RoadNode;
import com.georoute.model.RouteResult;
import com.georoute.repository.RoadEdgeRepository;
import com.georoute.routing.Algorithm;
import com.georoute.routing.Path;
import com.georoute.routing.RoadGraph;
import com.georoute.util.GeoUtils;
import lombok.extern.slf4j.Slf4j;
//beans allows to read from application.prop
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Routes on an in-memory subgraph loaded from PostGIS for the source–destination
 * corridor. Long trips use a highway hierarchy + endpoint buffers so the load stays
 * small and under the JDBC timeout.
 */
@Slf4j
@Service
public class RoutingService {

    /** Minimum bbox padding (~3 km). */
    private static final double MIN_PAD_DEG = 0.03;
    /** Extra padding as a fraction of the source–dest span (short trips). */
    private static final double SPAN_PAD_FACTOR = 0.25;
    /** Cap undirected edges loaded into memory for one request. */
    private static final int MAX_SUBGRAPH_EDGES = 250_000;
    /** Straight-line distance above which we load major roads only (plus endpoint buffers). */
    private static final double LONG_TRIP_METERS = 12_000;
    /** Buffer around origin/destination that always includes all road classes (~1.2 km). */
    private static final double ENDPOINT_BUFFER_DEG = 0.012;
    /** Subgraph query timeout (seconds) — long envelopes need more than the global 30s snap timeout. */
    private static final int SUBGRAPH_QUERY_TIMEOUT_SEC = 120;

    private static final String MAJOR_TYPES_SQL =
            "('motorway','motorway_link','trunk','trunk_link',"
                    + "'primary','primary_link','secondary','secondary_link',"
                    + "'tertiary','tertiary_link','unclassified')";

    private final RoadEdgeRepository edgeRepo;
    private final JdbcTemplate jdbc;

    @Value("${routing.max.speed.kmh:110}")
    private double maxSpeedKmh;

    public RoutingService(RoadEdgeRepository edgeRepo, JdbcTemplate jdbc) {
        this.edgeRepo = edgeRepo;
        this.jdbc = jdbc;
    }

    public List<RouteResult> route(long sourceNodeId, long targetNodeId, int numPaths, Algorithm algorithm) {
        int k = Math.max(1, numPaths);

        RoadNode source = loadNode(sourceNodeId);
        RoadNode target = loadNode(targetNodeId);
        if (source == null || target == null) {
            log.warn("Snap node not in DB: source={}, target={}", sourceNodeId, targetNodeId);
            return List.of();
        }

        double distM = GeoUtils.haversineMeters(
                source.getLat(), source.getLng(), target.getLat(), target.getLng());
        boolean longTrip = distM >= LONG_TRIP_METERS;
        double pad = paddingDegrees(source, target, longTrip);

        RoadGraph graph = loadSubgraph(source, target, pad, longTrip);

        if (!graph.hasNode(sourceNodeId) || !graph.hasNode(targetNodeId)) {
            log.warn("Source/target outside loaded subgraph (pad={})", pad);
            return List.of();
        }

        List<Path> paths = graph.kShortestPaths(sourceNodeId, targetNodeId, k, algorithm);

        // If hierarchy missed a connection, retry once with all roads in a modest corridor.
        if (paths.isEmpty() && longTrip) {
            log.info("No path on major-road subgraph; retrying with all road classes");
            graph = loadSubgraph(source, target, Math.min(pad * 1.2, 0.12), false);
            paths = graph.kShortestPaths(sourceNodeId, targetNodeId, k, algorithm);
        } else if (paths.isEmpty() && pad < 0.2) {
            double wider = Math.min(pad * 2.0, 0.25);
            log.info("No path in first subgraph; retrying with pad={}", wider);
            graph = loadSubgraph(source, target, wider, false);
            paths = graph.kShortestPaths(sourceNodeId, targetNodeId, k, algorithm);
        }

        if (paths.isEmpty()) {
            log.warn("No route between nodes {} and {} (subgraph {} nodes).",
                    sourceNodeId, targetNodeId, graph.nodeCount());
            return List.of();
        }

        Set<Long> edgeIds = new HashSet<>();
        for (Path p : paths) {
            for (RoadEdge e : p.edges) {
                edgeIds.add(e.getId());
            }
        }
        Map<Long, List<double[]>> geometries = edgeRepo.fetchGeometries(edgeIds);

        List<RouteResult> results = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            Path p = paths.get(i);
            RouteResult r = new RouteResult();
            r.setRouteIndex(i);
            r.setLabel(i == 0 ? "Fastest Route" : "Alternate " + i);
            r.setTotalDistanceMeters(p.lengthMeters());
            r.setEstimatedTimeMinutes(p.cost / 60.0);
            r.setCoordinates(buildCoordinates(p, geometries, source, target));
            results.add(r);
        }
        return results;
    }

    private double paddingDegrees(RoadNode source, RoadNode target, boolean longTrip) {
        double spanLng = Math.abs(source.getLng() - target.getLng());
        double spanLat = Math.abs(source.getLat() - target.getLat());
        double span = Math.max(spanLng, spanLat);
        if (longTrip) {
            // Slim corridor for long trips — less junk residential in the bbox mid-section.
            return Math.max(0.025, Math.min(0.08, span * 0.12 + 0.02));
        }
        return Math.max(MIN_PAD_DEG, span * SPAN_PAD_FACTOR + MIN_PAD_DEG * 0.5);
    }

    /**
     * Load edges in the S–T envelope without joining {@code road_nodes} (that join
     * dominates latency on large graphs). Node coordinates are fetched in a second
     * indexed IN query. Long trips restrict mid-corridor edges to major road classes
     * while keeping all roads near the endpoints.
     */
    private RoadGraph loadSubgraph(RoadNode source, RoadNode target, double pad, boolean hierarchy) {
        double minLng = Math.min(source.getLng(), target.getLng()) - pad;
        double maxLng = Math.max(source.getLng(), target.getLng()) + pad;
        double minLat = Math.min(source.getLat(), target.getLat()) - pad;
        double maxLat = Math.max(source.getLat(), target.getLat()) + pad;

        String sql;
        Object[] args;
        if (hierarchy) {
            sql = """
                    SELECT e.id, e.source_id, e.target_id, e.road_name, e.road_type,
                           e.length_m, e.speed_kmh, e.oneway, e.cost
                    FROM road_edges e
                    WHERE e.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)
                      AND (
                            e.road_type IN %s
                            OR e.geom && ST_Expand(ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)
                            OR e.geom && ST_Expand(ST_SetSRID(ST_MakePoint(?, ?), 4326), ?)
                          )
                    LIMIT ?
                    """.formatted(MAJOR_TYPES_SQL);
            args = new Object[]{
                    minLng, minLat, maxLng, maxLat,
                    source.getLng(), source.getLat(), ENDPOINT_BUFFER_DEG,
                    target.getLng(), target.getLat(), ENDPOINT_BUFFER_DEG,
                    MAX_SUBGRAPH_EDGES
            };
        } else {
            sql = """
                    SELECT e.id, e.source_id, e.target_id, e.road_name, e.road_type,
                           e.length_m, e.speed_kmh, e.oneway, e.cost
                    FROM road_edges e
                    WHERE e.geom && ST_MakeEnvelope(?, ?, ?, ?, 4326)
                    LIMIT ?
                    """;
            args = new Object[]{minLng, minLat, maxLng, maxLat, MAX_SUBGRAPH_EDGES};
        }

        List<RoadEdge> undirected = new ArrayList<>();
        Set<Long> nodeIds = new HashSet<>();
        nodeIds.add(source.getId());
        nodeIds.add(target.getId());

        PreparedStatementCreator psc = con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setQueryTimeout(SUBGRAPH_QUERY_TIMEOUT_SEC);
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                if (a instanceof Integer) {
                    ps.setInt(i + 1, (Integer) a);
                } else if (a instanceof Long) {
                    ps.setLong(i + 1, (Long) a);
                } else if (a instanceof Double) {
                    ps.setDouble(i + 1, (Double) a);
                } else {
                    ps.setObject(i + 1, a);
                }
            }
            return ps;
        };

        jdbc.query(psc, rs -> {
            long id = rs.getLong("id");
            long sourceId = rs.getLong("source_id");
            long targetId = rs.getLong("target_id");
            nodeIds.add(sourceId);
            nodeIds.add(targetId);
            undirected.add(new RoadEdge(
                    id, sourceId, targetId,
                    rs.getString("road_name"), rs.getString("road_type"),
                    rs.getDouble("length_m"), rs.getDouble("speed_kmh"),
                    rs.getBoolean("oneway"), rs.getDouble("cost"), false));
        });

        Map<Long, RoadNode> nodes = loadNodes(nodeIds);
        nodes.put(source.getId(), source);
        nodes.put(target.getId(), target);

        List<RoadEdge> directed = new ArrayList<>(undirected.size() * 2);
        for (RoadEdge e : undirected) {
            if (!nodes.containsKey(e.getSourceId()) || !nodes.containsKey(e.getTargetId())) {
                continue;
            }
            directed.add(e);
            if (!e.isOneway()) {
                directed.add(new RoadEdge(
                        e.getId(), e.getTargetId(), e.getSourceId(),
                        e.getRoadName(), e.getRoadType(),
                        e.getLengthM(), e.getSpeedKmh(), e.isOneway(), e.getCost(), true));
            }
        }

        log.info("Loaded routing subgraph: {} nodes, {} directed edges (pad={}°, hierarchy={})",
                nodes.size(), directed.size(), pad, hierarchy);
        return new RoadGraph(new ArrayList<>(nodes.values()), directed, maxSpeedKmh);
    }

    private Map<Long, RoadNode> loadNodes(Set<Long> ids) {
        Map<Long, RoadNode> nodes = new HashMap<>(ids.size() * 2);
        if (ids.isEmpty()) {
            return nodes;
        }
        // Chunk to avoid oversized IN lists.
        List<Long> list = new ArrayList<>(ids);
        final int chunk = 5000;
        for (int i = 0; i < list.size(); i += chunk) {
            List<Long> part = list.subList(i, Math.min(i + chunk, list.size()));
            String in = String.join(",", part.stream().map(String::valueOf).toList());
            jdbc.query(
                    "SELECT id, ST_X(geom) AS lng, ST_Y(geom) AS lat FROM road_nodes WHERE id IN (" + in + ")",
                    rs -> {
                        nodes.put(rs.getLong("id"),
                                new RoadNode(rs.getLong("id"), rs.getDouble("lng"), rs.getDouble("lat")));
                    });
        }
        return nodes;
    }

    private List<double[]> buildCoordinates(Path path, Map<Long, List<double[]>> geometries,
                                            RoadNode source, RoadNode target) {
        List<double[]> out = new ArrayList<>();
        for (RoadEdge edge : path.edges) {
            List<double[]> seg = geometries.get(edge.getId());
            if (seg == null || seg.isEmpty()) {
                seg = fallbackSegment(edge, source, target);
            } else if (edge.isReversed()) {
                seg = reversed(seg);
            }
            appendSegment(out, seg);
        }
        if (out.isEmpty()) {
            out.add(new double[]{source.getLng(), source.getLat()});
            out.add(new double[]{target.getLng(), target.getLat()});
        }
        return out;
    }

    private List<double[]> fallbackSegment(RoadEdge edge, RoadNode source, RoadNode target) {
        // Prefer already-known endpoint coords when matching path ends; else query.
        List<double[]> seg = new ArrayList<>();
        RoadNode s = edge.getSourceId() == source.getId() ? source
                : edge.getSourceId() == target.getId() ? target : loadNode(edge.getSourceId());
        RoadNode t = edge.getTargetId() == target.getId() ? target
                : edge.getTargetId() == source.getId() ? source : loadNode(edge.getTargetId());
        if (s != null) {
            seg.add(new double[]{s.getLng(), s.getLat()});
        }
        if (t != null) {
            seg.add(new double[]{t.getLng(), t.getLat()});
        }
        return seg;
    }

    private List<double[]> reversed(List<double[]> seg) {
        List<double[]> r = new ArrayList<>(seg);
        java.util.Collections.reverse(r);
        return r;
    }

    private void appendSegment(List<double[]> out, List<double[]> seg) {
        for (double[] c : seg) {
            if (!out.isEmpty()) {
                double[] last = out.get(out.size() - 1);
                if (last[0] == c[0] && last[1] == c[1]) {
                    continue;
                }
            }
            out.add(c);
        }
    }

    public void invalidateGraph() {
        // Subgraphs are built per request; nothing to cache.
    }

    private RoadNode loadNode(long id) {
        List<RoadNode> rows = jdbc.query(
                "SELECT id, ST_X(geom) AS lng, ST_Y(geom) AS lat FROM road_nodes WHERE id = ?",
                (rs, i) -> new RoadNode(rs.getLong("id"), rs.getDouble("lng"), rs.getDouble("lat")),
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
