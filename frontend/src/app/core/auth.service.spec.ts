import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { storeSession } from '../testing/session';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('starts as a visitor', () => {
    const auth = TestBed.inject(AuthService);
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.isAdmin()).toBe(false);
  });

  it('restores a stored session that has not expired', () => {
    storeSession('ADMIN');
    const auth = TestBed.inject(AuthService);
    expect(auth.isAdmin()).toBe(true);
  });

  it('ignores an expired stored session', () => {
    storeSession('USER', -1000);
    const auth = TestBed.inject(AuthService);
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('logs in, stores the session, and logs out', () => {
    const auth = TestBed.inject(AuthService);
    auth.login('alice', 'secret-password').subscribe();
    const req = http.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ username: 'alice', password: 'secret-password' });
    req.flush({
      token: 'jwt',
      username: 'alice',
      role: 'USER',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    });

    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.isAdmin()).toBe(false);
    expect(localStorage.getItem('token-tracker.session')).toContain('"jwt"');

    auth.logout();
    expect(auth.isAuthenticated()).toBe(false);
    expect(localStorage.getItem('token-tracker.session')).toBeNull();
  });

  it('flags a session rejected by the server as expired', () => {
    storeSession('USER');
    const auth = TestBed.inject(AuthService);
    auth.expire();
    expect(auth.isAuthenticated()).toBe(false);
    expect(auth.expired()).toBe(true);
  });
});
