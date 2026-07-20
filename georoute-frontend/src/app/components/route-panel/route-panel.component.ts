import { Component } from '@angular/core';
import { MapStateService } from '../../services/map-state.service';
import { RouteService } from '../../services/route.service';
import { RouteResult, RoutingAlgorithm } from '../../models/route-result.model';

const ROUTE_COLORS = ['#2F81F7', '#F78166', '#A371F7', '#3FB950', '#39D4C5'];

@Component({
  selector: 'app-route-panel',
  templateUrl: './route-panel.component.html',
  styleUrls: ['./route-panel.component.scss'],
})
export class RoutePanelComponent {
  routeColors = ROUTE_COLORS;

  constructor(
    public mapState: MapStateService,
    private routeService: RouteService
  ) {}

  get canFindRoutes(): boolean {
    return (
      !!this.mapState.sourcePin$.getValue() &&
      !!this.mapState.destPin$.getValue() &&
      !this.mapState.routing$.getValue()
    );
  }

  setAlgorithm(algorithm: RoutingAlgorithm): void {
    this.mapState.algorithm$.next(algorithm);
  }

  decRoutes(): void {
    const v = this.mapState.numAlternatePaths$.getValue();
    if (v > 1) {
      this.mapState.numAlternatePaths$.next(v - 1);
    }
  }

  incRoutes(): void {
    const v = this.mapState.numAlternatePaths$.getValue();
    if (v < 10) {
      this.mapState.numAlternatePaths$.next(v + 1);
    }
  }

  findRoutes(): void {
    const src = this.mapState.sourcePin$.getValue();
    const dest = this.mapState.destPin$.getValue();
    if (!src || !dest) {
      return;
    }
    const k = this.mapState.numAlternatePaths$.getValue();
    const algorithm = this.mapState.algorithm$.getValue();

    this.mapState.routing$.next(true);
    this.mapState.showToast('Calculating routes...');

    this.routeService
      .findRoutes(src.snappedLat, src.snappedLng, dest.snappedLat, dest.snappedLng, k, algorithm)
      .subscribe({
        next: (response) => {
          this.mapState.routing$.next(false);
          const results: RouteResult[] = response.features.map((f) => ({
            routeIndex: f.properties.routeIndex,
            label: f.properties.label,
            totalDistanceMeters: f.properties.totalDistanceMeters,
            estimatedTimeMinutes: f.properties.estimatedTimeMinutes,
            coordinates: f.geometry.coordinates,
          }));
          this.mapState.routes$.next(results);
          this.mapState.showToast(
            results.length
              ? `${results.length} route(s) found`
              : 'No route found — restart backend with shapefile.force-reload=true to fix road data'
          );
        },
        error: (err) => {
          this.mapState.routing$.next(false);
          const msg =
            err?.message?.includes('timed out') || err?.message?.includes('Timeout')
              ? 'Route calculation timed out — try closer points'
              : err?.error?.message || 'Route calculation failed';
          this.mapState.showToast(msg);
        },
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
