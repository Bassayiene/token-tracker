import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { TokenSummary, TopHolders } from '../../core/models';
import { storeSession } from '../../testing/session';
import { TokenDetail } from './token-detail';

const ASSET = 'bPWkA3MNyEr1TuDchWgdpqJZhGhfPXj7dJdr3qiW2kD';
const ADDRESS = '3PRCoguiVX33Fj6z46qgxheib6K1267W7Zr';

const TOKEN = {
  assetId: ASSET,
  name: 'TurtleNetwork',
  ticker: 'TN',
  decimals: 8,
  priceAsset: 'WAVES',
} as TokenSummary;

const HOLDERS: TopHolders = {
  date: '2026-09-30',
  previousDate: null,
  height: 5424646,
  totalQuantity: 95193321.74608945,
  holders: [
    { rank: 1, address: ADDRESS, balance: 1000, share: 12.5, balanceChange: null, rankChange: null, isNew: false },
  ],
  availableDates: ['2026-09-30'],
};

describe('TokenDetail', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [TokenDetail],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('links each top holder address to wscan.io in a new tab', async () => {
    storeSession('USER');
    const fixture = TestBed.createComponent(TokenDetail);
    fixture.componentRef.setInput('assetId', ASSET);
    fixture.detectChanges();

    http.expectOne(`/api/tokens/${ASSET}`).flush(TOKEN);
    // Empty series: no chart is drawn (jsdom has no canvas)
    http.expectOne((r) => r.url === `/api/tokens/${ASSET}/prices`).flush([]);
    http.expectOne((r) => r.url === `/api/tokens/${ASSET}/quantities`).flush([]);
    http.expectOne((r) => r.url === `/api/tokens/${ASSET}/holder-stats`).flush([]);
    http.expectOne(`/api/tokens/${ASSET}/holders`).flush(HOLDERS);
    await fixture.whenStable();

    const link = (fixture.nativeElement as HTMLElement).querySelector(
      'tbody td.mono a',
    ) as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe(`https://wscan.io/${ADDRESS}`);
    expect(link.target).toBe('_blank');
    expect(link.rel).toContain('noopener');
  });
});
