import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { storeSession } from '../testing/session';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpTestingController;

  function setup(): HttpClient {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(HttpClient);
  }

  beforeEach(() => localStorage.clear());

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('sends no header for visitors', () => {
    setup().get('/api/tokens').subscribe();
    expect(http.expectOne('/api/tokens').request.headers.has('Authorization')).toBe(false);
  });

  it('sends the token on API calls, but not on login', () => {
    const session = storeSession('USER');
    const client = setup();
    client.get('/api/tokens').subscribe();
    expect(http.expectOne('/api/tokens').request.headers.get('Authorization')).toBe(
      `Bearer ${session.token}`,
    );
    client.post('/api/auth/login', {}).subscribe();
    expect(http.expectOne('/api/auth/login').request.headers.has('Authorization')).toBe(false);
  });

  it('drops a rejected session and replays reads as a visitor', () => {
    storeSession('USER');
    const client = setup();
    let result: unknown;
    client.get('/api/tokens').subscribe((r) => (result = r));

    http.expectOne('/api/tokens').flush({}, { status: 401, statusText: 'Unauthorized' });
    const retry = http.expectOne('/api/tokens');
    expect(retry.request.headers.has('Authorization')).toBe(false);
    retry.flush([]);

    expect(result).toEqual([]);
    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(false);
  });

  it('does not replay rejected writes', () => {
    storeSession('ADMIN');
    const client = setup();
    let failed = false;
    client.post('/api/tokens/sync', null).subscribe({ error: () => (failed = true) });
    http.expectOne('/api/tokens/sync').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(failed).toBe(true);
  });
});
