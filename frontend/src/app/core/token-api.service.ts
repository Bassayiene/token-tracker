import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  HolderStats,
  SyncResult,
  TokenPrice,
  TokenQuantity,
  TokenSummary,
  TopHolders,
} from './models';

/** Optional `from`/`to` bounds; omitted bounds fall back to the backend defaults. */
export interface Range {
  from?: string;
  to?: string;
}

/** Client for the backend `/api/tokens` REST resource. */
@Injectable({ providedIn: 'root' })
export class TokenApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/tokens';

  getAll(): Observable<TokenSummary[]> {
    return this.http.get<TokenSummary[]>(this.baseUrl);
  }

  getOne(assetId: string): Observable<TokenSummary> {
    return this.http.get<TokenSummary>(this.url(assetId));
  }

  /** Hourly prices; `from`/`to` are ISO-8601 instants (default last 7 days). */
  getPrices(assetId: string, range: Range = {}): Observable<TokenPrice[]> {
    return this.http.get<TokenPrice[]>(this.url(assetId, 'prices'), { params: rangeParams(range) });
  }

  /** Daily quantities; `from`/`to` are ISO dates (default last 90 days). */
  getQuantities(assetId: string, range: Range = {}): Observable<TokenQuantity[]> {
    return this.http.get<TokenQuantity[]>(this.url(assetId, 'quantities'), {
      params: rangeParams(range),
    });
  }

  /** Daily holder statistics; `from`/`to` are ISO dates (default last 90 days). */
  getHolderStats(assetId: string, range: Range = {}): Observable<HolderStats[]> {
    return this.http.get<HolderStats[]>(this.url(assetId, 'holder-stats'), {
      params: rangeParams(range),
    });
  }

  /** Top holders of a day (ISO date), or of the latest snapshot when `date` is omitted. */
  getTopHolders(assetId: string, date?: string): Observable<TopHolders> {
    const params = date ? new HttpParams().set('date', date) : undefined;
    return this.http.get<TopHolders>(this.url(assetId, 'holders'), { params });
  }

  addToken(assetId: string): Observable<TokenSummary> {
    return this.http.post<TokenSummary>(this.baseUrl, { assetId });
  }

  deleteToken(assetId: string): Observable<void> {
    return this.http.delete<void>(this.url(assetId));
  }

  syncAll(): Observable<SyncResult[]> {
    return this.http.post<SyncResult[]>(`${this.baseUrl}/sync`, null);
  }

  syncOne(assetId: string): Observable<SyncResult> {
    return this.http.post<SyncResult>(this.url(assetId, 'sync'), null);
  }

  private url(assetId: string, sub?: string): string {
    const base = `${this.baseUrl}/${encodeURIComponent(assetId)}`;
    return sub ? `${base}/${sub}` : base;
  }
}

function rangeParams(range: Range): HttpParams {
  let params = new HttpParams();
  if (range.from) {
    params = params.set('from', range.from);
  }
  if (range.to) {
    params = params.set('to', range.to);
  }
  return params;
}

/** Human readable message from a backend error (`{message, entityName, errorKey}`) or a network failure. */
export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) {
      return 'The server cannot be reached.';
    }
    const message = (err.error as { message?: string } | null)?.message;
    return message || `Request failed (${err.status} ${err.statusText}).`;
  }
  return err instanceof Error ? err.message : 'Unexpected error.';
}
