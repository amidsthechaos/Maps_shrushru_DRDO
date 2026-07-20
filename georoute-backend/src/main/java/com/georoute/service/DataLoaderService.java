package com.georoute.service;

import com.georoute.util.GeoUtils;
import lombok.extern.slf4j.Slf4j;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.opengis.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Streams the road-network Shapefile into PostGIS in a background thread so the HTTP
 * server stays reachable. Rows are written in batches — the full graph is never held
 * in heap. Idempotent via {@code system_config.shp_loaded}.
 */
@Slf4j
@Service
public class DataLoaderService {

    private static final double DEDUP_DEGREES = 0.00001;
    private static final int EDGE_BATCH_SIZE = 2000;
    private static final int PROGRESS_EVERY_FEATURES = 50_000;

    private final JdbcTemplate jdbc;
    private final DataLoadStatus loadStatus;
    private final RoutingService routingService;

    @Value("${shapefile.base.path}")
    private String shapefilePath;

    @Value("${shapefile.attr.name:name}")
    private String attrName;

    @Value("${shapefile.attr.fclass:fclass}")
    private String attrFclass;

    @Value("${shapefile.attr.oneway:oneway}")
    private String attrOneway;

    @Value("${shapefile.force-reload:false}")
    private boolean forceReload;

    @Value("${shapefile.node-cache.size:300000}")
    private int nodeCacheSize;

    public DataLoaderService(JdbcTemplate jdbc, DataLoadStatus loadStatus, RoutingService routingService) {
        this.jdbc = jdbc;
        this.loadStatus = loadStatus;
        this.routingService = routingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleLoad() {
        Thread loader = new Thread(this::loadInBackground, "georoute-data-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private void loadInBackground() {
        try {
            if (forceReload && isLoaded()) {
                log.warn("shapefile.force-reload=true — clearing road graph and re-ingesting.");
                clearRoadData();
            }
            if (isLoaded()) {
                long nodes = countNodes();
                long edges = countEdges();
                loadStatus.finishLoading((int) nodes, (int) edges);
                log.info("Road network already loaded ({} nodes, {} edges).", nodes, edges);
                return;
            }

            if (countNodes() > 0 || countEdges() > 0) {
                log.warn("Partial road data from a previous failed ingest — clearing tables.");
                clearRoadData();
            }

            File shp = new File(shapefilePath);
            if (!shp.exists()) {
                log.warn("Shapefile not found at '{}'.", shp.getAbsolutePath());
                loadStatus.failMissingShapefile(shp.getAbsolutePath());
                return;
            }

            loadStatus.startLoading();
            log.info("Streaming road network from {} (background thread)...", shp.getAbsolutePath());
            long[] counts = ingestStreaming(shp);
            jdbc.update("INSERT INTO system_config(key, value) VALUES ('shp_loaded', 'true') "
                    + "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value");
            jdbc.execute("ANALYZE road_nodes");
            jdbc.execute("ANALYZE road_edges");
            routingService.invalidateGraph();
            loadStatus.finishLoading((int) counts[0], (int) counts[1]);
            log.info("Road network ingest complete ({} nodes, {} edges).", counts[0], counts[1]);
        } catch (Exception e) {
            log.error("Road network ingest failed: {}", e.getMessage(), e);
            loadStatus.failLoading(e.getMessage());
        }
    }

    private boolean isLoaded() {
        List<String> v = jdbc.query("SELECT value FROM system_config WHERE key = 'shp_loaded'",
                (rs, i) -> rs.getString("value"));
        return !v.isEmpty() && "true".equalsIgnoreCase(v.get(0));
    }

    private void clearRoadData() {
        jdbc.execute("TRUNCATE road_edges, road_nodes RESTART IDENTITY CASCADE");
        jdbc.execute("DELETE FROM system_config WHERE key = 'shp_loaded'");
        routingService.invalidateGraph();
    }

    private long countNodes() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM road_nodes", Long.class);
        return n == null ? 0 : n;
    }

    private long countEdges() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM road_edges", Long.class);
        return n == null ? 0 : n;
    }

    private void ensureCoordKeyColumn() {
        jdbc.execute("ALTER TABLE road_nodes ADD COLUMN IF NOT EXISTS coord_key TEXT");
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_road_nodes_coord_key ON road_nodes(coord_key)");
    }

    private long[] ingestStreaming(File shp) throws Exception {
        ensureCoordKeyColumn();

        Map<String, Object> params = new HashMap<>();
        params.put("url", shp.toURI().toURL());

        DataStore store = DataStoreFinder.getDataStore(params);
        if (store == null) {
            throw new IllegalStateException("No GeoTools DataStore could read " + shp);
        }

        NodeIdResolver nodes = new NodeIdResolver(jdbc, nodeCacheSize);
        EdgeBatcher edges = new EdgeBatcher(jdbc, EDGE_BATCH_SIZE);
        long featureCount = 0;

        try {
            String typeName = store.getTypeNames()[0];
            SimpleFeatureSource featureSource = store.getFeatureSource(typeName);
            SimpleFeatureCollection collection = featureSource.getFeatures();

            try (SimpleFeatureIterator it = collection.features()) {
                while (it.hasNext()) {
                    SimpleFeature feature = it.next();
                    featureCount++;
                    Object geomObj = feature.getDefaultGeometry();
                    if (!(geomObj instanceof Geometry geometry)) {
                        continue;
                    }
                    String roadName = stringAttr(feature, attrName);
                    String fclass = stringAttr(feature, attrFclass);
                    boolean oneway = parseOneway(stringAttr(feature, attrOneway));
                    double speed = speedForClass(fclass);

                    for (int g = 0; g < geometry.getNumGeometries(); g++) {
                        Geometry part = geometry.getGeometryN(g);
                        if (part instanceof LineString ls && ls.getNumPoints() >= 2) {
                            buildSegments(ls, roadName, fclass, oneway, speed, nodes, edges);
                        }
                    }

                    if (featureCount % PROGRESS_EVERY_FEATURES == 0) {
                        log.info("Ingest progress: {} features, {} nodes, {} edges written…",
                                featureCount, nodes.insertedCount(), edges.writtenCount());
                    }
                }
            }
        } finally {
            store.dispose();
        }

        edges.flush();
        resetSequences();
        return new long[]{countNodes(), edges.writtenCount()};
    }

    private void buildSegments(LineString ls, String roadName, String fclass,
                               boolean oneway, double speed,
                               NodeIdResolver nodes, EdgeBatcher edges) {
        Coordinate[] coords = ls.getCoordinates();
        double speedMs = Math.max(speed, 1) / 3.6;

        for (int i = 0; i < coords.length - 1; i++) {
            Coordinate a = coords[i];
            Coordinate b = coords[i + 1];
            if (a.equals2D(b)) {
                continue;
            }

            long sourceId = nodes.resolve(a.x, a.y);
            long targetId = nodes.resolve(b.x, b.y);
            if (sourceId == targetId) {
                continue;
            }

            double lengthM = GeoUtils.haversineMeters(a.y, a.x, b.y, b.x);
            double cost = lengthM / speedMs;
            String wkt = segmentWkt(a.x, a.y, b.x, b.y);

            edges.add(sourceId, targetId, wkt, roadName, fclass, lengthM, speed, oneway, cost);
        }
    }

    private static String segmentWkt(double x1, double y1, double x2, double y2) {
        return "LINESTRING(" + x1 + " " + y1 + "," + x2 + " " + y2 + ")";
    }

    private double snap(double v) {
        return Math.round(v / DEDUP_DEGREES) * DEDUP_DEGREES;
    }

    private String coordKey(double x, double y) {
        double sx = snap(x);
        double sy = snap(y);
        return sx + "," + sy;
    }

    private void resetSequences() {
        jdbc.execute("SELECT setval(pg_get_serial_sequence('road_nodes','id'), "
                + "GREATEST((SELECT COALESCE(MAX(id),1) FROM road_nodes), 1))");
        jdbc.execute("SELECT setval(pg_get_serial_sequence('road_edges','id'), "
                + "GREATEST((SELECT COALESCE(MAX(id),1) FROM road_edges), 1))");
    }

    private String stringAttr(SimpleFeature f, String attr) {
        try {
            Object v = f.getAttribute(attr);
            return v == null ? null : v.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean parseOneway(String v) {
        if (v == null || v.isBlank()) {
            return false;
        }
        String s = v.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "T", "TRUE", "YES", "1", "Y" -> true;
            case "F", "FALSE", "NO", "0", "N", "B" -> false;
            default -> false;
        };
    }

    private double speedForClass(String fclass) {
        if (fclass == null) {
            return 50;
        }
        return switch (fclass.toLowerCase(Locale.ROOT)) {
            case "motorway", "motorway_link" -> 110;
            case "trunk", "trunk_link" -> 100;
            case "primary", "primary_link" -> 80;
            case "secondary", "secondary_link" -> 60;
            case "tertiary", "tertiary_link" -> 50;
            case "residential", "living_street", "unclassified" -> 40;
            case "service" -> 30;
            case "track", "path" -> 20;
            default -> 50;
        };
    }

    /** LRU cache + PostGIS upsert for coordinate → node id (bounded heap use). */
    private final class NodeIdResolver {
        private final JdbcTemplate jdbc;
        private final Map<String, Long> cache;
        private long inserted;

        NodeIdResolver(JdbcTemplate jdbc, int maxCache) {
            this.jdbc = jdbc;
            this.cache = new LinkedHashMap<>(maxCache, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > maxCache;
                }
            };
        }

        long resolve(double x, double y) {
            String key = coordKey(x, y);
            Long cached = cache.get(key);
            if (cached != null) {
                return cached;
            }

            double sx = snap(x);
            double sy = snap(y);
            List<Long> ids = jdbc.query(
                    "INSERT INTO road_nodes (coord_key, geom) "
                            + "VALUES (?, ST_SetSRID(ST_MakePoint(?, ?), 4326)) "
                            + "ON CONFLICT (coord_key) DO NOTHING RETURNING id",
                    (rs, i) -> rs.getLong("id"),
                    key, sx, sy);

            long id;
            if (!ids.isEmpty()) {
                id = ids.get(0);
                inserted++;
            } else {
                id = jdbc.queryForObject("SELECT id FROM road_nodes WHERE coord_key = ?", Long.class, key);
            }
            cache.put(key, id);
            return id;
        }

        long insertedCount() {
            return inserted;
        }
    }

    /** Buffers edge rows and flushes to PostGIS in fixed-size batches. */
    private static final class EdgeBatcher {
        private final JdbcTemplate jdbc;
        private final int batchSize;
        private final List<Object[]> batch = new ArrayList<>();
        private long written;

        private static final String SQL = "INSERT INTO road_edges"
                + "(source_id, target_id, geom, road_name, road_type, length_m, speed_kmh, oneway, cost) "
                + "VALUES (?, ?, ST_GeomFromText(?, 4326), ?, ?, ?, ?, ?, ?)";

        EdgeBatcher(JdbcTemplate jdbc, int batchSize) {
            this.jdbc = jdbc;
            this.batchSize = batchSize;
        }

        void add(long sourceId, long targetId, String wkt, String roadName, String fclass,
                 double lengthM, double speed, boolean oneway, double cost) {
            batch.add(new Object[]{sourceId, targetId, wkt, roadName, fclass,
                    lengthM, speed, oneway, cost});
            if (batch.size() >= batchSize) {
                flush();
            }
        }

        void flush() {
            if (batch.isEmpty()) {
                return;
            }
            jdbc.batchUpdate(SQL, batch);
            written += batch.size();
            batch.clear();
        }

        long writtenCount() {
            return written;
        }
    }
}
