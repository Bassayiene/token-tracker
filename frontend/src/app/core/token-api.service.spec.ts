import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TokenApiService, errorMessage } from './token-api.service';

describe('TokenApiService', () => {
  let api: TokenApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(TokenApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends only the given range bounds', () => {
    api.getPrices('abc', { from: '2026-09-01T00:00:00Z' }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/tokens/abc/prices');
    expect(req.request.params.get('from')).toBe('2026-09-01T00:00:00Z');
    expect(req.request.params.has('to')).toBe(false);
    req.flush([]);
  });

  it('asks for the latest holders snapshot when no date is given', () => {
    api.getTopHolders('abc').subscribe();
    const req = http.expectOne('/api/tokens/abc/holders');
    expect(req.request.params.keys()).toEqual([]);
    req.flush({ holders: [], availableDates: [] });
  });

  it('posts the asset id when adding a token', () => {
    api.addToken('abc').subscribe();
    const req = http.expectOne('/api/tokens');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ assetId: 'abc' });
    req.flush({});
  });

  it('syncs one token', () => {
    api.syncOne('abc').subscribe();
    const req = http.expectOne('/api/tokens/abc/sync');
    expect(req.request.method).toBe('POST');
    req.flush({ assetId: 'abc', priceHours: 0, dailyDone: true, error: null });
  });
});

describe('errorMessage', () => {
  it('uses the backend message', () => {
    const err = new HttpErrorResponse({
      status: 400,
      error: { message: 'assetId: must be a base58 Waves asset id' },
    });
    expect(errorMessage(err)).toBe('assetId: must be a base58 Waves asset id');
  });

  it('reports an unreachable server', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toBe('The server cannot be reached.');
  });

  it('falls back to the status', () => {
    const err = new HttpErrorResponse({ status: 502, statusText: 'Bad Gateway' });
    expect(errorMessage(err)).toBe('Request failed (502 Bad Gateway).');
  });
});
