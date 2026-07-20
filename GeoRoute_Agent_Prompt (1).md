# AI Agent Prompt — GeoRoute: Pan-India Map & Routing Webapp
### ⚠️ FULLY OFFLINE — Zero External API Calls, Zero CDN, Zero Internet at Runtime

---

## 🧭 Project Overview

You are a senior full-stack geospatial engineer. Your task is to build a production-grade web application called **GeoRoute** — a pan-India interactive mapping and routing webapp that runs **100% offline** after a one-time setup.

It renders a real satellite/natural-color basemap from a locally stored GeoTIFF file, overlays India's road network loaded from a local `.shp` Shapefile, lets users drop pins anywhere on the map, snaps them to the nearest road using PostGIS spatial queries, and computes the shortest path + N alternate paths using the **A\* (A-Star) algorithm** implemented in Java.

**The application must work with no internet connection whatsoever.** No external tile servers, no CDN-loaded JS libraries, no Google Fonts, no external geocoding APIs, no cloud services of any kind. Every byte — code, libraries, fonts, and map data — is served from localhost.

---

## 🔴 OFFLINE MANDATE (Read Before Writing Any Code)

These rules are absolute and override any default coding habits:

1. **NO CDN links** — Zero `<script src="https://...">` or `<link href="https://...">` in any HTML file. All JS/CSS must come from locally installed `node_modules/` and Angular CLI's build output.
2. **NO Google Fonts** — Zero `@import url('https://fonts.googleapis.com/...')` anywhere. Use system font stacks or self-host downloaded `.woff2` files under `frontend/src/assets/fonts/`.
3. **NO external tile servers** — No OpenStreetMap tile URLs (`tile.openstreetmap.org`), no Mapbox, no Stamen, no ESRI tiles. The only tile source is `http://localhost:8080/api/tiles/{z}/{x}/{y}.png`.
4. **NO external API calls from Java** — The Spring Boot backend must never make outbound HTTP calls at runtime. No REST clients hitting external URLs.
5. **NO npm install at runtime** — All `node_modules` must be installed during setup via `npm install`. The app runs with `ng serve` from local node_modules; no network fetches occur during serving.
6. **NO Maven downloads at runtime** — Run `mvn dependency:go-offline` once during setup. The app must build with `mvn install --offline` thereafter.
7. **NO placeholder or stub implementations** — If a library or font is referenced, it must be fully local and functional. Do not write `// TODO: replace with local copy`.

---

## 🛠️ Tech Stack (Non-Negotiable)

| Layer          | Technology                                                              |
|----------------|-------------------------------------------------------------------------|
| Frontend       | **Angular 17** (TypeScript) + OpenLayers 7.x — all via local npm       |
| Backend        | Java 17+ with Spring Boot 3.x                                           |
| Database       | PostgreSQL 15+ with **PostGIS** extension (local install)               |
| Map Basemap    | Natural color GeoTIFF — served as XYZ PNG tiles by Spring Boot          |
| Road Network   | Pan-India `.shp` Shapefile — loaded into PostGIS on startup             |
| Routing Engine | A\* algorithm + Yen's K-Shortest Paths — pure Java, no libraries        |
| Build Tools    | Maven (backend), Angular CLI (`ng`) — frontend                          |
| Language       | TypeScript (frontend), Java 17 (backend)                                |
| Fonts          | Self-hosted `.woff2` font files under `src/assets/fonts/`               |
| Dev Servers    | Spring Boot on :8080 (backend), `ng serve` on :4200 (frontend)          |

---

## 📦 One-Time Setup — Download Everything Before Going Offline

The agent must generate a `setup.sh` (Linux/Mac) and `setup.bat` (Windows) script that performs all downloads in one shot. The scripts must:

### Frontend — Install Angular CLI and All Packages

```bash
# Install Angular CLI globally (one time only)
npm install -g @angular/cli@17

# Scaffold project (run once)
ng new georoute-frontend \
  --routing=true \
  --style=scss \
  --skip-git \
  --skip-tests=false

cd georoute-frontend

# Install OpenLayers (map rendering — fully offline after install)
npm install ol@7.5.2

# Install TypeScript types for OpenLayers
npm install --save-dev @types/ol

# After this, node_modules/ contains everything.
# ng serve and ng build work fully offline from here.
```

### Self-Hosted Fonts (download once, serve locally)

```bash
# JetBrains Mono — open-source under OFL-1.1, from JetBrains GitHub releases
mkdir -p georoute-frontend/src/assets/fonts

curl -L https://github.com/JetBrains/JetBrainsMono/releases/download/v2.304/JetBrainsMono-2.304.zip \
     -o /tmp/jbmono.zip
unzip /tmp/jbmono.zip "fonts/webfonts/JetBrainsMono-Regular.woff2" \
                      "fonts/webfonts/JetBrainsMono-Medium.woff2" \
     -d /tmp/jbmono
cp /tmp/jbmono/fonts/webfonts/*.woff2 georoute-frontend/src/assets/fonts/

# Inter font — open-source under OFL-1.1, from rsms/inter GitHub releases
curl -L https://github.com/rsms/inter/releases/download/v4.0/Inter-4.0.zip \
     -o /tmp/inter.zip
unzip /tmp/inter.zip "Inter Desktop/Inter-Regular.woff2" -d /tmp/inter
cp "/tmp/inter/Inter Desktop/Inter-Regular.woff2" georoute-frontend/src/assets/fonts/
```

### Backend Maven Dependencies (cache locally)

```bash
cd georoute-backend
mvn dependency:go-offline   # Downloads all JARs into ~/.m2/repository
# After this, all builds use: mvn install --offline
```

### GIS Data Files (download separately — see Data Sources section at bottom)

```
data/
├── india_natural_color.tif     # GeoTIFF basemap
├── india_roads.shp             # Road network
├── india_roads.dbf
├── india_roads.shx
└── india_roads.prj
```

---

## 📁 Full Project Structure

```
georoute/
├── setup.sh                             # One-time online setup (Linux/Mac)
├── setup.bat                            # One-time online setup (Windows)
│
├── georoute-backend/                    # Spring Boot application
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/georoute/
│       │   ├── GeoRouteApplication.java
│       │   ├── controller/
│       │   │   ├── RouteController.java
│       │   │   ├── TileController.java      # GET /api/tiles/{z}/{x}/{y}.png
│       │   │   └── SnapController.java
│       │   ├── service/
│       │   │   ├── RoutingService.java      # A* + Yen's K-Shortest Paths
│       │   │   ├── RoadSnapService.java     # PostGIS nearest road snap
│       │   │   ├── GeoTiffTileService.java  # GeoTIFF → PNG tile renderer
│       │   │   └── DataLoaderService.java   # SHP → PostGIS ingestion on startup
│       │   ├── model/
│       │   │   ├── RoadNode.java
│       │   │   ├── RoadEdge.java
│       │   │   ├── RouteResult.java
│       │   │   └── SnapResult.java
│       │   ├── repository/
│       │   │   ├── RoadNodeRepository.java
│       │   │   └── RoadEdgeRepository.java
│       │   └── config/
│       │       ├── PostGISConfig.java
│       │       └── CorsConfig.java          # Allow localhost:4200 only
│       └── resources/
│           └── application.properties
│
├── georoute-frontend/                   # Angular 17 application
│   ├── angular.json                     # Angular CLI config (no CDN, local assets)
│   ├── tsconfig.json
│   ├── tsconfig.app.json
│   ├── package.json
│   └── src/
│       ├── main.ts
│       ├── index.html                   # Zero https:// script/link tags
│       ├── styles.scss                  # Global styles + @font-face (local .woff2)
│       ├── environments/
│       │   ├── environment.ts           # { apiBaseUrl: 'http://localhost:8080' }
│       │   └── environment.prod.ts
│       ├── assets/
│       │   └── fonts/
│       │       ├── JetBrainsMono-Regular.woff2   # Self-hosted
│       │       ├── JetBrainsMono-Medium.woff2    # Self-hosted
│       │       └── Inter-Regular.woff2           # Self-hosted
│       └── app/
│           ├── app.module.ts
│           ├── app.component.ts
│           ├── app.component.html
│           ├── app-routing.module.ts
│           ├── components/
│           │   ├── map/
│           │   │   ├── map.component.ts
│           │   │   ├── map.component.html
│           │   │   └── map.component.scss
│           │   ├── route-panel/
│           │   │   ├── route-panel.component.ts
│           │   │   ├── route-panel.component.html
│           │   │   └── route-panel.component.scss
│           │   └── toast/
│           │       ├── toast.component.ts
│           │       └── toast.component.html
│           ├── services/
│           │   ├── route.service.ts     # HttpClient → localhost:8080/api/route
│           │   ├── snap.service.ts      # HttpClient → localhost:8080/api/snap
│           │   └── map-state.service.ts # Shared state (pins, routes, loading)
│           └── models/
│               ├── route-result.model.ts
│               └── snap-result.model.ts
│
├── data/                                # GIS data — gitignored
│   ├── india_natural_color.tif
│   ├── india_roads.shp
│   ├── india_roads.dbf
│   ├── india_roads.shx
│   └── india_roads.prj
│
└── db/
    └── init.sql                         # PostGIS schema setup
```

---

## 🌐 Frontend — Angular 17 (TypeScript)

### index.html — Must Contain Zero External URLs

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>GeoRoute</title>
  <base href="/">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <!--
    ✅ Angular CLI injects bundle scripts here at build time — all local
    ❌ DO NOT add: <link href="https://fonts.googleapis.com/...">
    ❌ DO NOT add: <script src="https://cdnjs.cloudflare.com/...">
    ❌ DO NOT add: <link rel="stylesheet" href="https://...">
    Every asset is served from localhost via Angular CLI dev server or ng build output.
  -->
</head>
<body>
  <app-root></app-root>
</body>
</html>
```

### styles.scss — Self-Hosted Fonts via @font-face

```scss
/*
  ✅ CORRECT: fonts are local .woff2 files in src/assets/fonts/
  ❌ WRONG:   @import url('https://fonts.googleapis.com/css2?family=Inter');
*/

@font-face {
  font-family: 'JetBrains Mono';
  src: url('/assets/fonts/JetBrainsMono-Regular.woff2') format('woff2');
  font-weight: 400;
  font-style: normal;
  font-display: swap;
}

@font-face {
  font-family: 'JetBrains Mono';
  src: url('/assets/fonts/JetBrainsMono-Medium.woff2') format('woff2');
  font-weight: 500;
  font-style: normal;
  font-display: swap;
}

@font-face {
  font-family: 'Inter';
  src: url('/assets/fonts/Inter-Regular.woff2') format('woff2');
  font-weight: 400;
  font-style: normal;
  font-display: swap;
}

/* Global token variables */
:root {
  --bg:          #0D1117;
  --surface:     #161B22;
  --border:      #30363D;
  --route-0:     #2F81F7;   /* Primary — blue */
  --route-1:     #F78166;   /* Alternate 1 — orange */
  --route-2:     #A371F7;   /* Alternate 2 — purple */
  --route-3:     #3FB950;   /* Alternate 3 — green */
  --route-4:     #39D4C5;   /* Alternate 4+ — teal */
  --pin-source:  #3FB950;
  --pin-dest:    #F78166;
  --text:        #E6EDF3;
  --text-muted:  #8B949E;
  --error:       #FF6B6B;
  --font-mono:   'JetBrains Mono', 'Courier New', monospace;
  --font-body:   'Inter', system-ui, sans-serif;
}

* { box-sizing: border-box; margin: 0; padding: 0; }

body {
  background: var(--bg);
  color: var(--text);
  font-family: var(--font-body);
  height: 100vh;
  overflow: hidden;
}
```

### angular.json — Register Assets Folder (fonts are automatically included)

```json
{
  "projects": {
    "georoute-frontend": {
      "architect": {
        "build": {
          "options": {
            "styles": ["src/styles.scss"],
            "assets": [
              "src/favicon.ico",
              "src/assets"
            ],
            "allowedCommonJsDependencies": ["ol"]
          }
        },
        "serve": {
          "options": {
            "proxyConfig": "proxy.conf.json"
          }
        }
      }
    }
  }
}
```

### proxy.conf.json — Proxy /api calls to Spring Boot (localhost only)

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": false,
    "logLevel": "info"
  }
}
```

> With this proxy, Angular's `ng serve` on :4200 forwards all `/api/*` requests to Spring Boot on :8080. No CORS issues, no external URLs — everything stays on localhost.

### package.json

```json
{
  "name": "georoute-frontend",
  "version": "1.0.0",
  "private": true,
  "scripts": {
    "ng":      "ng",
    "start":   "ng serve --port 4200",
    "build":   "ng build --configuration production",
    "watch":   "ng build --watch --configuration development"
  },
  "dependencies": {
    "@angular/animations":   "^17.0.0",
    "@angular/common":       "^17.0.0",
    "@angular/compiler":     "^17.0.0",
    "@angular/core":         "^17.0.0",
    "@angular/forms":        "^17.0.0",
    "@angular/platform-browser": "^17.0.0",
    "@angular/platform-browser-dynamic": "^17.0.0",
    "@angular/router":       "^17.0.0",
    "rxjs":                  "~7.8.0",
    "tslib":                 "^2.6.0",
    "zone.js":               "~0.14.0",
    "ol":                    "7.5.2"
  },
  "devDependencies": {
    "@angular-devkit/build-angular": "^17.0.0",
    "@angular/cli":          "^17.0.0",
    "@angular/compiler-cli": "^17.0.0",
    "@types/ol":             "^7.5.6",
    "typescript":            "~5.2.0",
    "sass":                  "^1.69.0"
  }
}
```

### environments/environment.ts — All URLs Point to Localhost

```typescript
// ✅ CORRECT: localhost only — no external services
// ❌ WRONG:   apiBaseUrl: 'https://some-cloud-api.com'
export const environment = {
  production: false,
  apiBaseUrl: 'http://localhost:8080',   // Spring Boot backend
  tileUrl: '/api/tiles/{z}/{x}/{y}.png', // Proxied through ng serve → :8080
};
```

### models/route-result.model.ts

```typescript
export interface RouteResult {
  routeIndex:           number;
  label:                string;    // "Fastest Route" | "Alternate 1" | ...
  totalDistanceMeters:  number;
  estimatedTimeMinutes: number;
  coordinates:          [number, number][];  // [[lng, lat], ...] GeoJSON order
}

export interface RouteResponse {
  type:     'FeatureCollection';
  features: GeoJsonFeature[];
}

export interface GeoJsonFeature {
  type: 'Feature';
  properties: {
    routeIndex:           number;
    label:                string;
    totalDistanceMeters:  number;
    estimatedTimeMinutes: number;
  };
  geometry: {
    type:        'LineString';
    coordinates: [number, number][];
  };
}
```

### models/snap-result.model.ts

```typescript
export interface SnapResult {
  snappedLat:      number;
  snappedLng:      number;
  nodeId:          number;
  nearestRoadName: string;
  distanceToRoadM: number;
}

export interface SnapError {
  error:   'NO_ROAD_NEARBY';
  message: string;
}
```

### services/map-state.service.ts — Shared State with RxJS

```typescript
import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { SnapResult } from '../models/snap-result.model';
import { RouteResult } from '../models/route-result.model';

@Injectable({ providedIn: 'root' })
export class MapStateService {
  // Pin state
  sourcePin$ = new BehaviorSubject<SnapResult | null>(null);
  destPin$   = new BehaviorSubject<SnapResult | null>(null);

  // Route results
  routes$    = new BehaviorSubject<RouteResult[]>([]);

  // Loading states
  snapping$  = new BehaviorSubject<boolean>(false);
  routing$   = new BehaviorSubject<boolean>(false);

  // Toast messages
  toast$     = new BehaviorSubject<string | null>(null);

  // Number of alternate paths requested by user
  numAlternatePaths$ = new BehaviorSubject<number>(2);

  // Which pin is being placed next: 'source' | 'dest'
  pinMode$   = new BehaviorSubject<'source' | 'dest'>('source');

  showToast(message: string, durationMs = 3000): void {
    this.toast$.next(message);
    setTimeout(() => this.toast$.next(null), durationMs);
  }

  clearAll(): void {
    this.sourcePin$.next(null);
    this.destPin$.next(null);
    this.routes$.next([]);
    this.pinMode$.next('source');
  }
}
```

### services/snap.service.ts

```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { SnapResult } from '../models/snap-result.model';

@Injectable({ providedIn: 'root' })
export class SnapService {
  private url = `${environment.apiBaseUrl}/api/snap`;

  constructor(private http: HttpClient) {}

  // ✅ Calls localhost:8080 only — no external geocoding API
  snap(lat: number, lng: number): Observable<SnapResult> {
    return this.http.post<SnapResult>(this.url, { lat, lng });
  }
}
```

### services/route.service.ts

```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { RouteResponse } from '../models/route-result.model';

@Injectable({ providedIn: 'root' })
export class RouteService {
  private url = `${environment.apiBaseUrl}/api/route`;

  constructor(private http: HttpClient) {}

  findRoutes(
    sourceLat: number, sourceLng: number,
    destLat:   number, destLng:   number,
    numAlternatePaths: number
  ): Observable<RouteResponse> {
    return this.http.post<RouteResponse>(this.url, {
      sourceLat, sourceLng, destLat, destLng, numAlternatePaths
    });
  }
}
```

### components/map/map.component.ts

```typescript
import { Component, OnInit, OnDestroy, ElementRef, ViewChild } from '@angular/core';
import { Subscription } from 'rxjs';

// ✅ All OpenLayers imports from local node_modules — no CDN
import Map        from 'ol/Map';
import View       from 'ol/View';
import TileLayer  from 'ol/layer/Tile';
import XYZ        from 'ol/source/XYZ';
import VectorLayer  from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import Feature    from 'ol/Feature';
import Point      from 'ol/geom/Point';
import LineString from 'ol/geom/LineString';
import Style      from 'ol/style/Style';
import Stroke     from 'ol/style/Stroke';
import CircleStyle from 'ol/style/Circle';
import Fill       from 'ol/style/Fill';
import { fromLonLat, toLonLat } from 'ol/proj';
import { Coordinate } from 'ol/coordinate';

import { MapStateService }  from '../../services/map-state.service';
import { SnapService }      from '../../services/snap.service';
import { RouteResult }      from '../../models/route-result.model';

// Route colors matching CSS variables
const ROUTE_COLORS = ['#2F81F7', '#F78166', '#A371F7', '#3FB950', '#39D4C5'];
const ROUTE_DASHES: (number[] | undefined)[] = [
  undefined,          // primary: solid
  [8, 4],             // alt 1: long dash
  [6, 6],             // alt 2: medium dash
  [4, 8],             // alt 3: short dash
  [2, 6],             // alt 4+: dotted
];

@Component({
  selector: 'app-map',
  templateUrl: './map.component.html',
  styleUrls:  ['./map.component.scss']
})
export class MapComponent implements OnInit, OnDestroy {
  @ViewChild('mapContainer', { static: true }) mapContainer!: ElementRef;

  private map!: Map;
  private pinSource   = new VectorSource();
  private routeSource = new VectorSource();
  private subs = new Subscription();

  constructor(
    private mapState: MapStateService,
    private snapService: SnapService
  ) {}

  ngOnInit(): void {
    this.initMap();
    this.subscribeToRoutes();
    this.subscribeToPins();
  }

  private initMap(): void {
    this.map = new Map({
      target: this.mapContainer.nativeElement,
      layers: [
        // ✅ CORRECT: Local Spring Boot tile server on localhost
        // ❌ WRONG:   'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
        new TileLayer({
          source: new XYZ({
            url: '/api/tiles/{z}/{x}/{y}.png',  // proxied to :8080 by ng serve
            crossOrigin: 'anonymous',
            maxZoom: 18,
            minZoom: 4,
          })
        }),
        // Route lines layer
        new VectorLayer({
          source: this.routeSource,
          zIndex: 1,
        }),
        // Pin markers layer
        new VectorLayer({
          source: this.pinSource,
          zIndex: 2,
        }),
      ],
      view: new View({
        center: fromLonLat([78.9629, 20.5937]),  // India center
        zoom: 5,
      })
    });

    // Map click → snap to nearest road
    this.map.on('click', (event) => {
      const [lng, lat] = toLonLat(event.coordinate);
      this.handleMapClick(lat, lng);
    });
  }

  private handleMapClick(lat: number, lng: number): void {
    this.mapState.snapping$.next(true);
    this.mapState.showToast('🔄 Snapping to nearest road...');

    this.snapService.snap(lat, lng).subscribe({
      next: (result) => {
        this.mapState.snapping$.next(false);
        const mode = this.mapState.pinMode$.getValue();

        if (mode === 'source') {
          this.mapState.sourcePin$.next(result);
          this.mapState.pinMode$.next('dest');
          this.mapState.showToast(`📍 Origin snapped to: ${result.nearestRoadName}`);
        } else {
          this.mapState.destPin$.next(result);
          this.mapState.pinMode$.next('source');
          this.mapState.showToast(`📍 Destination snapped to: ${result.nearestRoadName}`);
        }
      },
      error: (err) => {
        this.mapState.snapping$.next(false);
        this.mapState.showToast('❌ No road within 1km of this point');
      }
    });
  }

  private subscribeToPins(): void {
    // Re-draw pin markers whenever source or dest changes
    this.subs.add(this.mapState.sourcePin$.subscribe(() => this.redrawPins()));
    this.subs.add(this.mapState.destPin$.subscribe(() => this.redrawPins()));
  }

  private redrawPins(): void {
    this.pinSource.clear();

    const source = this.mapState.sourcePin$.getValue();
    const dest   = this.mapState.destPin$.getValue();

    if (source) {
      this.pinSource.addFeature(this.makePinFeature(source.snappedLng, source.snappedLat, '#3FB950'));
    }
    if (dest) {
      this.pinSource.addFeature(this.makePinFeature(dest.snappedLng, dest.snappedLat, '#F78166'));
    }
  }

  private makePinFeature(lng: number, lat: number, color: string): Feature<Point> {
    const feature = new Feature({ geometry: new Point(fromLonLat([lng, lat])) });
    feature.setStyle(new Style({
      image: new CircleStyle({
        radius: 8,
        fill:   new Fill({ color }),
        stroke: new Stroke({ color: '#ffffff', width: 2 }),
      })
    }));
    return feature;
  }

  private subscribeToRoutes(): void {
    this.subs.add(
      this.mapState.routes$.subscribe(routes => this.redrawRoutes(routes))
    );
  }

  private redrawRoutes(routes: RouteResult[]): void {
    this.routeSource.clear();

    routes.forEach((route, i) => {
      const coords     = route.coordinates.map(([lng, lat]) => fromLonLat([lng, lat]));
      const feature    = new Feature({ geometry: new LineString(coords) });
      const color      = ROUTE_COLORS[i] ?? ROUTE_COLORS[ROUTE_COLORS.length - 1];
      const lineDash   = ROUTE_DASHES[i] ?? ROUTE_DASHES[ROUTE_DASHES.length - 1];
      const lineWidth  = i === 0 ? 4 : 2.5;

      feature.setStyle(new Style({
        stroke: new Stroke({ color, width: lineWidth, lineDash })
      }));

      // Hover: increase stroke width
      feature.set('routeIndex', i);
      this.routeSource.addFeature(feature);
    });
  }

  clearMap(): void {
    this.pinSource.clear();
    this.routeSource.clear();
    this.mapState.clearAll();
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.map.dispose();
  }
}
```

### components/map/map.component.html

```html
<div class="map-wrapper">
  <div #mapContainer class="map-container"></div>
</div>
```

### components/route-panel/route-panel.component.ts

```typescript
import { Component } from '@angular/core';
import { MapStateService }  from '../../services/map-state.service';
import { RouteService }     from '../../services/route.service';
import { RouteResult }      from '../../models/route-result.model';

const ROUTE_COLORS = ['#2F81F7', '#F78166', '#A371F7', '#3FB950', '#39D4C5'];

@Component({
  selector: 'app-route-panel',
  templateUrl: './route-panel.component.html',
  styleUrls:  ['./route-panel.component.scss']
})
export class RoutePanelComponent {
  routeColors = ROUTE_COLORS;

  constructor(
    public mapState: MapStateService,
    private routeService: RouteService
  ) {}

  get canFindRoutes(): boolean {
    return !!this.mapState.sourcePin$.getValue()
        && !!this.mapState.destPin$.getValue()
        && !this.mapState.routing$.getValue();
  }

  findRoutes(): void {
    const src  = this.mapState.sourcePin$.getValue()!;
    const dest = this.mapState.destPin$.getValue()!;
    const k    = this.mapState.numAlternatePaths$.getValue();

    this.mapState.routing$.next(true);
    this.mapState.showToast('🔄 Calculating routes...');

    this.routeService
      .findRoutes(src.snappedLat, src.snappedLng, dest.snappedLat, dest.snappedLng, k)
      .subscribe({
        next: (response) => {
          this.mapState.routing$.next(false);
          const results: RouteResult[] = response.features.map(f => ({
            routeIndex:           f.properties.routeIndex,
            label:                f.properties.label,
            totalDistanceMeters:  f.properties.totalDistanceMeters,
            estimatedTimeMinutes: f.properties.estimatedTimeMinutes,
            coordinates:          f.geometry.coordinates as [number, number][],
          }));
          this.mapState.routes$.next(results);
          this.mapState.showToast(`✅ ${results.length} routes found`);
        },
        error: () => {
          this.mapState.routing$.next(false);
          this.mapState.showToast('❌ Route calculation failed');
        }
      });
  }

  clearMap(): void {
    this.mapState.clearAll();
  }

  formatDistance(m: number): string {
    return m >= 1000 ? `${(m / 1000).toFixed(1)} km` : `${Math.round(m)} m`;
  }

  formatTime(minutes: number): string {
    const h = Math.floor(minutes / 60);
    const m = Math.round(minutes % 60);
    return h > 0 ? `${h}h ${m}min` : `${m}min`;
  }
}
```

### components/route-panel/route-panel.component.html

```html
<aside class="sidebar">
  <header class="sidebar__header">
    <span class="sidebar__logo">⬡ GeoRoute</span>
    <span class="sidebar__sub">Pan-India Routing</span>
  </header>

  <!-- Number of alternate paths -->
  <section class="sidebar__section">
    <label class="field-label">Routes to show</label>
    <div class="num-input-row">
      <button (click)="mapState.numAlternatePaths$.next(mapState.numAlternatePaths$.getValue() - 1)"
              [disabled]="(mapState.numAlternatePaths$ | async)! <= 1">−</button>
      <span class="num-display">{{ mapState.numAlternatePaths$ | async }}</span>
      <button (click)="mapState.numAlternatePaths$.next(mapState.numAlternatePaths$.getValue() + 1)"
              [disabled]="(mapState.numAlternatePaths$ | async)! >= 10">+</button>
    </div>
  </section>

  <!-- Origin -->
  <section class="sidebar__section">
    <label class="field-label">Origin</label>
    <div class="coord-display" *ngIf="mapState.sourcePin$ | async as src; else srcEmpty">
      <span class="pin-dot" style="background: var(--pin-source)"></span>
      <span class="coord-text">{{ src.snappedLat | number:'1.4-4' }}°, {{ src.snappedLng | number:'1.4-4' }}°</span>
      <span class="road-name">{{ src.nearestRoadName }}</span>
    </div>
    <ng-template #srcEmpty>
      <div class="coord-placeholder">Click on map to set origin</div>
    </ng-template>
  </section>

  <!-- Destination -->
  <section class="sidebar__section">
    <label class="field-label">Destination</label>
    <div class="coord-display" *ngIf="mapState.destPin$ | async as dst; else dstEmpty">
      <span class="pin-dot" style="background: var(--pin-dest)"></span>
      <span class="coord-text">{{ dst.snappedLat | number:'1.4-4' }}°, {{ dst.snappedLng | number:'1.4-4' }}°</span>
      <span class="road-name">{{ dst.nearestRoadName }}</span>
    </div>
    <ng-template #dstEmpty>
      <div class="coord-placeholder">Click on map to set destination</div>
    </ng-template>
  </section>

  <!-- Action buttons -->
  <section class="sidebar__section sidebar__actions">
    <button class="btn btn--primary" [disabled]="!canFindRoutes" (click)="findRoutes()">
      <span *ngIf="!(mapState.routing$ | async)">Find Routes</span>
      <span *ngIf="mapState.routing$ | async">Calculating...</span>
    </button>
    <button class="btn btn--ghost" (click)="clearMap()">Clear Map</button>
  </section>

  <!-- Route results list -->
  <section class="sidebar__section sidebar__routes"
           *ngIf="(mapState.routes$ | async)?.length">
    <label class="field-label">Routes</label>
    <div class="route-card"
         *ngFor="let route of mapState.routes$ | async; let i = index">
      <div class="route-card__swatch" [style.background]="routeColors[i] ?? routeColors[4]"></div>
      <div class="route-card__info">
        <span class="route-card__label">{{ route.label }}</span>
        <span class="route-card__meta">
          {{ formatDistance(route.totalDistanceMeters) }} ·
          {{ formatTime(route.estimatedTimeMinutes) }}
        </span>
      </div>
    </div>
  </section>
</aside>
```

### app.module.ts

```typescript
import { NgModule }              from '@angular/core';
import { BrowserModule }         from '@angular/platform-browser';
import { HttpClientModule }      from '@angular/common/http';
import { FormsModule }           from '@angular/forms';
import { ReactiveFormsModule }   from '@angular/forms';
import { CommonModule }          from '@angular/common';

import { AppRoutingModule }      from './app-routing.module';
import { AppComponent }          from './app.component';
import { MapComponent }          from './components/map/map.component';
import { RoutePanelComponent }   from './components/route-panel/route-panel.component';
import { ToastComponent }        from './components/toast/toast.component';

@NgModule({
  declarations: [
    AppComponent,
    MapComponent,
    RoutePanelComponent,
    ToastComponent,
  ],
  imports: [
    BrowserModule,
    HttpClientModule,   // Used internally — all requests go to localhost
    FormsModule,
    ReactiveFormsModule,
    CommonModule,
    AppRoutingModule,
  ],
  providers: [],
  bootstrap: [AppComponent]
})
export class AppModule {}
```

### app.component.html — Shell Layout

```html
<div class="app-shell">
  <app-route-panel></app-route-panel>
  <app-map></app-map>
  <app-toast></app-toast>
</div>
```

---

## ⚙️ Backend — Spring Boot (Unchanged from AngularJS version)

### application.properties

```properties
# ─── Database (local PostgreSQL + PostGIS) ──────────────────────────────────
spring.datasource.url=jdbc:postgresql://localhost:5432/georoute
spring.datasource.username=postgres
spring.datasource.password=yourpassword
spring.datasource.driver-class-name=org.postgresql.Driver

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.properties.hibernate.dialect=org.hibernate.spatial.dialect.postgis.PostgisPG10Dialect

# ─── GIS Data Paths (local filesystem — no URLs) ────────────────────────────
geotiff.base.path=../data/india_natural_color.tif
shapefile.base.path=../data/india_roads.shp

# ─── Tile Server ────────────────────────────────────────────────────────────
tiles.cache.dir=./tile-cache
tiles.cache.enabled=true

# ─── Server ─────────────────────────────────────────────────────────────────
server.port=8080

# ─── CORS: allow only local Angular dev server ──────────────────────────────
cors.allowed-origins=http://localhost:4200
```

### pom.xml — All Dependencies from Local Maven Cache

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.georoute</groupId>
    <artifactId>georoute-backend</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <!--
        All dependencies are downloaded ONCE via: mvn dependency:go-offline
        After that, build offline with: mvn install -offline
        No internet access required after initial setup.
    -->

    <repositories>
        <repository>
            <id>osgeo</id>
            <name>OSGeo Release Repository</name>
            <url>https://repo.osgeo.org/repository/release/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.hibernate.orm</groupId>
            <artifactId>hibernate-spatial</artifactId>
            <version>6.3.1.Final</version>
        </dependency>
        <dependency>
            <groupId>org.geotools</groupId>
            <artifactId>gt-coverage</artifactId>
            <version>29.2</version>
        </dependency>
        <dependency>
            <groupId>org.geotools</groupId>
            <artifactId>gt-geotiff</artifactId>
            <version>29.2</version>
        </dependency>
        <dependency>
            <groupId>org.geotools</groupId>
            <artifactId>gt-shapefile</artifactId>
            <version>29.2</version>
        </dependency>
        <dependency>
            <groupId>org.geotools</groupId>
            <artifactId>gt-referencing</artifactId>
            <version>29.2</version>
        </dependency>
        <dependency>
            <groupId>org.geotools</groupId>
            <artifactId>gt-epsg-hsql</artifactId>
            <version>29.2</version>
        </dependency>
        <dependency>
            <groupId>org.locationtech.jts</groupId>
            <artifactId>jts-core</artifactId>
            <version>1.19.0</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

---

## 🗄️ Database Schema (PostGIS — Local Install)

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

CREATE TABLE system_config (
    key   TEXT PRIMARY KEY,
    value TEXT
);

CREATE TABLE road_nodes (
    id        BIGSERIAL PRIMARY KEY,
    geom      GEOMETRY(Point, 4326) NOT NULL,
    elevation FLOAT DEFAULT 0
);
CREATE INDEX idx_road_nodes_geom ON road_nodes USING GIST(geom);

CREATE TABLE road_edges (
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
CREATE INDEX idx_road_edges_geom   ON road_edges USING GIST(geom);
CREATE INDEX idx_road_edges_source ON road_edges(source_id);
CREATE INDEX idx_road_edges_target ON road_edges(target_id);
```

---

## ⚙️ Backend Services

### RoutingService.java — A* + Yen's K-Shortest Paths

```java
@Service
public class RoutingService {

    // ─── A* Algorithm ────────────────────────────────────────────────────────
    // findShortestPath(sourceNodeId, targetNodeId) → List<Long> nodeIds
    //
    // f(n) = g(n) + h(n)
    //   g(n) = cumulative edge cost from source to n
    //   h(n) = haversineDistance(n, target) / maxSpeedKmh  ← admissible heuristic
    //
    // Haversine (pure Java — no library):
    //   double R = 6371000;
    //   double dLat = toRadians(lat2 - lat1);
    //   double dLon = toRadians(lon2 - lon1);
    //   double a = sin(dLat/2)^2 + cos(lat1)*cos(lat2)*sin(dLon/2)^2;
    //   return R * 2 * atan2(sqrt(a), sqrt(1-a));
    //
    // Respects edge.oneway flag.
    // Uses PriorityQueue<NodeState> ordered by f(n).

    // ─── Yen's K-Shortest Paths ───────────────────────────────────────────────
    // findKShortestPaths(sourceNodeId, targetNodeId, k) → List<RouteResult>
    //
    // 1. A[0] = A*(source, target)
    // 2. For i = 1..k-1:
    //    For each spur node in A[i-1]:
    //      rootPath = A[i-1].prefix(spurNode)
    //      Temporarily remove edges used by A[0..i-1] sharing rootPath
    //      spurPath = A*(spurNode, target) on pruned graph
    //      If found: add rootPath + spurPath to candidate heap B
    //      Restore removed edges
    //    A[i] = lowest-cost path in B; remove from B
    // 3. Return A[0..k-1] each as RouteResult with coordinates, distance, time, label
}
```

### RoadSnapService.java — PostGIS Nearest Road

```java
@Service
public class RoadSnapService {

    // Pure PostGIS query — no external geocoding whatsoever:
    //
    // SELECT
    //   e.id, e.road_name, e.source_id, e.target_id,
    //   ST_X(ST_ClosestPoint(e.geom, pt)) AS snap_lng,
    //   ST_Y(ST_ClosestPoint(e.geom, pt)) AS snap_lat,
    //   ST_Distance(e.geom::geography, pt::geography) AS dist_m
    // FROM road_edges e,
    // LATERAL (SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS pt) q
    // WHERE ST_DWithin(e.geom::geography, pt::geography, 1000)
    // ORDER BY dist_m ASC
    // LIMIT 1
    //
    // Returns nearest node ID (source or target of snapped edge)
    // plus snapped coordinates for pin placement.
    // Returns SnapError if no road within 1000m.
}
```

### GeoTiffTileService.java — Local GeoTIFF → PNG Tiles

```java
@Service
public class GeoTiffTileService {

    // Reads from local file ONLY — no WMS, no network call:
    //   AbstractGridCoverageReader reader = new GeoTiffReader(new File(geotiffPath));
    //
    // For each GET /api/tiles/{z}/{x}/{y}.png:
    //   1. Convert (z,x,y) TMS coords to WGS84 bounding box
    //   2. Crop GeoTIFF coverage to that bbox
    //   3. Scale to 256×256 BufferedImage
    //   4. Encode as PNG → return bytes
    //   5. Cache to disk at tiles.cache.dir/{z}/{x}/{y}.png for future requests
}
```

---

## 🔌 REST API Endpoints

```
POST   /api/route
       Body:     { "sourceLat": 28.6, "sourceLng": 77.2,
                   "destLat": 19.07, "destLng": 72.87,
                   "numAlternatePaths": 3 }
       Response: GeoJSON FeatureCollection

POST   /api/snap
       Body:     { "lat": 28.65, "lng": 77.23 }
       Response: SnapResult | SnapError

GET    /api/tiles/{z}/{x}/{y}.png
       Response: 256×256 PNG

GET    /api/health
       Response: { "status": "UP", "postgis": true, "dataLoaded": true }
```

---

## 🔄 Full User Flow

```
1.  User opens http://localhost:4200
2.  Angular 17 app bootstraps — ALL assets served from local ng serve
3.  OpenLayers map initializes with tile source: /api/tiles/{z}/{x}/{y}.png
      → ng serve proxy forwards to localhost:8080
      → Spring Boot renders GeoTIFF tile → returns PNG
4.  India satellite basemap renders in browser
5.  User sets "Routes to show: 4" in sidebar
6.  User clicks anywhere on map (on-road or off-road)
      → POST /api/snap { lat, lng }  (via ng serve proxy → :8080)
      → PostGIS: ST_DWithin + ST_ClosestPoint → nearest road found
      → Green pin appears at snapped location
      → Toast: "📍 Origin snapped to: NH-44"
7.  User clicks second location
      → Same snap flow → orange destination pin appears
8.  "Find Routes" button activates
9.  User clicks "Find Routes"
      → POST /api/route { ..., numAlternatePaths: 4 }
      → A* finds shortest path; Yen's finds 3 alternates
      → GeoJSON FeatureCollection returned with 4 features
10. Angular draws 4 colored LineStrings on OpenLayers map
11. Sidebar lists all 4 routes with distance and estimated time
12. User hovers route line → stroke widens, tooltip shows details
13. User clicks "Clear Map" → pins and routes cleared, state reset
```

---

## 📥 Data Ingestion Pipeline (Spring Boot Startup)

`DataLoaderService` implements `ApplicationRunner`, runs once, is idempotent:

1. Check `system_config` for `shp_loaded = 'true'` → skip if already done
2. Open `india_roads.shp` with `ShapefileDataStore(new File(path).toURI().toURL())` *(file:// — offline)*
3. Set `Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER = true`
4. Iterate `SimpleFeatureCollection`
5. For each `LineString` feature:
   - Deduplicate nodes by coordinate proximity (< 0.00001°)
   - Insert unique nodes → `road_nodes`
   - Map `fclass` → `speed_kmh` (motorway=110, primary=80, secondary=60, residential=40, track=20)
   - Compute `length_m` via `ST_Length(geom::geography)`
   - Insert → `road_edges`
6. Commit in batches of 1000
7. Write `system_config('shp_loaded', 'true')`

---

## ✅ Acceptance Criteria

- [ ] App runs fully with no internet connection after one-time setup
- [ ] `ng build --configuration production` completes using only local node_modules
- [ ] `mvn install --offline` builds the backend JAR successfully
- [ ] `index.html` contains zero `https://` references
- [ ] No `@import url('https://...')` in any SCSS/CSS file
- [ ] Fonts render from `src/assets/fonts/*.woff2` local files
- [ ] GeoTIFF tiles render at zoom levels 5–15 from local file
- [ ] Clicking off-road snaps pin to nearest road via PostGIS
- [ ] A* computes a valid shortest path (verified by unit test)
- [ ] Yen's K-Shortest returns exactly N distinct routes
- [ ] All N routes drawn with distinct colors and dash patterns
- [ ] Route panel shows distance (km) and time for each route
- [ ] "Clear Map" resets all state
- [ ] Error toast shown when no road within 1km

---

## 🚫 Absolute Constraints

- ❌ NO `<script src="https://...">` anywhere
- ❌ NO `<link href="https://fonts.googleapis.com/...">` anywhere
- ❌ NO `@import url('https://...')` in any SCSS or CSS file
- ❌ NO Google Maps API, Mapbox, ESRI, HERE, or any mapping API
- ❌ NO OSRM, Valhalla, GraphHopper — A* is hand-written in Java
- ❌ NO AngularJS (1.x) — use **Angular 17** with TypeScript
- ❌ NO external HTTP calls from Java at runtime
- ❌ NO CDN tile sources in OpenLayers — tile URL must be `/api/tiles/...` (localhost proxy)
- ❌ NO GeoServer or MapServer — tile rendering is in Spring Boot only
- ✅ Angular 17 with standalone-compatible module structure
- ✅ TypeScript strict mode enabled (`"strict": true` in tsconfig.json)
- ✅ All geometry stored as EPSG:4326 (WGS84)
- ✅ A* heuristic must be Haversine distance
- ✅ Yen's K-Shortest Paths wraps A* as its inner subroutine
- ✅ All PostGIS queries use `::geography` cast for meter-accurate distances

---
---

# 📦 Free Data Sources (Download During Setup)

## 1. GeoTIFF — Natural Color Satellite Imagery of India

### Option A: NASA Earthdata — MODIS (Easiest for Prototyping)
- URL: https://earthdata.nasa.gov/
- Shortcut: https://worldview.earthdata.nasa.gov/ → zoom India → Snapshot → GeoTIFF
- Resolution: 250m/pixel — good for zoom levels 5–10, single file

### Option B: Bhuvan — ISRO India Geoportal (Best India Coverage)
- URL: https://bhuvan.nrsc.gov.in/
- Account: Free registration
- Data: Resourcesat-2/2A → LISS-III → True Color Composite at 23.5m/pixel

### Option C: USGS Earth Explorer — Landsat 8/9 (30m, Free)
- URL: https://earthexplorer.usgs.gov/
- Bands 4, 3, 2 → natural color composite; merge with `gdal_merge.py` (offline)

### Option D: Copernicus Sentinel-2 (Best Quality — 10m)
- URL: https://dataspace.copernicus.eu/
- Product: S2MSI2A Level-2A True Color (TCI) GeoTIFF; mosaic with `gdal_merge.py`

---

## 2. Road Network SHP — Pan-India

### Option A: Geofabrik OpenStreetMap Extract (BEST — Recommended)
- URL: https://download.geofabrik.de/asia/india.html
- File: `india-latest-free.shp.zip` (~180MB) → extract `gis_osm_roads_free_1.shp`
- Has `fclass`, `name`, `oneway`, `maxspeed` — everything the routing engine needs
- License: ODbL (free with attribution)

### Option B: BBBike City Extract (For Development — Single City)
- URL: https://extract.bbbike.org/
- Draw bbox over Delhi/Mumbai/Bangalore → Shapefile → ~5–20MB

### Option C: DIVA-GIS (Simpler, Less Detail)
- URL: https://www.diva-gis.org/gdata → Country: India → Roads

---

## ⚡ Recommended Data Strategy

| Phase       | GeoTIFF                        | Road SHP                         |
|-------------|--------------------------------|----------------------------------|
| Development | NASA Worldview snapshot (250m) | BBBike — Delhi only              |
| Integration | USGS Landsat 8 (30m)           | Geofabrik — 1 state              |
| Production  | Sentinel-2 mosaic (10m)        | Geofabrik — full India           |

> **GeoTIFF prep with GDAL (offline tool — install once):**
> ```bash
> # Reproject to EPSG:4326 if needed
> gdalwarp -t_srs EPSG:4326 input.tif india_natural_color.tif
>
> # Build overview pyramids (speeds up low-zoom tile rendering)
> gdaladdo -r average india_natural_color.tif 2 4 8 16 32 64
>
> # Add internal tiling (speeds up random tile-crop access)
> gdal_translate -co TILED=YES -co COMPRESS=LZW india_natural_color.tif india_tiled.tif
> ```

---

*Generated for: GeoRoute — Pan-India Map & Routing Webapp*
*Stack: Angular 17 (TypeScript) + Spring Boot 3 + PostGIS + GeoTIFF + A\* Algorithm*
*Mode: 100% Offline — No External APIs, No CDN, No Internet Required at Runtime*
