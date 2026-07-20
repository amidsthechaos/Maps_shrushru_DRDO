# GeoRoute — Pan-India Offline Map & Routing Webapp

A fully **offline** geospatial routing application:

- Renders a local GeoTIFF basemap as XYZ PNG tiles (rendered by Spring Boot).
- Loads a pan-India road network from a local Shapefile into PostGIS on startup.
- Snaps clicks to the nearest road using PostGIS spatial queries.
- Computes the shortest path + N alternates using **A\*** *or* **Dijkstra**
  (user-selectable), with **Yen's K-Shortest Paths** for alternates — all
  hand-written in Java, no routing libraries.

After a one-time online setup, the app runs with **zero internet access**: no CDN,
no external tile servers, no external APIs, self-hosted fonts.

```
Angular 17 + OpenLayers (:4200)  ──/api/* proxy──►  Spring Boot (:8080)
                                                      ├─ JDBC ──► PostgreSQL + PostGIS
                                                      ├─ GeoTools ──► GeoTIFF basemap
                                                      └─ GeoTools ──► Roads Shapefile (startup ingest)
```

---

## 1. Prerequisites (install once)

| Tool                 | Version  | Notes                                                            |
|----------------------|----------|------------------------------------------------------------------|
| JDK                  | **17**   | GeoTools 29.2 / Hibernate-Spatial 6.x target Java 17. Newer JDKs (e.g. 21+) may break the GIS stack. |
| Maven                | 3.8+     | `mvn`                                                            |
| Node.js              | 18 / 20  | for Angular                                                     |
| Angular CLI          | 17       | `npm install -g @angular/cli@17`                                |
| PostgreSQL           | 15+      | with the **PostGIS** extension                                  |
| GDAL                 | any      | only for preparing the GeoTIFF (optional)                       |

> **JDK 17 note:** if your default `java` is a newer version, install JDK 17 and point
> the backend build at it, e.g. set `JAVA_HOME` to the JDK 17 install before running Maven.

## 2. One-time setup (online)

```bash
# Linux / macOS
./setup.sh

# Windows (PowerShell or cmd)
setup.bat
```

This installs the Angular CLI, runs `npm install` (including OpenLayers), downloads the
self-hosted fonts, and caches all Maven dependencies (`mvn dependency:go-offline`).

## 3. Add GIS data

Download the GeoTIFF basemap and road Shapefile into `data/` — see
[`data/README.md`](data/README.md).

## 4. Create the database

```bash
createdb georoute
psql -d georoute -f db/init.sql
```

Then set your DB password in
[`georoute-backend/src/main/resources/application.properties`](georoute-backend/src/main/resources/application.properties).

## 5. Run

```bash
# Backend (offline build + run). Ensure JAVA_HOME points to JDK 17.
cd georoute-backend
mvn install --offline
mvn spring-boot:run        # serves on http://localhost:8080
                           # first run ingests the Shapefile into PostGIS (idempotent)

# Frontend (separate terminal)
cd georoute-frontend
npm start                  # ng serve on http://localhost:4200 (proxies /api → :8080)
```

Open http://localhost:4200.

---

## 6. Portable offline package (no npm on the target PC)

For air-gapped / demo machines that **cannot** run `npm start`, build a single fat JAR that
also serves the Angular UI on port **8080**.

### On a machine with internet (this PC)

```bat
package-offline.bat
```

That script:

1. Builds the Angular production UI
2. Embeds it into a Spring Boot fat JAR (`-Poffline-bundle`)
3. Assembles `offline-bundle/` with the JAR, `application.properties` (placeholder DB password),
   `db/init.sql`, optional GIS files from `data/`, and `run-offline.bat`

Zip and copy **`offline-bundle/`** to the offline PC.

### On the offline / target machine

Needs only **JDK 17** + **PostgreSQL with PostGIS** (no Node, no Maven):

```bat
createdb georoute
psql -d georoute -f db\init.sql
REM edit application.properties → set DB password + GIS paths if needed
run-offline.bat
```

Open **http://localhost:8080** (UI + API together).

> First backend start still ingests the Shapefile into PostGIS (can take a long time for
> pan-India). Keep `shapefile.force-reload=false` after that.

---

## Usage

1. The India basemap renders from the local GeoTIFF.
2. Pick the routing algorithm (A\* or Dijkstra) and the number of routes in the sidebar.
3. Click the map to set the origin → it snaps to the nearest road (green pin).
4. Click again to set the destination (orange pin).
5. Click **Find Routes** → the shortest path + alternates are drawn in distinct colors,
   with distance and estimated time listed in the sidebar.
6. **Clear Map** resets everything.

## REST API

```
POST /api/route   { sourceLat, sourceLng, destLat, destLng, numAlternatePaths, algorithm }
POST /api/snap    { lat, lng }
GET  /api/tiles/{z}/{x}/{y}.png
GET  /api/health
```

`algorithm` is `"astar"` (default) or `"dijkstra"`.

## Offline guarantees

- No `https://` script/link tags in `index.html`.
- No `@import url('https://...')` — fonts are self-hosted `.woff2`.
- Tile source is `/api/tiles/...` (same host as the UI) — no external tile servers.
- No outbound HTTP from the backend at runtime.
- Builds work offline after setup: `mvn install --offline` and `ng build` from local `node_modules`.
- **Portable mode:** `package-offline.bat` → ship `offline-bundle/` → `run-offline.bat` (Java + PostGIS only).

## Notes & troubleshooting

- **JDK 17 is required for the backend.** GeoTools 29.2 and Hibernate-Spatial 6.x target
  Java 17; very new JDKs can break the GIS stack. Point `JAVA_HOME` at a JDK 17 before
  running Maven.
- **Routing algorithm.** A* (Haversine heuristic) and Dijkstra (uniform-cost) share the
  same search core and return the identical optimal path; alternates use Yen's K-Shortest
  Paths wrapping the selected engine. Choose the engine in the sidebar or via the
  `algorithm` field of `POST /api/route`.
- **Shapefile attributes.** Ingestion reads `fclass` / `name` / `oneway` (Geofabrik OSM
  naming). If your dataset differs, change `shapefile.attr.*` in `application.properties`.
- **Tiles.** XYZ (Web Mercator) tile indices are converted to a WGS84 bbox and the GeoTIFF
  is resampled to 256x256. If your basemap looks misaligned, confirm it is reprojected to
  EPSG:4326 (`gdalwarp -t_srs EPSG:4326`). Rendered tiles are cached under
  `georoute-backend/tile-cache/`; delete it to force a re-render.
- The first backend start ingests the Shapefile (can take a while for large extracts) and
  records `shp_loaded=true` in `system_config` so subsequent starts skip it.

## Project layout

```
.
├── package-offline.bat             # build portable offline-bundle/
├── scripts/package-offline.ps1     # packaging details
├── setup.sh / setup.bat            # one-time online setup
├── db/init.sql                     # PostGIS schema
├── data/                           # GIS files (gitignored)
├── georoute-backend/               # Spring Boot 3 + GeoTools + PostGIS
└── georoute-frontend/              # Angular 17 + OpenLayers
```



/frontened
/node_modules
and
/.angular
/dist

/backend
-/target

https://drive.google.com/drive/folders/1F2wLBxkzMldFqrVnhRzaaI0ouerHVOCD?usp=drive_link
