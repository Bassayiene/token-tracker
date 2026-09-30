import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

export type Role = 'USER' | 'ADMIN';

/** Response of `/api/auth/login` and `/api/auth/register`. */
export interface Session {
  token: string;
  username: string;
  role: Role;
  expiresAt: string;
}

const STORAGE_KEY = 'token-tracker.session';

/**
 * Current session. Visitors have none; users read all data; admins also manage tokens and syncs.
 * The session is kept in localStorage so it survives reloads, and dropped when its token expires.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly current = signal<Session | null>(null);
  private expiryTimer?: ReturnType<typeof setTimeout>;

  readonly session = this.current.asReadonly();
  readonly isAuthenticated = computed(() => this.current() !== null);
  readonly isAdmin = computed(() => this.current()?.role === 'ADMIN');
  /** Set when the session ended because the token expired or was rejected. */
  readonly expired = signal(false);

  constructor() {
    this.set(readStored());
  }

  login(username: string, password: string): Observable<Session> {
    return this.http
      .post<Session>('/api/auth/login', { username, password })
      .pipe(tap((session) => this.start(session)));
  }

  register(username: string, password: string): Observable<Session> {
    return this.http
      .post<Session>('/api/auth/register', { username, password })
      .pipe(tap((session) => this.start(session)));
  }

  logout(): void {
    this.set(null);
  }

  /** Ends a session the server no longer accepts. */
  expire(): void {
    if (this.current()) {
      this.expired.set(true);
      this.set(null);
    }
  }

  private start(session: Session): void {
    this.expired.set(false);
    this.set(session);
  }

  private set(session: Session | null): void {
    clearTimeout(this.expiryTimer);
    this.current.set(session);
    try {
      if (session) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
      } else {
        localStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      // Storage unavailable (private mode...): the session only lasts until reload.
    }
    if (session) {
      // setTimeout overflows above ~24.8 days; tokens live far less than that.
      const delay = Math.min(Date.parse(session.expiresAt) - Date.now(), 2 ** 31 - 1);
      this.expiryTimer = setTimeout(() => this.expire(), delay);
    }
  }
}

function readStored(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const session = JSON.parse(raw) as Session;
    return session.token && Date.parse(session.expiresAt) > Date.now() ? session : null;
  } catch {
    return null;
  }
}
