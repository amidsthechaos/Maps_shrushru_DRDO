-- GeoRoute PostGIS schema.
-- Run once against a fresh database:
--   createdb georoute
--   psql -d georoute -f db/init.sql

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- ─── Key/value config (used for idempotent data loading) ────────────────────
CREATE TABLE IF NOT EXISTS system_config (
    key   TEXT PRIMARY KEY,
    value TEXT
);

-- ─── Road graph nodes ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS road_nodes (
    id        BIGSERIAL PRIMARY KEY,
    coord_key TEXT UNIQUE,
    geom      GEOMETRY(Point, 4326) NOT NULL,
    elevation FLOAT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_road_nodes_geom ON road_nodes USING GIST(geom);

-- ─── Road graph edges ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS road_edges (
    id          BIGSERIAL PRIMARY KEY,
    source_id   BIGINT NOT NULL REFERENCES road_nodes(id),
    target_id   BIGINT NOT NULL REFERENCES road_nodes(id),
    geom        GEOMETRY(LineString, 4326) NOT NULL,
    road_name   TEXT,
    road_type   TEXT,
    length_m    FLOAT NOT NULL,
    speed_kmh   FLOAT DEFAULT 50,
    oneway      BOOLEAN DEFAULT FALSE,
    cost        FLOAT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_road_edges_geom   ON road_edges USING GIST(geom);
CREATE INDEX IF NOT EXISTS idx_road_edges_source ON road_edges(source_id);
CREATE INDEX IF NOT EXISTS idx_road_edges_target ON road_edges(target_id);
