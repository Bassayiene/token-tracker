import { DatePipe } from '@angular/common';
import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/token-api.service';
import {
  AddressAssetVariation,
  AddressBalance,
  AddressBalanceChange,
  AddressDailyBalance,
  AddressHistoryAsset,
  WatchApiService,
  WatchedAddress,
} from '../../core/watch-api.service';
import { AmountPipe, ShortIdPipe, SignedPercentPipe, trendClass } from '../../shared/formatting';
import { ChartSeries, LineChart } from '../../shared/line-chart';

interface RangeOption {
  label: string;
  days: number;
}

const HISTORY_RANGES: RangeOption[] = [
  { label: '30d', days: 30 },
  { label: '90d', days: 90 },
  { label: '1y', days: 365 },
];

const DAY_MS = 24 * 3600 * 1000;

/** One colour per curve of the variation chart; the backend returns at most this many assets. */
const VARIATION_COLORS = [
  '#2563eb',
  '#dc2626',
  '#059669',
  '#d97706',
  '#7c3aed',
  '#0891b2',
  '#db2777',
  '#4b5563',
];

/** Base58 Waves address, same rule as the backend `AddWatchedAddressRequest`. */
const ADDRESS_PATTERN = /^[1-9A-HJ-NP-Za-km-z]{35}$/;
const EXPLORER_ADDRESS_URL = 'https://wscan.io/';

/** Admin page: addresses whose balance variations are notified on Telegram. */
@Component({
  selector: 'app-watch-page',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, AmountPipe, ShortIdPipe, SignedPercentPipe, LineChart],
  templateUrl: './watch-page.html',
})
export class WatchPage {
  private readonly api = inject(WatchApiService);
  protected readonly auth = inject(AuthService);

  protected readonly addresses = signal<WatchedAddress[]>([]);
  protected readonly telegramConfigured = signal<boolean | null>(null);
  protected readonly selected = signal<WatchedAddress | null>(null);
  protected readonly changes = signal<AddressBalanceChange[]>([]);
  protected readonly balances = signal<AddressBalance[]>([]);

  /** Assets that have daily balances for the selected address, including those no longer held. */
  protected readonly historyAssets = signal<AddressHistoryAsset[]>([]);
  protected readonly historyAssetId = signal<string | null>(null);
  protected readonly history = signal<AddressDailyBalance[]>([]);
  protected readonly historyRanges = HISTORY_RANGES;
  protected readonly historyRange = signal(HISTORY_RANGES[1]);

  /** Assets that moved, in percent of their first tracked day, over the same period as the daily balance. */
  protected readonly variations = signal<AddressAssetVariation[]>([]);
  protected readonly variationColors = VARIATION_COLORS;
  /** Every day on which at least one of the assets has a point. */
  protected readonly variationLabels = computed(() =>
    [...new Set(this.variations().flatMap((v) => v.points.map((p) => p.date)))].sort(),
  );
  protected readonly variationSeries = computed<ChartSeries[]>(() => {
    const labels = this.variationLabels();
    return this.variations().map((v, i) => {
      const byDate = new Map(v.points.map((p) => [p.date, p.percent]));
      return {
        label: v.assetName,
        // Days without a point for this asset are bridged by the chart
        data: labels.map((date) => byDate.get(date) ?? null),
        color: VARIATION_COLORS[i % VARIATION_COLORS.length],
      };
    });
  });

  protected readonly historyLabels = computed(() => this.history().map((h) => h.date));
  protected readonly historySeries = computed<ChartSeries[]>(() => {
    const asset = this.historyAssets().find((a) => a.assetId === this.historyAssetId());
    return [
      {
        label: asset?.assetName ?? 'Balance',
        data: this.history().map((h) => h.balance),
        color: '#2563eb',
        fill: true,
      },
    ];
  });

  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly trendClass = trendClass;

  protected readonly form = new FormGroup({
    address: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(ADDRESS_PATTERN)],
    }),
    label: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(100)] }),
  });

  constructor() {
    // Reload when the session starts or ends: everything here is reserved to admins.
    effect(() => {
      const admin = this.auth.isAdmin();
      untracked(() => {
        this.selected.set(null);
        if (admin) {
          this.load();
          this.api.isTelegramConfigured().subscribe({
            next: (configured) => this.telegramConfigured.set(configured),
            error: (err) => this.error.set(errorMessage(err)),
          });
        }
      });
    });
  }

  protected explorerUrl(address: string): string {
    return EXPLORER_ADDRESS_URL + encodeURIComponent(address);
  }

  protected add(): void {
    const address = this.form.controls.address.value.trim();
    if (!ADDRESS_PATTERN.test(address) || this.form.controls.label.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const label = this.form.controls.label.value.trim() || null;
    this.run(
      () => this.api.add(address, label),
      (created) => {
        this.form.reset();
        this.notice.set(
          `Now watching ${created.label ?? created.address}: ${created.assetsCount} asset(s) stored as the baseline.`,
        );
        this.load();
      },
    );
  }

  protected remove(item: WatchedAddress): void {
    if (!confirm(`Stop watching ${item.label ?? item.address} and delete its history?`)) {
      return;
    }
    this.run(
      () => this.api.delete(item.id),
      () => {
        if (this.selected()?.id === item.id) {
          this.selected.set(null);
        }
        this.load();
      },
    );
  }

  protected check(item: WatchedAddress): void {
    this.run(
      () => this.api.check(item.id),
      (result) => {
        if (result.error) {
          this.error.set(`Check of ${item.label ?? item.address}: ${result.error}`);
        } else {
          this.notice.set(
            !result.changes
              ? 'No variation since the last check.'
              : this.telegramConfigured()
                ? `${result.changes} variation(s) detected and sent to Telegram.`
                : `${result.changes} variation(s) detected (not sent: Telegram is not configured).`,
          );
        }
        this.load();
        if (this.selected()?.id === item.id) {
          this.loadDetails(item.id);
          this.loadHistory();
          this.loadVariations();
        }
      },
    );
  }

  protected select(item: WatchedAddress): void {
    if (this.selected()?.id === item.id) {
      this.selected.set(null);
      return;
    }
    this.selected.set(item);
    this.changes.set([]);
    this.balances.set([]);
    this.historyAssets.set([]);
    this.historyAssetId.set(null);
    this.history.set([]);
    this.variations.set([]);
    this.loadDetails(item.id);
    this.loadVariations();
    this.api.getHistoryAssets(item.id).subscribe({
      next: (assets) => {
        this.historyAssets.set(assets);
        // The backend lists WAVES first
        this.selectHistoryAsset(assets[0]?.assetId ?? null);
      },
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  protected selectHistoryAsset(assetId: string | null): void {
    this.historyAssetId.set(assetId);
    this.loadHistory();
  }

  protected selectHistoryRange(range: RangeOption): void {
    this.historyRange.set(range);
    this.loadHistory();
    this.loadVariations();
  }

  protected sendTest(): void {
    this.run(
      () => this.api.sendTelegramTest(),
      () => this.notice.set('Test message sent: check your Telegram chat.'),
    );
  }

  private load(): void {
    this.api.getAll().subscribe({
      next: (addresses) => {
        this.addresses.set(addresses);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  private loadDetails(id: number): void {
    this.api.getChanges(id).subscribe({
      next: (changes) => this.changes.set(changes),
      error: (err) => this.error.set(errorMessage(err)),
    });
    this.api.getBalances(id).subscribe({
      next: (balances) => this.balances.set(balances),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  private loadHistory(): void {
    const id = this.selected()?.id;
    const assetId = this.historyAssetId();
    if (id === undefined || assetId === null) {
      this.history.set([]);
      return;
    }
    this.api.getHistory(id, assetId, this.historyFrom()).subscribe({
      next: (history) => this.history.set(history),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  private loadVariations(): void {
    const id = this.selected()?.id;
    if (id === undefined) {
      return;
    }
    this.api.getVariations(id, this.historyFrom()).subscribe({
      next: (variations) => this.variations.set(variations),
      error: (err) => this.error.set(errorMessage(err)),
    });
  }

  /** First day (UTC) of the selected period. */
  private historyFrom(): string {
    return new Date(Date.now() - (this.historyRange().days - 1) * DAY_MS).toISOString().slice(0, 10);
  }

  private run<T>(call: () => Observable<T>, done: (value: T) => void): void {
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    call().subscribe({
      next: (value) => {
        this.busy.set(false);
        done(value);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(errorMessage(err));
      },
    });
  }
}
