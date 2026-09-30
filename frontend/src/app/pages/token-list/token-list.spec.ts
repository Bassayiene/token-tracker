import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { TokenSummary } from '../../core/models';
import { storeSession } from '../../testing/session';
import { TokenList } from './token-list';

const TN: TokenSummary = {
  assetId: 'HxxSmVuX4HbHDiDSCLTBgK7SwXhQtaL4g1UpHLMwvxw6',
  name: 'TurtleNetwork',
  ticker: 'TN',
  decimals: 2,
  description: null,
  createdAt: null,
  reissuable: false,
  hasScript: false,
  priceAsset: 'WAVES',
  lastPrice: 0.0012,
  lastPriceTime: '2026-09-30T14:00:00Z',
  lastTradeTime: '2026-09-30T13:00:00Z',
  change24h: 2.5,
  change7d: -1.25,
  quantity: 1000000,
  quantityDate: '2026-09-30',
  holdersCount: 4200,
  top10Share: 55.5,
  top100Share: 80,
  holdersDate: '2026-09-30',
};

describe('TokenList', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [TokenList],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('shows visitors the prices only, without management actions', async () => {
    const fixture = TestBed.createComponent(TokenList);
    fixture.detectChanges();
    http.expectOne('/api/tokens').flush([{ ...TN, holdersCount: null, top10Share: null }]);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('form')).toBeNull();
    expect(el.textContent).not.toContain('Sync all now');
    expect(el.textContent).not.toContain('Remove');
    expect(el.querySelector('thead')?.textContent).not.toContain('Holders');
    expect(el.textContent).toContain('create an account');
  });

  it('shows users the holder columns but no management actions', async () => {
    storeSession('USER');
    const fixture = TestBed.createComponent(TokenList);
    fixture.detectChanges();
    http.expectOne('/api/tokens').flush([TN]);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('thead')?.textContent).toContain('Holders');
    expect(el.querySelector('tbody')?.textContent).toContain('4200');
    expect(el.querySelector('form')).toBeNull();
    expect(el.textContent).not.toContain('Remove');
  });

  it('lists the tracked tokens', async () => {
    const fixture = TestBed.createComponent(TokenList);
    fixture.detectChanges();
    http.expectOne('/api/tokens').flush([TN]);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    const rows = el.querySelectorAll('tbody tr');
    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('TurtleNetwork');
    expect(rows[0].textContent).toContain('+2.50 %');
    expect(rows[0].querySelector('.down')?.textContent).toContain('-1.25 %');
  });

  it('does not post an invalid asset id', async () => {
    storeSession('ADMIN');
    const fixture = TestBed.createComponent(TokenList);
    fixture.detectChanges();
    http.expectOne('/api/tokens').flush([]);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    const input = el.querySelector('input') as HTMLInputElement;
    input.value = 'not-an-id';
    input.dispatchEvent(new Event('input'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    http.expectNone({ method: 'POST', url: '/api/tokens' });
    expect(el.querySelector('.field-error')).not.toBeNull();
  });

  it('adds a valid asset id and reloads the list', async () => {
    storeSession('ADMIN');
    const fixture = TestBed.createComponent(TokenList);
    fixture.detectChanges();
    http.expectOne('/api/tokens').flush([]);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    const input = el.querySelector('input') as HTMLInputElement;
    input.value = TN.assetId;
    input.dispatchEvent(new Event('input'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));

    const post = http.expectOne({ method: 'POST', url: '/api/tokens' });
    expect(post.request.body).toEqual({ assetId: TN.assetId });
    post.flush(TN);
    http.expectOne({ method: 'GET', url: '/api/tokens' }).flush([TN]);
    await fixture.whenStable();

    expect(el.querySelector('.alert.info')?.textContent).toContain('TurtleNetwork is now tracked');
    expect(el.querySelectorAll('tbody tr').length).toBe(1);
  });
});
