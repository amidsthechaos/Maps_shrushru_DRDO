package com.georoute.repository;

import com.georoute.model.RoadNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read access to {@code road_nodes}. Thin layer over {@link JdbcTemplate} with native
 * PostGIS SQL (geometry is never mapped through the ORM).
 */
@Repository
public class RoadNodeRepository {

    private final JdbcTemplate jdbc;

    public RoadNodeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Load every node with its WGS84 coordinates (used to build the routing graph). */
    public List<RoadNode> findAll() {
        return jdbc.query(
                "SELECT id, ST_X(geom) AS lng, ST_Y(geom) AS lat FROM road_nodes",
                (rs, i) -> new RoadNode(rs.getLong("id"), rs.getDouble("lng"), rs.getDouble("lat")));
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM road_nodes", Long.class);
        return n == null ? 0 : n;
    }
}
