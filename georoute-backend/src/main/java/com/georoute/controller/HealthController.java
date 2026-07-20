package com.georoute.controller;

import com.georoute.service.DataLoadStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
// rest api's get http requests like post, get and return data in xml or json format. Here we are using json format. 
// http requests need to contain all the information to process the request. The information is sent in the form of a request body.
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbc;
    private final DataLoadStatus loadStatus;

    public HealthController(JdbcTemplate jdbc, DataLoadStatus loadStatus) {
        this.jdbc = jdbc;
        this.loadStatus = loadStatus;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean postgis = false;
        boolean dataLoaded = false;
        long nodeCount = 0;
        long edgeCount = 0;
        try {
            Integer pg = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_extension WHERE extname = 'postgis'", Integer.class);
            postgis = pg != null && pg > 0;
            Long edges = jdbc.queryForObject("SELECT COUNT(*) FROM road_edges", Long.class);
            edgeCount = edges == null ? 0 : edges;
            Long nodes = jdbc.queryForObject("SELECT COUNT(*) FROM road_nodes", Long.class);
            nodeCount = nodes == null ? 0 : nodes;
            dataLoaded = edgeCount > 0;
            if (dataLoaded) {
                List<Map<String, Double>> boundsRows = jdbc.query(
                        "SELECT ST_XMin(e) AS min_lng, ST_YMin(e) AS min_lat, "
                                + "ST_XMax(e) AS max_lng, ST_YMax(e) AS max_lat "
                                + "FROM (SELECT ST_Extent(geom) AS e FROM road_nodes) s",
                        (rs, i) -> {
                            Map<String, Double> b = new LinkedHashMap<>();
                            b.put("minLng", rs.getDouble("min_lng"));
                            b.put("minLat", rs.getDouble("min_lat"));
                            b.put("maxLng", rs.getDouble("max_lng"));
                            b.put("maxLat", rs.getDouble("max_lat"));
                            return b;
                        });
                if (!boundsRows.isEmpty()) {
                    body.put("roadBounds", boundsRows.get(0));
                }
            }
        } catch (Exception ignored) {
        }
        body.put("status", "UP");
        body.put("postgis", postgis);
        body.put("dataLoaded", dataLoaded);
        body.put("nodeCount", nodeCount);
        body.put("edgeCount", edgeCount);
        body.put("dataLoading", loadStatus.isLoading());
        body.put("dataReady", loadStatus.isReady());
        body.put("message", loadStatus.getMessage());
        return body;
    }
}
