import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, timeout, catchError, throwError } from 'rxjs';
import { SnapResult } from '../models/snap-result.model';

@Injectable({ providedIn: 'root' })
export class SnapService {
  // Relative URL → ng serve proxy forwards to localhost:8080
  private url = '/api/snap';

  constructor(private http: HttpClient) {}

  snap(lat: number, lng: number): Observable<SnapResult> {
    return this.http.post<SnapResult>(this.url, { lat, lng }).pipe(
      timeout(60000),
      catchError((err) => {
        if (err?.name === 'TimeoutError' || err?.message?.includes('Timeout')) {
          return throwError(() => new Error('Snap timed out — backend may still be loading road data'));
        }
        return throwError(() => err);
      })
    );
  }
}
