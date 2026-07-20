import { Component, OnInit, OnDestroy, ElementRef, ViewChild } from '@angular/core';
import { Subscription } from 'rxjs';

// All OpenLayers imports from local node_modules — no CDN.
import Map from 'ol/Map';
import View from 'ol/View';
import TileLayer from 'ol/layer/Tile';
import XYZ from 'ol/source/XYZ';
import VectorLayer from 'ol/layer/Vector';
import VectorSource from 'ol/source/Vector';
import Feature from 'ol/Feature';
import Point from 'ol/geom/Point';
import LineString from 'ol/geom/LineString';
import Style from 'ol/style/Style';
import Stroke from 'ol/style/Stroke';
import CircleStyle from 'ol/style/Circle';
import Fill from 'ol/style/Fill';
import { fromLonLat, toLonLat, transformExtent } from 'ol/proj';
import { HttpClient } from '@angular/common/http';

import { MapStateService } from '../../services/map-state.service';
import { SnapService } from '../../services/snap.service';
import { RouteResult } from '../../models/route-result.model';
import { environment } from '../../../environments/environment';

const ROUTE_COLORS = ['#2F81F7', '#F78166', '#A371F7', '#3FB950', '#39D4C5'];
const ROUTE_DASHES: (number[] | undefined)[] = [
  undefined, // primary: solid
  [8, 4], // alt 1: long dash
  [6, 6], // alt 2: medium dash
  [4, 8], // alt 3: short dash
  [2, 6], // alt 4+: dotted
];

@Component({
  selector: 'app-map',
  templateUrl: './map.component.html',
  styleUrls: ['./map.component.scss'],
})
export class MapComponent implements OnInit, OnDestroy {
  @ViewChild('mapContainer', { static: true }) mapContainer!: ElementRef;

  private map!: Map;
  private pinSource = new VectorSource();
  private routeSource = new VectorSource();
  private subs = new Subscription();

  constructor(
    private mapState: MapStateService,
    private snapService: SnapService,
    private http: HttpClient
  ) {}

  ngOnInit(): void {
    this.initMap();
    this.fitToRoadData();
    this.subscribeToRoutes();
    this.subscribeToPins();
  }

  private initMap(): void {
    this.map = new Map({
      target: this.mapContainer.nativeElement,
      layers: [
        // Local Spring Boot tile server on localhost (proxied to :8080 by ng serve).
        new TileLayer({
          source: new XYZ({
            url: environment.tileUrl,
            crossOrigin: 'anonymous',
            maxZoom: 18,
            minZoom: 4,
          }),
        }),
        // Route lines layer
        new VectorLayer({ source: this.routeSource, zIndex: 1 }),
        // Pin markers layer
        new VectorLayer({ source: this.pinSource, zIndex: 2 }),
      ],
      view: new View({
        center: fromLonLat([77.209, 28.6139]), // Delhi — match typical city road extracts
        zoom: 12,
        minZoom: 4,
        maxZoom: 18,
      }),
    });

    // Map click → snap to nearest road.
    this.map.on('click', (event) => {
      const [lng, lat] = toLonLat(event.coordinate);
      this.handleMapClick(lat, lng);
    });
  }

  /** Zoom the map to the loaded road-network footprint (small extracts won't cover all of Delhi). */
  private fitToRoadData(): void {
    this.http.get<{
      dataReady?: boolean;
      roadBounds?: { minLng: number; minLat: number; maxLng: number; maxLat: number };
      edgeCount?: number;
    }>('/api/health').subscribe({
      next: (health) => {
        const b = health.roadBounds;
        if (!b) return;
        const extent = transformExtent(
          [b.minLng, b.minLat, b.maxLng, b.maxLat],
          'EPSG:4326',
          'EPSG:3857'
        );
        this.map.getView().fit(extent, { padding: [48, 48, 48, 48], maxZoom: 16, duration: 300 });
        if ((health.edgeCount ?? 0) < 5000) {
          this.mapState.showToast(
            'Road data covers a small area — map zoomed to fit. Click inside the highlighted region.'
          );
        }
      },
      error: () => {},
    });
  }

  private handleMapClick(lat: number, lng: number): void {
    if (this.mapState.snapping$.getValue()) {
      return;
    }
    this.mapState.snapping$.next(true);
    this.mapState.showToast('Snapping to nearest road...');

    this.snapService.snap(lat, lng).subscribe({
      next: (result) => {
        this.mapState.snapping$.next(false);
        const mode = this.mapState.pinMode$.getValue();

        if (mode === 'source') {
          this.mapState.sourcePin$.next(result);
          this.mapState.pinMode$.next('dest');
          this.mapState.showToast(`Origin snapped to: ${result.nearestRoadName}`);
        } else {
          this.mapState.destPin$.next(result);
          this.mapState.pinMode$.next('source');
          this.mapState.showToast(`Destination snapped to: ${result.nearestRoadName}`);
        }
      },
      error: (err) => {
        this.mapState.snapping$.next(false);
        let msg = 'Snap failed — is the backend running on :8080?';
        if (err?.status === 503) {
          msg = 'Roads still loading — wait for ingest to finish, then try again';
        } else if (err?.status === 404) {
          msg = 'No road within 1km — click closer to a road';
        } else if (err?.status === 504 || err?.status === 500) {
          msg = err?.error?.message || 'Snap timed out — try clicking again';
        } else if (err?.message?.includes('timed out') || err?.message?.includes('Timeout')) {
          msg = 'Snap timed out — try clicking again';
        } else if (err?.error?.message) {
          msg = err.error.message;
        }
        this.mapState.showToast(msg);
      },
    });
  }

  private subscribeToPins(): void {
    this.subs.add(this.mapState.sourcePin$.subscribe(() => this.redrawPins()));
    this.subs.add(this.mapState.destPin$.subscribe(() => this.redrawPins()));
  }

  private redrawPins(): void {
    this.pinSource.clear();

    const source = this.mapState.sourcePin$.getValue();
    const dest = this.mapState.destPin$.getValue();

    if (source) {
      this.pinSource.addFeature(
        this.makePinFeature(source.snappedLng, source.snappedLat, '#3FB950')
      );
    }
    if (dest) {
      this.pinSource.addFeature(
        this.makePinFeature(dest.snappedLng, dest.snappedLat, '#F78166')
      );
    }
  }

  private makePinFeature(lng: number, lat: number, color: string): Feature<Point> {
    const feature = new Feature({ geometry: new Point(fromLonLat([lng, lat])) });
    feature.setStyle(
      new Style({
        image: new CircleStyle({
          radius: 8,
          fill: new Fill({ color }),
          stroke: new Stroke({ color: '#ffffff', width: 2 }),
        }),
      })
    );
    return feature;
  }

  private subscribeToRoutes(): void {
    this.subs.add(this.mapState.routes$.subscribe((routes) => this.redrawRoutes(routes)));
  }

  private redrawRoutes(routes: RouteResult[]): void {
    this.routeSource.clear();

    // Draw alternates first so the primary route sits on top.
    const order = routes.map((_, i) => i).reverse();
    for (const i of order) {
      const route = routes[i];
      const coords = route.coordinates.map(([lng, lat]) => fromLonLat([lng, lat]));
      const feature = new Feature({ geometry: new LineString(coords) });
      const color = ROUTE_COLORS[i] ?? ROUTE_COLORS[ROUTE_COLORS.length - 1];
      const lineDash = ROUTE_DASHES[i] ?? ROUTE_DASHES[ROUTE_DASHES.length - 1];
      const lineWidth = i === 0 ? 5 : 3;

      feature.setStyle(
        new Style({
          stroke: new Stroke({ color, width: lineWidth, lineDash }),
        })
      );
      feature.set('routeIndex', i);
      this.routeSource.addFeature(feature);
    }

    // Zoom to fit the primary route.
    if (routes.length > 0 && routes[0].coordinates.length > 0) {
      const extent = this.routeSource.getExtent();
      this.map.getView().fit(extent, { padding: [60, 60, 60, 60], duration: 400, maxZoom: 15 });
    }
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
    this.map.dispose();
  }
}
