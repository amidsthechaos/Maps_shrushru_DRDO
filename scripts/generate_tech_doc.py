"""
Generate an expanded GeoRoute technical documentation PDF.
Requires: pip install fpdf2
Run:  python scripts/generate_tech_doc.py
"""
from pathlib import Path

from fpdf import FPDF

OUT = Path(__file__).resolve().parent.parent / "GeoRoute_Technical_Documentation.pdf"


class Doc(FPDF):
    def header(self):
        if self.page_no() == 1:
            return
        self.set_font("Helvetica", "I", 8)
        self.set_text_color(100, 100, 100)
        self.cell(95, 8, "GeoRoute - Technical Documentation (Detailed)", align="L")
        self.cell(95, 8, f"Page {self.page_no()}", align="R", new_x="LMARGIN", new_y="NEXT")
        self.set_draw_color(180, 180, 180)
        self.line(10, self.get_y(), 200, self.get_y())
        self.ln(4)
        self.set_text_color(0, 0, 0)

    def footer(self):
        self.set_y(-12)
        self.set_font("Helvetica", "I", 7)
        self.set_text_color(120, 120, 120)
        self.cell(
            0, 8,
            "Offline pan-India map & routing  |  Angular 17 + Spring Boot 3 + PostGIS + GeoTools",
            align="C",
        )

    def body(self, text):
        self.set_x(self.l_margin)
        self.set_font("Helvetica", "", 9)
        self.multi_cell(0, 5, text)
        self.ln(1)

    def bullet(self, text, indent=8):
        self.set_font("Helvetica", "", 9)
        self.set_x(self.l_margin + indent)
        self.multi_cell(self.w - self.r_margin - self.l_margin - indent, 5, f"-  {text}")

    def mono(self, text):
        self.set_x(self.l_margin)
        self.set_font("Courier", "", 7.5)
        self.set_fill_color(245, 247, 250)
        self.multi_cell(0, 4.2, text, fill=True)
        self.ln(2)

    def h1(self, text):
        if self.get_y() > 250:
            self.add_page()
        self.set_x(self.l_margin)
        self.set_font("Helvetica", "B", 15)
        self.set_text_color(20, 60, 110)
        self.ln(3)
        self.multi_cell(0, 8, text)
        self.set_draw_color(20, 60, 110)
        self.line(10, self.get_y(), 200, self.get_y())
        self.ln(3)
        self.set_text_color(0, 0, 0)

    def h2(self, text):
        if self.get_y() > 260:
            self.add_page()
        self.set_x(self.l_margin)
        self.set_font("Helvetica", "B", 11)
        self.set_text_color(30, 70, 120)
        self.ln(2)
        self.multi_cell(0, 6.5, text)
        self.ln(1)
        self.set_text_color(0, 0, 0)

    def h3(self, text):
        self.set_x(self.l_margin)
        self.set_font("Helvetica", "B", 10)
        self.set_text_color(40, 40, 40)
        self.ln(1.5)
        self.multi_cell(0, 5.5, text)
        self.set_text_color(0, 0, 0)

    def table(self, headers, rows, col_widths=None):
        if col_widths is None:
            w = 190 / len(headers)
            col_widths = [w] * len(headers)
        self.set_font("Helvetica", "B", 7.5)
        self.set_fill_color(30, 70, 120)
        self.set_text_color(255, 255, 255)
        for i, h in enumerate(headers):
            self.cell(col_widths[i], 6, str(h)[:48], border=1, fill=True)
        self.ln()
        self.set_text_color(0, 0, 0)
        self.set_font("Helvetica", "", 7)
        fill = False
        for row in rows:
            if self.get_y() > 275:
                self.add_page()
                self.set_font("Helvetica", "B", 7.5)
                self.set_fill_color(30, 70, 120)
                self.set_text_color(255, 255, 255)
                for i, h in enumerate(headers):
                    self.cell(col_widths[i], 6, str(h)[:48], border=1, fill=True)
                self.ln()
                self.set_text_color(0, 0, 0)
                self.set_font("Helvetica", "", 7)
            if fill:
                self.set_fill_color(240, 244, 248)
            else:
                self.set_fill_color(255, 255, 255)
            # Estimate row height from longest cell wrap needs — keep single line truncated
            max_len = max(1, int(min(col_widths) / 1.6))
            for i, cell in enumerate(row):
                text = str(cell).replace("\n", " ")
                limit = max(20, int(col_widths[i] / 1.55))
                if len(text) > limit:
                    text = text[: limit - 3] + "..."
                self.cell(col_widths[i], 5.5, text, border=1, fill=True)
            self.ln()
            fill = not fill
        self.ln(2)


def box(pdf, x, y, w, h, label, fill=(230, 240, 255)):
    pdf.set_fill_color(*fill)
    pdf.set_draw_color(30, 70, 120)
    pdf.set_line_width(0.35)
    pdf.rect(x, y, w, h, style="DF")
    pdf.set_xy(x + 1, y + 1.5)
    pdf.set_font("Helvetica", "B", 6.5)
    pdf.set_text_color(20, 40, 80)
    pdf.multi_cell(w - 2, 3.2, label, align="C")
    pdf.set_text_color(0, 0, 0)
    pdf.set_x(pdf.l_margin)


def arrow_down(pdf, x, y, length=7):
    pdf.set_draw_color(60, 60, 60)
    pdf.set_line_width(0.45)
    pdf.line(x, y, x, y + length)
    pdf.line(x, y + length, x - 1.8, y + length - 2.5)
    pdf.line(x, y + length, x + 1.8, y + length - 2.5)


def draw_system_flowchart(pdf):
    pdf.h2("7.1 End-to-end interactive workflow")
    pdf.body(
        "Primary user path: map click to snap, then Find Routes, then GeoJSON overlay on OpenLayers."
    )
    start_y = pdf.get_y() + 1
    if start_y > 195:
        pdf.add_page()
        start_y = pdf.get_y() + 1
    cx, bw, bh = 105, 78, 11
    steps = [
        ("User clicks map (OpenLayers)", (220, 235, 250)),
        ("POST /api/snap  {lat, lng}", (210, 228, 245)),
        ("RoadSnapService: GIST bbox + KNN", (200, 220, 240)),
        ("Pin placed (source or dest nodeId)", (190, 230, 210)),
        ("User clicks Find Routes", (220, 235, 250)),
        ("POST /api/route (re-snap + RoutingService)", (210, 228, 245)),
        ("Load corridor subgraph (+ hierarchy if long)", (200, 220, 240)),
        ("A*/Dijkstra + edge-penalty alternates", (200, 220, 240)),
        ("GeoJSON FeatureCollection returned", (190, 230, 210)),
        ("Map draws routes (colors / dashes)", (220, 235, 250)),
    ]
    y = start_y
    for i, (label, color) in enumerate(steps):
        if y + bh + 12 > 280:
            pdf.add_page()
            y = pdf.get_y() + 2
        box(pdf, cx - bw / 2, y, bw, bh, label, fill=color)
        if i < len(steps) - 1:
            arrow_down(pdf, cx, y + bh + 0.4, 5.5)
            y = y + bh + 7
        else:
            y = y + bh + 3
    pdf.set_y(y + 2)


def draw_architecture_diagram(pdf):
    pdf.h2("7.2 Layered architecture")
    y = pdf.get_y() + 1
    if y > 210:
        pdf.add_page()
        y = pdf.get_y() + 1
    layers = [
        (10, y, 190, 16,
         "Frontend - Angular 17 + OpenLayers\nMapComponent | RoutePanel | SnapService | RouteService | MapStateService",
         (235, 245, 255)),
        (10, y + 24, 190, 16,
         "API - Spring Boot Controllers (:8080)\nSnapController | RouteController | TileController | HealthController | SpaWebConfig",
         (225, 238, 250)),
        (10, y + 48, 190, 18,
         "Domain services\nDataLoaderService | RoadSnapService | RoutingService | GeoTiffTileService | RoadGraph",
         (215, 230, 245)),
        (10, y + 76, 92, 18, "PostgreSQL + PostGIS\nroad_nodes | road_edges | system_config", (230, 245, 230)),
        (108, y + 76, 92, 18, "Local GIS files\nRoads Shapefile | GeoTIFF basemap", (255, 245, 230)),
    ]
    for x, yy, w, h, label, fill in layers:
        box(pdf, x, yy, w, h, label, fill=fill)
    for ay in [y + 16, y + 40, y + 66]:
        arrow_down(pdf, 105, ay + 1, 6)
    pdf.set_y(y + 100)


def build():
    pdf = Doc(orientation="P", unit="mm", format="A4")
    pdf.set_auto_page_break(auto=True, margin=14)
    pdf.add_page()

    # Title
    pdf.set_y(42)
    pdf.set_font("Helvetica", "B", 26)
    pdf.set_text_color(20, 60, 110)
    pdf.cell(0, 12, "GeoRoute", align="C", new_x="LMARGIN", new_y="NEXT")
    pdf.set_font("Helvetica", "", 13)
    pdf.cell(0, 7, "Pan-India Offline Map & Routing Webapp", align="C", new_x="LMARGIN", new_y="NEXT")
    pdf.ln(4)
    pdf.set_font("Helvetica", "B", 12)
    pdf.cell(0, 7, "Detailed Technical Documentation", align="C", new_x="LMARGIN", new_y="NEXT")
    pdf.ln(8)
    pdf.set_draw_color(20, 60, 110)
    pdf.line(55, pdf.get_y(), 155, pdf.get_y())
    pdf.ln(10)
    pdf.set_font("Helvetica", "", 9)
    pdf.set_text_color(60, 60, 60)
    for line in [
        "Architecture, data model, ingest, snap, routing algorithms,",
        "basemap tiles, REST APIs, frontend, and offline portable packaging.",
        "",
        "Stack: Angular 17 + OpenLayers  |  Spring Boot 3.2  |  JDK 17",
        "       PostgreSQL + PostGIS  |  GeoTools 29.2",
        "",
        "This document supersedes the shorter overview PDF with operational detail",
        "of how the system behaves at country / city scale.",
    ]:
        pdf.cell(0, 5, line, align="C", new_x="LMARGIN", new_y="NEXT")

    # TOC
    pdf.add_page()
    pdf.h1("Table of contents")
    toc = [
        "1. Project overview and design goals",
        "2. Technology stack",
        "3. Repository layout",
        "4. Database schema (PostGIS)",
        "5. Backend components (detailed)",
        "6. Algorithms in depth (ingest, snap, route, tiles)",
        "7. Flowcharts and architecture diagrams",
        "8. Frontend components and UX flow",
        "9. REST API contracts",
        "10. Configuration reference",
        "11. Dev vs portable offline package",
        "12. Known pitfalls and performance notes",
        "13. Summary",
    ]
    for t in toc:
        pdf.bullet(t, indent=4)

    # 1
    pdf.h1("1. Project overview and design goals")
    pdf.body(
        "GeoRoute is an offline-capable geospatial routing application. It loads a local road "
        "Shapefile into PostGIS, optionally renders a local GeoTIFF as XYZ map tiles, snaps "
        "map clicks to the nearest road, and computes one or more routes using hand-written "
        "A* or Dijkstra search (no commercial routing engines, no cloud map APIs)."
    )
    pdf.h3("Design goals")
    pdf.bullet("Runtime offline: zero outbound HTTP for maps, routing, fonts, or tiles.")
    pdf.bullet("Scale: handle multi-million edge OSM extracts (ingest streaming; query by bbox).")
    pdf.bullet("Usability: OpenLayers map, algorithm toggle, K alternates, snap pins, toasts.")
    pdf.bullet("Portability: prebuild a fat JAR that serves UI + API on one port (no npm on target).")
    pdf.h3("Runtime topology (development)")
    pdf.mono(
        "Browser  ->  Angular ng serve (:4200)\n"
        "                |  proxy /api/*\n"
        "                v\n"
        "             Spring Boot (:8080)\n"
        "                |-- JDBC -----> PostgreSQL + PostGIS\n"
        "                |-- GeoTools -> roads Shapefile (startup ingest)\n"
        "                +-- GeoTools -> GeoTIFF (tile render)"
    )
    pdf.h3("Runtime topology (portable offline-bundle)")
    pdf.mono(
        "Browser  ->  http://localhost:8080/\n"
        "                |  static Angular from JAR classpath:/static/\n"
        "                |  /api/*  Spring controllers\n"
        "                v\n"
        "             java -jar georoute-backend-1.0.0.jar\n"
        "                +-- same PostGIS + local data/ files"
    )

    # 2
    pdf.h1("2. Technology stack")
    pdf.table(
        ["Layer", "Technology", "Role"],
        [
            ["Frontend", "Angular 17, OpenLayers, TS", "Map UI, pins, routes, sidebar"],
            ["Backend", "Spring Boot 3.2, Java 17", "REST, ingest, snap, route, tiles"],
            ["GIS libs", "GeoTools 29.2, JTS", "Shapefile + GeoTIFF processing"],
            ["Database", "PostgreSQL + PostGIS", "Spatial graph + GIST indexes"],
            ["Build", "Maven, npm / Angular CLI", "Dev builds; offline-bundle packing"],
            ["Package", "Spring Boot fat JAR + SPA", "Air-gapped single-process deploy"],
        ],
        [28, 58, 104],
    )

    # 3
    pdf.h1("3. Repository layout")
    pdf.mono(
        "Maps_shrushru_DRDO/\n"
        "  db/init.sql                      PostGIS schema\n"
        "  data/                            GIS assets (gitignored): SHP, GeoTIFF\n"
        "  georoute-backend/                Spring Boot app\n"
        "    controller/                    REST\n"
        "    service/                       DataLoader, Snap, Routing, GeoTIFF\n"
        "    routing/                       RoadGraph (A*/Dijkstra/alternates)\n"
        "    repository/                    JDBC helpers\n"
        "    config/                        CORS, SpaWebConfig, PostGIS\n"
        "  georoute-frontend/               Angular app\n"
        "    components/ map, route-panel, toast\n"
        "    services/ snap, route, map-state\n"
        "  scripts/\n"
        "    package-offline.ps1            Build offline-bundle/\n"
        "    rebuild_road_basemap.py        RGB road basemap from PostGIS\n"
        "    generate_tech_doc.py           This PDF\n"
        "  package-offline.bat              Wrapper for packaging\n"
        "  offline-bundle/                  Generated portable folder (not committed)\n"
        "  setup.bat / setup.sh             One-time dependency install"
    )

    # 4
    pdf.h1("4. Database schema (PostGIS)")
    pdf.body(
        "Schema is owned by db/init.sql. Hibernate ddl-auto=none. Extensions: postgis, "
        "postgis_topology."
    )
    pdf.h2("4.1 Tables")
    pdf.table(
        ["Table", "Purpose", "Key columns / indexes"],
        [
            ["system_config", "Ingest flags", "key PK, value; shp_loaded=true"],
            ["road_nodes", "Graph vertices", "id, coord_key UNIQUE, geom Point4326, GIST(geom)"],
            ["road_edges", "Road segments", "source/target FKs, LineString, length_m, speed_kmh,"
                                           " oneway, cost; GIST(geom); FK indexes"],
        ],
        [34, 40, 116],
    )
    pdf.h2("4.2 Cost model")
    pdf.body(
        "Edge cost is travel time in seconds: length_m / (speed_kmh / 3.6). Speeds come from "
        "OSM fclass during ingest (motorway ~110 km/h down to tracks/paths ~20). Two-way roads "
        "are stored once; RoutingService expands them to bidirectional edges in memory. "
        "oneway=true keeps the forward direction only."
    )

    # 5
    pdf.h1("5. Backend components (detailed)")
    pdf.h2("5.1 Controllers")
    pdf.table(
        ["Class", "Endpoint", "Responsibility"],
        [
            ["SnapController", "POST /api/snap", "Snap lat/lng to nearest road node"],
            ["RouteController", "POST /api/route", "Re-snap endpoints; K routes; GeoJSON"],
            ["TileController", "GET /api/tiles/{z}/{x}/{y}.png", "256x256 basemap PNG; no-store"],
            ["HealthController", "GET /api/health", "PostGIS, ready flags, bounds, counts"],
        ],
        [38, 62, 90],
    )
    pdf.h2("5.2 DataLoaderService")
    pdf.body(
        "On ApplicationReadyEvent, starts daemon thread georoute-data-loader so Tomcat accepts "
        "HTTP while ingest runs. Streams Shapefile features with GeoTools (does not load the "
        "entire network into heap). Each LineString is split into consecutive vertex-to-vertex "
        "segments so intersections become shared nodes."
    )
    pdf.bullet("force-reload=true: TRUNCATE nodes/edges, clear shp_loaded, re-ingest once.")
    pdf.bullet("If shp_loaded already true: skip ingest and mark DataLoadStatus ready.")
    pdf.bullet("NodeIdResolver: LRU cache (shapefile.node-cache.size, default 300k) + upsert by coord_key.")
    pdf.bullet("EdgeBatcher: JDBC batch inserts (chunks ~2000).")
    pdf.bullet("Post-ingest: ANALYZE tables; set shp_loaded; DataLoadStatus.ready=true.")

    pdf.h2("5.3 RoadSnapService")
    pdf.body(
        "Snaps a WGS84 click to the nearest road within 1 km. A naive global KNN "
        "(ORDER BY geom <-> point) on 20M+ edges hits the 30s JDBC timeout. Strategy:"
    )
    pdf.bullet("Prefilter: e.geom && ST_Expand(point, 0.015 deg) (~1.5 km) using GIST.")
    pdf.bullet("Among candidates: ORDER BY geom <-> point LIMIT 1.")
    pdf.bullet("Snapped pin = ST_ClosestPoint; routing node = nearer of edge endpoints.")
    pdf.bullet("If distance > 1000 m or no hit: NoRoadNearbyException (404).")
    pdf.bullet("While ingest incomplete: DataNotReadyException (503).")

    pdf.h2("5.4 RoutingService")
    pdf.body(
        "Does not walk the national graph via per-neighbor JDBC (too slow). Loads a corridor "
        "subgraph around source and destination, builds an in-memory RoadGraph, searches locally."
    )
    pdf.bullet("Straight-line distance >= 12 km => longTrip (highway hierarchy).")
    pdf.bullet("Short pad: max(0.03 deg, span*0.25 + 0.015). Long pad: slimmer corridor caps.")
    pdf.bullet("Hierarchy: mid-corridor major types; all classes within ~1.2 km of endpoints.")
    pdf.bullet("Edge SQL does NOT join road_nodes (latency); nodes loaded in chunked IN lists.")
    pdf.bullet("PreparedStatement query timeout 90s for subgraph (global JDBC timeout remains 30s).")
    pdf.bullet("Cap: LIMIT 250000 undirected edges.")
    pdf.bullet("Retry: hierarchy miss -> all classes; short miss -> wider pad.")

    pdf.h2("5.5 RoadGraph")
    pdf.body(
        "In-memory directed adjacency. A* and Dijkstra share best-first search; difference is "
        "heuristic h(n): Haversine/maxSpeed (A*) vs 0 (Dijkstra). With an admissible heuristic, "
        "both return the same optimal path; A* expands fewer nodes when geometry is good."
    )
    pdf.h3("Alternate routes (not Yen)")
    pdf.body(
        "Classic Yen k-shortest often only detours one OSM segment on dense graphs. GeoRoute "
        "uses iterative edge-penalty diversification:"
    )
    pdf.bullet("Find shortest path.")
    pdf.bullet("Multiply cost of used edges (and reverse) by 2.5.")
    pdf.bullet("Re-search; reject duplicate signatures and Jaccard overlap > 0.55.")
    pdf.bullet("Repeat until K paths or maxAttempts (~ max(k*8, 8)). Sort by true cost.")
    pdf.bullet("Labels: index 0 'Fastest Route'; else 'Alternate i'.")

    pdf.h2("5.6 GeoTiffTileService")
    pdf.body(
        "XYZ (Web Mercator) indices -> WGS84 lon/lat box -> bilinear resample of coverage to "
        "256x256 -> PNG. Disk cache under tiles.cache.dir. Missing GeoTIFF or outside footprint "
        "=> transparent PNG so the UI still works."
    )
    pdf.bullet("RGB multi-band: drawRenderedImage.")
    pdf.bullet("Single-band/float: grayscale paint; near-constant fills stay translucent (avoids yellow map bug).")
    pdf.bullet("Recommended basemap: data/roads_basemap.tif (3-band RGB from rebuild_road_basemap.py).")

    pdf.h2("5.7 SpaWebConfig + CorsConfig")
    pdf.bullet("SpaWebConfig serves classpath:/static/ (packaged Angular); SPA fallback to index.html; never hijacks /api/*.")
    pdf.bullet("CorsConfig allows configured local origins for /api/** (dev :4200 and packaged :8080).")

    # 6
    pdf.h1("6. Algorithms in depth")
    pdf.h2("6.1 Ingest connectivity")
    pdf.body(
        "OSM lines often cross without sharing digitised vertices. Splitting each polyline into "
        "vertex-to-vertex segments and snapping coordinates to a fine coord_key grid "
        "(DEDUP_DEGREES ~ 1e-5) merges junctions so the graph is connected for routing."
    )
    pdf.h2("6.2 Snap complexity")
    pdf.body(
        "Without a GIST bbox, KNN may scan huge portions of road_edges. With ST_Expand + &&, "
        "the planner uses the spatial index. This is essential for pan-India datasets."
    )
    pdf.h2("6.3 Long-trip hierarchy rationale")
    pdf.body(
        "Loading every residential street inside a large S-T envelope can exceed timeouts. "
        "Hierarchy keeps arterial corridors for the mid-section and retains full connectivity "
        "near origin/destination so the path can enter/exit the major network."
    )
    pdf.h2("6.4 Basemap: roads_basemap vs broken rasterized.tif")
    pdf.body(
        "An earlier single-band float GeoTIFF filled entirely with value 255 rendered as near "
        "solid yellow (approx RGB 255,255,1) via GeoTools ColorModel + aggressive HTTP caching. "
        "rebuild_road_basemap.py burns PostGIS roads into an RGB dark-background TIFF. "
        "TileController uses Cache-Control: no-store; frontend tile URL includes ?v=roads1."
    )

    # 7 diagrams
    pdf.add_page()
    pdf.h1("7. Flowcharts and architecture diagrams")
    draw_system_flowchart(pdf)
    pdf.add_page()
    draw_architecture_diagram(pdf)

    pdf.h2("7.3 Startup / ingest sequence")
    pdf.mono(
        "Spring Boot starts -> HTTP :8080 up\n"
        "  DataLoaderService.scheduleLoad() [background]\n"
        "    force-reload? TRUNCATE + clear flag\n"
        "    shp_loaded? skip, mark ready\n"
        "    else stream SHP -> segments -> upsert nodes -> batch edges\n"
        "    ANALYZE; shp_loaded=true; DataLoadStatus.ready\n"
        "  GeoTiffTileService.init() loads geotiff.base.path if present\n"
        "  GET /api/health reflects dataReady + roadBounds"
    )
    pdf.h2("7.4 Routing sequence")
    pdf.mono(
        "RouteController: snap source + dest\n"
        "  RoutingService.route(nodeIds, k, algorithm)\n"
        "    distM = Haversine(S,T); longTrip = distM >= 12 km\n"
        "    loadSubgraph(pad, hierarchy=longTrip)\n"
        "    RoadGraph.kShortestPaths(...)\n"
        "    if empty: retry all-classes / wider pad\n"
        "    fetch geometries for used edges\n"
        "  GeoJson.fromRoutes -> FeatureCollection"
    )

    # 8
    pdf.h1("8. Frontend components and UX flow")
    pdf.table(
        ["Piece", "Role"],
        [
            ["MapComponent", "OL map; XYZ tiles; click->snap; pins; draw routes; fit roadBounds"],
            ["RoutePanelComponent", "A*/Dijkstra toggle; K=1..10; Find Routes; Clear; list metrics"],
            ["ToastComponent", "Transient status / errors"],
            ["MapStateService", "RxJS state: pins, routes, algorithm, k, flags, toast"],
            ["SnapService", "POST /api/snap (timeout 60s)"],
            ["RouteService", "POST /api/route (timeout 120s)"],
        ],
        [48, 142],
    )
    pdf.body(
        "UX: first click sets origin (green), second sets destination (orange); Find Routes "
        "draws the primary route solid and alternates dashed. Dev proxy.conf.json forwards "
        "/api to :8080. Production packaged mode uses same-origin relative /api URLs."
    )

    # 9
    pdf.h1("9. REST API contracts")
    pdf.h3("POST /api/snap")
    pdf.mono(
        'Request:  { "lat": number, "lng": number }\n'
        "Response: { snappedLat, snappedLng, nodeId, nearestRoadName, distanceToRoadM }\n"
        "Errors:   404 NO_ROAD_NEARBY | 503 DATA_NOT_READY | 504 SNAP_TIMEOUT"
    )
    pdf.h3("POST /api/route")
    pdf.mono(
        "Request:  { sourceLat, sourceLng, destLat, destLng,\n"
        "            numAlternatePaths, algorithm }\n"
        '          algorithm = "astar" | "dijkstra" (default astar)\n'
        "Response: GeoJSON FeatureCollection of LineStrings with\n"
        "          routeIndex, label, totalDistanceMeters, estimatedTimeMinutes\n"
        "Errors:   404 / 503 / 504 ROUTE_DB_ERROR / 500 ROUTE_FAILED"
    )
    pdf.h3("GET /api/health")
    pdf.mono(
        "status, postgis, dataLoaded, dataReady, dataLoading,\n"
        "nodeCount, edgeCount, message,\n"
        "roadBounds { minLng, minLat, maxLng, maxLat }"
    )
    pdf.h3("GET /api/tiles/{z}/{x}/{y}.png")
    pdf.body("image/png. Cache-Control: no-store, must-revalidate. Transparent outside footprint.")

    # 10
    pdf.h1("10. Configuration reference")
    pdf.body(
        "File: georoute-backend/src/main/resources/application.properties "
        "(offline-bundle overrides via external application.properties beside the JAR)."
    )
    pdf.table(
        ["Property", "Meaning"],
        [
            ["spring.datasource.*", "JDBC URL, user, password (never commit real secrets)"],
            ["spring.jpa.hibernate.ddl-auto", "none - schema from db/init.sql"],
            ["geotiff.base.path", "Basemap GeoTIFF (prefer roads_basemap.tif)"],
            ["shapefile.base.path", "Roads .shp path"],
            ["shapefile.force-reload", "true once to re-ingest, then false"],
            ["shapefile.node-cache.size", "LRU size during ingest (default 300000)"],
            ["shapefile.attr.*", "name / fclass / oneway attribute names"],
            ["spring.jdbc.template.query-timeout", "Global seconds (snap); subgraph uses 90"],
            ["tiles.cache.dir / enabled", "On-disk PNG tile cache"],
            ["routing.max.speed.kmh", "A* heuristic divisor (default 110)"],
            ["server.port", "Default 8080"],
            ["cors.allowed-origins", "Dev :4200 and packaged :8080 origins"],
        ],
        [70, 120],
    )

    # 11
    pdf.h1("11. Dev vs portable offline package")
    pdf.h2("11.1 Development")
    pdf.bullet("setup.bat once: npm install, fonts, mvn deps.")
    pdf.bullet("createdb georoute; psql -f db/init.sql; set DB password in properties.")
    pdf.bullet("mvn spring-boot:run  and  npm start  (UI :4200, API :8080).")
    pdf.h2("11.2 Building offline-bundle (on a connected PC)")
    pdf.bullet("Run package-offline.bat (scripts/package-offline.ps1).")
    pdf.bullet("Builds Angular production; mvn -Poffline-bundle package embeds dist into JAR.")
    pdf.bullet("Assembles offline-bundle/: JAR, application.properties (password PLACEHOLDER),")
    pdf.bullet("db/init.sql, optional data/*, run-offline.bat/ps1, README.")
    pdf.h2("11.3 Running on an air-gapped PC")
    pdf.bullet("Install JDK 17 + PostgreSQL with PostGIS only (no Node/Maven required).")
    pdf.bullet("createdb + init.sql; edit password in application.properties.")
    pdf.bullet("run-offline.bat -> open http://localhost:8080")
    pdf.bullet("First start may take a long time ingesting roads; later starts skip via shp_loaded.")
    pdf.body(
        "You can zip and copy the entire offline-bundle folder. Include or exclude heavy GIS "
        "files depending on transfer size; paths in application.properties must match."
    )

    # 12
    pdf.h1("12. Known pitfalls and performance notes")
    pdf.bullet("JDK: use 17 for GeoTools 29.2; newer JDKs can break the GIS stack.")
    pdf.bullet("Snap timeouts: usually need GIST + ST_Expand prefilter (already implemented).")
    pdf.bullet("Route timeouts on long trips: hierarchy + 90s subgraph timeout + no node-join.")
    pdf.bullet("Yellow map: bad single-band TIFF and/or 7-day browser tile cache (fixed with RGB + no-store + ?v=).")
    pdf.bullet("After changing GeoTIFF: delete tile-cache/ and hard-refresh browser.")
    pdf.bullet("After changing SHP parser: shapefile.force-reload=true once, then false.")
    pdf.bullet("Do not ship real DB passwords in zips; offline packer uses CHANGE_ME.")
    pdf.bullet("Frontend route timeout is 120s to cover long subgraph loads.")

    # 13
    pdf.h1("13. Summary")
    pdf.body(
        "GeoRoute cleanly separates UI (Angular/OpenLayers), spatial persistence (PostGIS), "
        "and graph search (in-memory RoadGraph on a corridor subgraph). Country-scale "
        "performance relies on streaming ingest, bbox-prefiltered snap, hierarchical long-trip "
        "loads with a 90s subgraph query budget, and edge-penalty diversification for "
        "meaningful alternate routes. Portable deployment collapses UI+API into one fat JAR "
        "so air-gapped machines need only Java and PostGIS."
    )
    pdf.ln(3)
    pdf.set_font("Helvetica", "I", 8)
    pdf.set_text_color(80, 80, 80)
    pdf.multi_cell(
        0, 5,
        "Generated by scripts/generate_tech_doc.py from the implemented codebase. "
        "For exact logic, see georoute-backend and georoute-frontend sources.",
    )

    OUT.parent.mkdir(parents=True, exist_ok=True)
    pdf.output(str(OUT))
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    build()
