import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal, untracked } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/token-api.service';
import {
  AddressBalance,
  AddressBalanceChange,
  WatchApiService,
  WatchedAddress,
} from '../../core/watch-api.service';
import { AmountPipe, ShortIdPipe, trendClass } from '../../shared/formatting';

/** Base58 Waves address, same rule as the backend `AddWatchedAddressRequest`. */
const ADDRESS_PATTERN = /^[1-9A-HJ-NP-Za-km-z]{35}$/;
const EXPLORER_ADDRESS_URL = 'https://wscan.io/';

/** Admin page: addresses whose balance variations are notified on Telegram. */
@Component({
  selector: 'app-watch-page',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, AmountPipe, ShortIdPipe],
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
    this.loadDetails(item.id);
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
