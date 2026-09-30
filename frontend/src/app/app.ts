import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';

import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
})
export class App {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Current page, so logging in comes back to it. */
  protected returnUrl(): string {
    return this.router.url.startsWith('/login') || this.router.url.startsWith('/register') ? '/' : this.router.url;
  }

  protected logout(): void {
    this.auth.logout();
  }
}
