import { DatePipe, formatDate } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { HolderStats, TokenPrice, TokenQuantity, TokenSummary, TopHolders } from '../../core/models';
import { TokenApiService, errorMessage } from '../../core/token-api.service';
import { AmountPipe, ShortIdPipe, SignedPercentPipe, trendClass } from '../../shared/formatting';
import { ChartSeries, LineChart } from '../../shared/line-chart';

interface RangeOption {
  label: string;
  days: number;
}

const PRICE_RANGES: RangeOption[] = [
  { label: '24h', days: 1 },
  { label: '7d', days: 7 },
  { label: '30d', days: 30 },
];

const DAILY_RANGES: RangeOption[] = [
  { label: '30d', days: 30 },
  { label: '90d', days: 90 },
  { label: '1y', days: 365 },
];

const DAY_MS = 24 * 3600 * 1000;

@Component({
  selector: 'app-token-detail',
  imports: [RouterLink, DatePipe, AmountPipe, SignedPercentPipe, ShortIdPipe, LineChart],
  templateUrl: './token-detail.html',
})
export class TokenDetail {
  private readonly api = inject(TokenApiService);
  protected readonly auth = inject(AuthService);

  /** Route parameter, bound through `withComponentInputBinding()`. */
  readonly assetId = input.required<string>();

  protected readonly priceRanges = PRICE_RANGES;
  protected readonly dailyRanges = DAILY_RANGES;
  protected readonly trendClass = trendClass;

  protected readonly token = signal<TokenSummary | null>(null);
  protected readonly prices = signal<TokenPrice[]>([]);
  protected readonly quantities = signal<TokenQuantity[]>([]);
  protected readonly holderStats = signal<HolderStats[]>([]);
  protected readonly topHolders = signal<TopHolders | null>(null);

  protected readonly priceRange = signal(PRICE_RANGES[1]);
  protected readonly dailyRange = signal(DAILY_RANGES[1]);

  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly syncing = signal(false);

  protected readonly priceLabels = computed(() =>
    this.prices().map((p) => formatDate(p.time, 'MM-dd HH:mm', 'en-US', 'UTC')),
  );
  protected readonly priceSeries = computed<ChartSeries[]>(() => [
    {
      label: `Price (${this.token()?.priceAsset ?? ''})`,
      data: this.prices().map((p) => p.price),
      color: '#2563eb',
      fill: true,
    },
  ]);
  protected readonly tradedHours = computed(() => this.prices().filter((p) => p.hasTrades).length);
  protected readonly periodVolume = computed(() =>
    this.prices().reduce((sum, p) => sum + p.volume, 0),
  );

  protected readonly quantityLabels = computed(() => this.quantities().map((q) => q.date));
  protected readonly quantitySeries = computed<ChartSeries[]>(() => [
    { label: 'Quantity', data: this.quantities().map((q) => q.quantity), color: '#7c3aed', fill: true },
  ]);

  protected readonly statsLabels = computed(() => this.holderStats().map((s) => s.date));
  protected readonly statsSeries = computed<ChartSeries[]>(() => [
    { label: 'Holders', data: this.holderStats().map((s) => s.holdersCount), color: '#059669' },
    {
      label: 'Top 10 share',
      data: this.holderStats().map((s) => s.top10Share),
      color: '#d97706',
      rightAxis: true,
    },
    {
      label: 'Top 100 share',
      data: this.holderStats().map((s) => s.top100Share),
      color: '#dc2626',
      rightAxis: true,
    },
  ]);

  constructor() {
    // What can be read depends on the session: reload whenever it starts or ends.
    effect(() => {
      this.auth.isAuthenticated();
      untracked(() => this.loadAll());
    });
  }

  protected selectPriceRange(range: RangeOption): void {
    this.priceRange.set(range);
    this.loadPrices();
  }

  protected selectDailyRange(range: RangeOption): void {
    this.dailyRange.set(range);
    this.loadDaily();
  }

  protected selectHoldersDate(date: string): void {
    this.api.getTopHolders(this.assetId(), date).subscribe({
      next: (holders) => this.topHolders.set(holders),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  protected sync(): void {
    this.syncing.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.api.syncOne(this.assetId()).subscribe({
      next: (result) => {
        this.syncing.set(false);
        if (result.error) {
          this.error.set(`Sync failed: ${result.error}`);
        } else {
          this.notice.set(
            `Sync done: ${result.priceHours} price hour(s) written` +
              (result.dailyDone ? ', daily figures updated.' : '.'),
          );
        }
        this.loadAll();
      },
      error: (err) => {
        this.syncing.set(false);
        this.error.set(errorMessage(err));
      },
    });
  }

  private loadAll(): void {
    this.api.getOne(this.assetId()).subscribe({
      next: (token) => this.token.set(token),
      error: (err) => this.error.set(errorMessage(err)),
    });
    this.loadPrices();
    if (!this.auth.isAuthenticated()) {
      // Daily data and holders are reserved to authenticated users.
      this.quantities.set([]);
      this.holderStats.set([]);
      this.topHolders.set(null);
      return;
    }
    this.loadDaily();
    this.api.getTopHolders(this.assetId()).subscribe({
      next: (holders) => this.topHolders.set(holders),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  private loadPrices(): void {
    const from = new Date(Date.now() - this.priceRange().days * DAY_MS).toISOString();
    this.api.getPrices(this.assetId(), { from }).subscribe({
      next: (prices) => this.prices.set(prices),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  private loadDaily(): void {
    const from = new Date(Date.now() - this.dailyRange().days * DAY_MS).toISOString().slice(0, 10);
    this.api.getQuantities(this.assetId(), { from }).subscribe({
      next: (quantities) => this.quantities.set(quantities),
      error: (err) => this.error.set(errorMessage(err)),
    });
    this.api.getHolderStats(this.assetId(), { from }).subscribe({
      next: (stats) => this.holderStats.set(stats),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }
}
