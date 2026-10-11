import { HttpClient } from '@angular/common/http';
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
