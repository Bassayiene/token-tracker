import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

/** An address whose balances are checked periodically (admin only). */
export interface WatchedAddress {
  id: number;
  address: string;
  label: string | null;
  createdAt: string;
  /** Last successful check, null before the first one. */
  lastCheckedAt: string | null;
  /** Error of the last check (node or Telegram), null when it succeeded. */
  lastError: string | null;
  assetsCount: number;
}

export interface AddressBalance {
  /** `WAVES` for the native token. */
  assetId: string;
  assetName: string;
  balance: number;
  updatedAt: string;
}

export interface AddressBalanceChange {
  id: number;
  assetId: string;
  assetName: string;
  oldBalance: number;
  newBalance: number;
  delta: number;
  /** The asset was not held at the previous check. */
  newAsset: boolean;
  detectedAt: string;
  /** Whether Telegram accepted the notification. */
  notified: boolean;
}

/** An asset whose daily balances are stored for an address, even if it is no longer held. */
export interface AddressHistoryAsset {
  assetId: string;
  assetName: string;
  firstDate: string;
  lastDate: string;
}

/** Last balance observed on one day (UTC). */
export interface AddressDailyBalance {
  date: string;
  balance: number;
}

/** Daily variation of one asset, in percent of its balance on its first tracked day. */
export interface AddressAssetVariation {
  assetId: string;
  assetName: string;
  /** First day with a stored balance: the 0 % reference. */
  baseDate: string;
  baseBalance: number;
  /** Variation on the last day of the period. */
  lastPercent: number;
  points: { date: string; percent: number }[];
}

export interface AddressCheckResult {
  id: number;
  address: string;
  changes: number;
  notified: boolean;
  error: string | null;
}

/** Client for the backend `/api/watched-addresses` REST resource. */
@Injectable({ providedIn: 'root' })
export class WatchApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/watched-addresses';

  getAll(): Observable<WatchedAddress[]> {
    return this.http.get<WatchedAddress[]>(this.baseUrl);
  }

  add(address: string, label: string | null): Observable<WatchedAddress> {
    return this.http.post<WatchedAddress>(this.baseUrl, { address, label });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  getBalances(id: number): Observable<AddressBalance[]> {
    return this.http.get<AddressBalance[]>(`${this.baseUrl}/${id}/balances`);
  }

  getChanges(id: number): Observable<AddressBalanceChange[]> {
    return this.http.get<AddressBalanceChange[]>(`${this.baseUrl}/${id}/changes`);
  }

  getHistoryAssets(id: number): Observable<AddressHistoryAsset[]> {
    return this.http.get<AddressHistoryAsset[]>(`${this.baseUrl}/${id}/history/assets`);
  }

  /** Daily balances of one asset; `from` is an ISO date (default last 90 days). */
  getHistory(id: number, assetId: string, from?: string): Observable<AddressDailyBalance[]> {
    let params = new HttpParams().set('assetId', assetId);
    if (from) {
      params = params.set('from', from);
    }
    return this.http.get<AddressDailyBalance[]>(`${this.baseUrl}/${id}/history`, { params });
  }

  /** Assets that moved the most, in percent of their first tracked day; `from` is an ISO date (default last 90 days). */
  getVariations(id: number, from?: string): Observable<AddressAssetVariation[]> {
    const params = from ? new HttpParams().set('from', from) : undefined;
    return this.http.get<AddressAssetVariation[]>(`${this.baseUrl}/${id}/history/variations`, { params });
  }

  check(id: number): Observable<AddressCheckResult> {
    return this.http.post<AddressCheckResult>(`${this.baseUrl}/${id}/check`, null);
  }

  isTelegramConfigured(): Observable<boolean> {
    return this.http
      .get<{ configured: boolean }>(`${this.baseUrl}/telegram`)
      .pipe(map((status) => status.configured));
  }

  sendTelegramTest(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/telegram/test`, null);
  }
}
