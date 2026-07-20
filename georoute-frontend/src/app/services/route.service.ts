import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, timeout, catchError, throwError } from 'rxjs';
import { RouteResponse, RoutingAlgorithm } from '../models/route-result.model';

@Injectable({ providedIn: 'root' })
export class RouteService {
  private url = '/api/route';

  constructor(private http: HttpClient) {}

  findRoutes(
    sourceLat: number,
    sourceLng: number,
    destLat: number,
    destLng: number,
    numAlternatePaths: number,
    algorithm: RoutingAlgorithm
  ): Observable<RouteResponse> {
    return this.http.post<RouteResponse>(this.url, {
      sourceLat,
      sourceLng,
      destLat,
      destLng,
      numAlternatePaths,
      algorithm,
    }).pipe(
      timeout(120000),
      catchError((err) => {
        if (err?.name === 'TimeoutError' || err?.message?.includes('Timeout')) {
          return throwError(() => new Error('Route calculation timed out'));
        }
        return throwError(() => err);
      })
    );
  }
}
