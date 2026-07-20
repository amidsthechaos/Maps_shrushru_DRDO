import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { SnapResult } from '../models/snap-result.model';
import { RouteResult, RoutingAlgorithm } from '../models/route-result.model';

@Injectable({ providedIn: 'root' })
export class MapStateService {
  // Pin state
  sourcePin$ = new BehaviorSubject<SnapResult | null>(null);
  destPin$ = new BehaviorSubject<SnapResult | null>(null);

  // Route results
  routes$ = new BehaviorSubject<RouteResult[]>([]);

  // Loading states
  snapping$ = new BehaviorSubject<boolean>(false);
  routing$ = new BehaviorSubject<boolean>(false);

  // Toast messages
  toast$ = new BehaviorSubject<string | null>(null);

  // Number of routes requested by user (1 = shortest only)
  numAlternatePaths$ = new BehaviorSubject<number>(3);

  // Selected routing engine
  algorithm$ = new BehaviorSubject<RoutingAlgorithm>('astar');

  // Which pin is being placed next: 'source' | 'dest'
  pinMode$ = new BehaviorSubject<'source' | 'dest'>('source');

  private toastTimer: ReturnType<typeof setTimeout> | null = null;

  showToast(message: string, durationMs = 3000): void {
    this.toast$.next(message);
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
    }
    this.toastTimer = setTimeout(() => this.toast$.next(null), durationMs);
  }

  clearAll(): void {
    this.sourcePin$.next(null);
    this.destPin$.next(null);
    this.routes$.next([]);
    this.pinMode$.next('source');
  }
}
