import { Component, computed, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../../core/token-api.service';

/** Same rules as the backend `RegisterRequest`. */
const USERNAME_PATTERN = /^[A-Za-z0-9._-]{3,50}$/;
const PASSWORD_MIN = 8;

/** Log in or sign up, depending on the route data `mode`. */
@Component({
  selector: 'app-auth-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './auth-page.html',
})
export class AuthPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Route data, bound through `withComponentInputBinding()`. */
  readonly mode = input<'login' | 'register'>('login');
  /** Page to go back to after logging in (query parameter). */
  readonly returnUrl = input<string>();

  protected readonly isRegister = computed(() => this.mode() === 'register');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly expired = this.auth.expired;
  protected readonly passwordMin = PASSWORD_MIN;

  protected readonly form = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    confirm: new FormControl('', { nonNullable: true }),
  });

  protected submit(): void {
    const { username, password, confirm } = this.form.getRawValue();
    const problem = this.validate(username.trim(), password, confirm);
    if (problem) {
      this.error.set(problem);
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const call = this.isRegister()
      ? this.auth.register(username.trim(), password)
      : this.auth.login(username.trim(), password);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.router.navigateByUrl(safeReturnUrl(this.returnUrl()));
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(errorMessage(err));
      },
    });
  }

  private validate(username: string, password: string, confirm: string): string | null {
    if (!username || !password) {
      return 'Enter a username and a password.';
    }
    if (!this.isRegister()) {
      return null;
    }
    if (!USERNAME_PATTERN.test(username)) {
      return "The username must be 3 to 50 letters, digits, '.', '_' or '-'.";
    }
    if (password.length < PASSWORD_MIN) {
      return `The password must be at least ${PASSWORD_MIN} characters.`;
    }
    if (password !== confirm) {
      return 'The two passwords do not match.';
    }
    return null;
  }
}

/** Only in-app paths, so a crafted link cannot send the user to another site after login. */
function safeReturnUrl(url: string | undefined): string {
  return url && url.startsWith('/') && !url.startsWith('//') ? url : '/';
}
