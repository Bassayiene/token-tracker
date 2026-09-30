import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal, untracked } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { TokenSummary } from '../../core/models';
import { TokenApiService, errorMessage } from '../../core/token-api.service';
import { AmountPipe, ShortIdPipe, SignedPercentPipe, trendClass } from '../../shared/formatting';

/** Base58 Waves asset id, same rule as the backend `AddTokenRequest`. */
const ASSET_ID_PATTERN = /^[1-9A-HJ-NP-Za-km-z]{32,44}$/;

@Component({
  selector: 'app-token-list',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, AmountPipe, SignedPercentPipe, ShortIdPipe],
  templateUrl: './token-list.html',
})
export class TokenList {
  private readonly api = inject(TokenApiService);
  protected readonly auth = inject(AuthService);

  protected readonly tokens = signal<TokenSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly trendClass = trendClass;

  protected readonly assetId = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(ASSET_ID_PATTERN)],
  });
  protected readonly addForm = new FormGroup({ assetId: this.assetId });

  constructor() {
    // Holder figures depend on the session: reload whenever it starts or ends.
    effect(() => {
      this.auth.isAuthenticated();
      untracked(() => this.load());
    });
  }

  protected load(): void {
    this.loading.set(true);
    this.api.getAll().subscribe({
      next: (tokens) => {
        this.tokens.set(tokens);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected add(): void {
    const assetId = this.assetId.value.trim();
    if (!ASSET_ID_PATTERN.test(assetId)) {
      this.assetId.markAsTouched();
      return;
    }
    this.run(() => this.api.addToken(assetId), (token) => {
      this.assetId.reset();
      this.notice.set(`${token.name} is now tracked. Its history is being collected in the background.`);
      this.load();
    });
  }

  protected remove(token: TokenSummary): void {
    if (!confirm(`Stop tracking ${token.name} and delete all of its collected data?`)) {
      return;
    }
    this.run(() => this.api.deleteToken(token.assetId), () => {
      this.notice.set(`${token.name} is no longer tracked.`);
      this.load();
    });
  }

  protected syncAll(): void {
    this.run(() => this.api.syncAll(), (results) => {
      const failed = results.filter((r) => r.error);
      this.notice.set(
        failed.length
          ? `Sync finished with ${failed.length} error(s): ${failed.map((r) => r.error).join('; ')}`
          : `Sync finished for ${results.length} token(s).`,
      );
      this.load();
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
