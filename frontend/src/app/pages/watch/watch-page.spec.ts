import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { WatchedAddress } from '../../core/watch-api.service';
import { storeSession } from '../../testing/session';
import { WatchPage } from './watch-page';

const ADDRESS = '3PRCoguiVX33Fj6z46qgxheib6K1267W7Zr';
const VARIATIONS_URL = '/api/watched-addresses/7/history/variations';

const WATCHED: WatchedAddress = {
  id: 7,
  address: ADDRESS,
  label: 'Treasury',
  createdAt: '2026-10-11T08:00:00Z',
  lastCheckedAt: '2026-10-11T09:10:00Z',
  lastError: null,
  assetsCount: 2,
};

describe('WatchPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [WatchPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  /** Renders the page for an admin and answers its two initial requests. */
  async function openAsAdmin(
    addresses: WatchedAddress[],
    telegramConfigured = true,
  ): Promise<ComponentFixture<WatchPage>> {
    storeSession('ADMIN');
    const fixture = TestBed.createComponent(WatchPage);
    fixture.detectChanges();
    http.expectOne('/api/watched-addresses').flush(addresses);
    http.expectOne('/api/watched-addresses/telegram').flush({ configured: telegramConfigured });
    await fixture.whenStable();
    return fixture;
  }

  it('asks nothing from the server for non-admins', async () => {
    storeSession('USER');
    const fixture = TestBed.createComponent(WatchPage);
    fixture.detectChanges();
    await fixture.whenStable();

    http.expectNone('/api/watched-addresses');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('reserved to the administrator');
  });

  it('lists the watched addresses with a link to the explorer', async () => {
    const fixture = await openAsAdmin([WATCHED]);
    const el = fixture.nativeElement as HTMLElement;

    const row = el.querySelector('tbody tr')!;
    expect(row.textContent).toContain('Treasury');
    expect(row.textContent).toContain('OK');
    expect(row.querySelector('a')?.getAttribute('href')).toBe(`https://wscan.io/${ADDRESS}`);
    expect(el.textContent).toContain('Telegram notifications are configured');
  });

  it('warns when Telegram is not configured and shows the last error of an address', async () => {
    const fixture = await openAsAdmin(
      [{ ...WATCHED, lastError: 'Telegram: Unable to reach Telegram' }],
      false,
    );
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('.alert.error')?.textContent).toContain('TELEGRAM_BOT_TOKEN');
    expect(el.textContent).not.toContain('Send a test message');
    expect(el.querySelector('tbody .down')?.textContent).toContain('Unable to reach Telegram');
  });

  it('does not post an invalid address', async () => {
    const fixture = await openAsAdmin([]);
    const el = fixture.nativeElement as HTMLElement;

    const input = el.querySelector('#address') as HTMLInputElement;
    input.value = 'not-an-address';
    input.dispatchEvent(new Event('input'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    http.expectNone({ method: 'POST', url: '/api/watched-addresses' });
    expect(el.querySelector('.field-error')).not.toBeNull();
  });

  it('adds an address with its label and reloads the list', async () => {
    const fixture = await openAsAdmin([]);
    const el = fixture.nativeElement as HTMLElement;

    const address = el.querySelector('#address') as HTMLInputElement;
    address.value = ADDRESS;
    address.dispatchEvent(new Event('input'));
    const label = el.querySelector('#label') as HTMLInputElement;
    label.value = ' Treasury ';
    label.dispatchEvent(new Event('input'));
    el.querySelector('form')!.dispatchEvent(new Event('submit'));

    const post = http.expectOne({ method: 'POST', url: '/api/watched-addresses' });
    expect(post.request.body).toEqual({ address: ADDRESS, label: 'Treasury' });
    post.flush(WATCHED);
    http.expectOne({ method: 'GET', url: '/api/watched-addresses' }).flush([WATCHED]);
    await fixture.whenStable();

    expect(el.querySelector('.alert.info')?.textContent).toContain('Now watching Treasury');
    expect(el.querySelectorAll('tbody tr').length).toBe(1);
  });

  it('shows the variations and balances of the selected address', async () => {
    const fixture = await openAsAdmin([WATCHED]);
    const el = fixture.nativeElement as HTMLElement;

    const details = [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Details'))!;
    details.click();
    http.expectOne('/api/watched-addresses/7/changes').flush([
      {
        id: 1,
        assetId: 'WAVES',
        assetName: 'WAVES',
        oldBalance: 1,
        newBalance: 0.5,
        delta: -0.5,
        newAsset: false,
        detectedAt: '2026-10-11T09:10:00Z',
        notified: true,
      },
    ]);
    http
      .expectOne('/api/watched-addresses/7/balances')
      .flush([{ assetId: 'WAVES', assetName: 'WAVES', balance: 0.5, updatedAt: '2026-10-11T09:10:00Z' }]);
    http.expectOne('/api/watched-addresses/7/history/assets').flush([]);
    http.expectOne((r) => r.url === VARIATIONS_URL).flush([]);
    await fixture.whenStable();

    expect(el.textContent).toContain('Latest variations of Treasury');
    expect(el.querySelector('section .down')?.textContent).toContain('-0.5');
    expect(el.textContent).toContain('sent');
    expect(el.textContent).toContain('Current balances');
    expect(el.textContent).toContain('No daily balance recorded for this period yet.');
  });

  it('charts the daily balance of the chosen asset, including assets no longer held', async () => {
    const fixture = await openAsAdmin([WATCHED]);
    const el = fixture.nativeElement as HTMLElement;

    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Details'))!.click();
    http.expectOne('/api/watched-addresses/7/changes').flush([]);
    http.expectOne('/api/watched-addresses/7/balances').flush([]);
    http.expectOne((r) => r.url === VARIATIONS_URL).flush([]);
    http.expectOne('/api/watched-addresses/7/history/assets').flush([
      { assetId: 'WAVES', assetName: 'WAVES', firstDate: '2026-10-10', lastDate: '2026-10-11' },
      { assetId: 'TN', assetName: 'TurtleNetwork', firstDate: '2026-10-10', lastDate: '2026-10-10' },
    ]);

    // WAVES, listed first, is charted by default over the last 90 days
    const waves = http.expectOne((r) => r.url === '/api/watched-addresses/7/history');
    expect(waves.request.params.get('assetId')).toBe('WAVES');
    expect(waves.request.params.get('from')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    waves.flush([
      { date: '2026-10-10', balance: 1 },
      { date: '2026-10-11', balance: 0.5 },
    ]);
    await fixture.whenStable();

    expect(el.querySelector('app-line-chart')).not.toBeNull();
    expect(el.textContent).toContain('2 day(s) recorded');
    const select = el.querySelector('select') as HTMLSelectElement;
    expect([...select.options].map((o) => o.textContent)).toEqual(['WAVES', 'TurtleNetwork']);

    select.value = 'TN';
    select.dispatchEvent(new Event('change'));
    const tn = http.expectOne((r) => r.url === '/api/watched-addresses/7/history');
    expect(tn.request.params.get('assetId')).toBe('TN');
    tn.flush([{ date: '2026-10-10', balance: 0 }]);
    await fixture.whenStable();

    expect(el.textContent).toContain('1 day(s) recorded');
  });

  it('charts the variation of the assets that moved, in percent of their first tracked day', async () => {
    const fixture = await openAsAdmin([WATCHED]);
    const el = fixture.nativeElement as HTMLElement;

    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Details'))!.click();
    http.expectOne('/api/watched-addresses/7/changes').flush([]);
    http.expectOne('/api/watched-addresses/7/balances').flush([]);
    http.expectOne('/api/watched-addresses/7/history/assets').flush([]);
    const request = http.expectOne((r) => r.url === VARIATIONS_URL);
    expect(request.request.params.get('from')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    request.flush([
      {
        assetId: 'FOO',
        assetName: 'Foo',
        baseDate: '2026-10-09',
        baseBalance: 100,
        lastPercent: 75,
        points: [
          { date: '2026-10-09', percent: 0 },
          { date: '2026-10-10', percent: 75 },
        ],
      },
      {
        assetId: 'WAVES',
        assetName: 'WAVES',
        baseDate: '2026-10-09',
        baseBalance: 2,
        lastPercent: -50,
        points: [
          { date: '2026-10-09', percent: 0 },
          { date: '2026-10-11', percent: -50 },
        ],
      },
    ]);
    await fixture.whenStable();

    const rows = el.querySelectorAll('table.variations tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Foo');
    expect(rows[0].querySelector('.up')?.textContent).toContain('+75.00 %');
    expect(rows[1].querySelector('.down')?.textContent).toContain('-50.00 %');
    expect(rows[1].textContent).toContain('2026-10-09');

    // Changing the period reloads the variations over the new period
    [...el.querySelectorAll('.segmented button')].find((b) => b.textContent?.trim() === '30d')!
      .dispatchEvent(new Event('click'));
    http.expectOne((r) => r.url === VARIATIONS_URL).flush([]);
    await fixture.whenStable();

    expect(el.textContent).toContain('No asset has moved from its first tracked balance');
  });
});
