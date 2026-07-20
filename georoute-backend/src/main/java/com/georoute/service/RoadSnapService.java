package com.georoute.service;

import com.georoute.model.SnapResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Snaps an arbitrary clicked point to the nearest road using PostGIS only — no external
 * geocoding. Returns the closest point on the nearest edge (for the pin) plus the nearer
 * endpoint node (the routing entry point).
 */
@Service
public class RoadSnapService {

    /** Maximum snap distance, in metres. */
    private static final double SNAP_RADIUS_M = 3000;

    /**
     * Degrees of padding around the click for the GIST bbox prefilter.
     * ~0.030° ≈ 3.0–3.4 km — enough to cover the 3 km snap radius without scanning
     * the full multi-million-edge table.
     */
    private static final double SNAP_BBOX_DEG = 0.030;

    private final JdbcTemplate jdbc;
    private final DataLoadStatus loadStatus;

    public RoadSnapService(JdbcTemplate jdbc, DataLoadStatus loadStatus) {
        this.jdbc = jdbc;
        this.loadStatus = loadStatus;
    }

    public SnapResult snap(double lat, double lng) {
        if (loadStatus.isLoading()) {
            throw new DataNotReadyException("Road network is still loading — wait a minute and try again.");
        }
        if (!loadStatus.isReady()) {
            throw new DataNotReadyException(loadStatus.getMessage());
        }

        // Bbox prefilter (&&) uses the GIST index; KNN only runs on candidates inside ~1.5 km.
        // Global ORDER BY geom <-> point on 20M+ edges exceeds the JDBC query timeout.
        String sql = """
                WITH pt AS (
                    SELECT ST_SetSRID(ST_MakePoint(?, ?), 4326) AS g
                ),
                near AS (
                    SELECT e.id, e.source_id, e.target_id, e.geom, e.road_name
                    FROM road_edges e, pt
                    WHERE e.geom && ST_Expand(pt.g, ?)
                    ORDER BY e.geom <-> pt.g
                    LIMIT 1
                )
                SELECT
                    n.source_id,
                    n.target_id,
                    n.road_name,
                    ST_X(ST_ClosestPoint(n.geom, pt.g)) AS snap_lng,
                    ST_Y(ST_ClosestPoint(n.geom, pt.g)) AS snap_lat,
                    ST_Distance(n.geom::geography, pt.g::geography) AS dist_m,
                    ST_Distance(ST_StartPoint(n.geom)::geography, pt.g::geography) AS src_dist,
                    ST_Distance(ST_EndPoint(n.geom)::geography, pt.g::geography) AS tgt_dist
                FROM near n, pt
                """;

        List<SnapResult> results = jdbc.query(sql, (rs, i) -> {
            double distM = rs.getDouble("dist_m");
            if (distM > SNAP_RADIUS_M) {
                return null;
            }
            long sourceId = rs.getLong("source_id");
            long targetId = rs.getLong("target_id");
            double srcDist = rs.getDouble("src_dist");
            double tgtDist = rs.getDouble("tgt_dist");
            long nodeId = srcDist <= tgtDist ? sourceId : targetId;

            String name = rs.getString("road_name");
            SnapResult sr = new SnapResult();
            sr.setSnappedLng(rs.getDouble("snap_lng"));
            sr.setSnappedLat(rs.getDouble("snap_lat"));
            sr.setNodeId(nodeId);
            sr.setNearestRoadName(name == null || name.isBlank() ? "Unnamed road" : name);
            sr.setDistanceToRoadM(distM);
            return sr;
        }, lng, lat, SNAP_BBOX_DEG);

        SnapResult hit = results.stream().filter(r -> r != null).findFirst().orElse(null);
        if (hit == null) {
            throw new NoRoadNearbyException("No road within " + (int) SNAP_RADIUS_M + "m of this point");
        }
        return hit;
    }
}
