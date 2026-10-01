import { DatePipe, formatDate } from '@angular/common';
import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { HolderStats, TokenPrice, TokenQuantity, TokenSummary, TopHolders } from '../../core/models';
import { TokenApiService, errorMessage } from '../../core/token-api.service';
import { AmountPipe, ShortIdPipe, SignedPercentPipe, trendClass } from '../../shared/formatting';
import { ChartSeries, LineChart } from '../../shared/line-chart';
import { crossRates, periodChange } from './cross-rate';

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

/** Block explorer used to inspect a holder address. */
const EXPLORER_ADDRESS_URL = 'https://wscan.io/';

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

  /** Other tracked tokens, in which the price can be expressed. */
  protected readonly quoteOptions = signal<TokenSummary[]>([]);
  /** Token the price is expressed in; null for the collected price asset (WAVES). */
  protected readonly quote = signal<TokenSummary | null>(null);
  /** Prices of the quote token over the same range, in the collected price asset. */
  private readonly quotePrices = signal<TokenPrice[]>([]);
  /** Ignores responses of a price request that a newer one replaced. */
  private priceRequest = 0;

  protected readonly quoteLabel = computed(() => {
    const quote = this.quote();
    return quote ? (quote.ticker ?? quote.name) : (this.token()?.priceAsset ?? '');
  });
  /** Displayed prices: as collected, or divided by the quote token price of the same hour. */
  protected readonly displayedPrices = computed<(number | null)[]>(() =>
    this.quote() ? crossRates(this.prices(), this.quotePrices()) : this.prices().map((p) => p.price),
  );
  protected readonly displayedChange = computed(() => periodChange(this.displayedPrices()));

  protected readonly priceLabels = computed(() =>
    this.prices().map((p) => formatDate(p.time, 'MM-dd HH:mm', 'en-US', 'UTC')),
  );
  protected readonly priceSeries = computed<ChartSeries[]>(() => [
    {
      label: `Price (${this.quoteLabel()})`,
      data: this.displayedPrices(),
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

  protected explorerUrl(address: string): string {
    return EXPLORER_ADDRESS_URL + encodeURIComponent(address);
  }

  protected selectPriceRange(range: RangeOption): void {
    this.priceRange.set(range);
    this.loadPrices();
  }

  /** @param assetId quote token, or an empty string for the collected price asset */
  protected selectQuote(assetId: string): void {
    this.quote.set(this.quoteOptions().find((t) => t.assetId === assetId) ?? null);
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
    this.api.getAll().subscribe({
      next: (tokens) => {
        const options = tokens.filter((t) => t.assetId !== this.assetId());
        this.quoteOptions.set(options);
        // Keep the selection on reload, pointing to the fresh object (or drop it if the token is gone)
        const quoteId = this.quote()?.assetId;
        if (quoteId) {
          this.quote.set(options.find((t) => t.assetId === quoteId) ?? null);
        }
      },
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
    const request = ++this.priceRequest;
    const from = new Date(Date.now() - this.priceRange().days * DAY_MS).toISOString();
    const quote = this.quote();
    forkJoin({
      prices: this.api.getPrices(this.assetId(), { from }),
      quotePrices: quote ? this.api.getPrices(quote.assetId, { from }) : of([]),
    }).subscribe({
      next: ({ prices, quotePrices }) => {
        if (request === this.priceRequest) {
          this.prices.set(prices);
          this.quotePrices.set(quotePrices);
        }
      },
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
