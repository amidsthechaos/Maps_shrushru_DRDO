package com.georoute.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.georoute.model.RoadEdge;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read access to {@code road_edges}. Thin layer over {@link JdbcTemplate} with native
 * PostGIS SQL. Edge geometry is fetched lazily (only for the edges actually used by a
 * computed route) to keep the in-memory graph small.
 */
@Repository
public class RoadEdgeRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public RoadEdgeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Load all edges for the in-memory graph WITHOUT geometry. Two-way segments are
     * expanded into two directed {@link RoadEdge} entries (forward + reversed); one-way
     * segments yield a single forward entry.
     */
    public List<RoadEdge> findAllDirected() {
        List<RoadEdge> directed = new ArrayList<>();
        jdbc.query(
                "SELECT id, source_id, target_id, road_name, road_type, length_m, speed_kmh, oneway, cost "
                        + "FROM road_edges",
                rs -> {
                    long id = rs.getLong("id");
                    long src = rs.getLong("source_id");
                    long tgt = rs.getLong("target_id");
                    String name = rs.getString("road_name");
                    String type = rs.getString("road_type");
                    double len = rs.getDouble("length_m");
                    double speed = rs.getDouble("speed_kmh");
                    boolean oneway = rs.getBoolean("oneway");
                    double cost = rs.getDouble("cost");

                    // Forward: source -> target (matches stored geometry orientation).
                    directed.add(new RoadEdge(id, src, tgt, name, type, len, speed, oneway, cost, false));
                    // Reverse direction only when the segment is two-way.
                    if (!oneway) {
                        directed.add(new RoadEdge(id, tgt, src, name, type, len, speed, oneway, cost, true));
                    }
                });
        return directed;
    }

    /**
     * Fetch geometry vertices (in stored source->target orientation) for the given edge
     * row ids. Returns {@code edgeId -> [[lng,lat], ...]}.
     */
    public Map<Long, List<double[]>> fetchGeometries(Collection<Long> edgeIds) {
        Map<Long, List<double[]>> result = new HashMap<>();
        if (edgeIds == null || edgeIds.isEmpty()) {
            return result;
        }
        String inClause = String.join(",", edgeIds.stream().map(String::valueOf).toList());
        jdbc.query(
                "SELECT id, ST_AsGeoJSON(geom) AS gj FROM road_edges WHERE id IN (" + inClause + ")",
                rs -> {
                    long id = rs.getLong("id");
                    String gj = rs.getString("gj");
                    result.put(id, parseLineStringCoords(gj));
                });
        return result;
    }

    private List<double[]> parseLineStringCoords(String geoJson) {
        List<double[]> coords = new ArrayList<>();
        try {
            JsonNode node = mapper.readTree(geoJson);
            JsonNode arr = node.get("coordinates");
            if (arr != null && arr.isArray()) {
                for (JsonNode pair : arr) {
                    coords.add(new double[]{pair.get(0).asDouble(), pair.get(1).asDouble()});
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse edge geometry GeoJSON", e);
        }
        return coords;
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM road_edges", Long.class);
        return n == null ? 0 : n;
    }
}
