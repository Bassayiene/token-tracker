import { TokenPrice } from '../../core/models';
import { crossRates, periodChange } from './cross-rate';

function price(time: string, value: number): TokenPrice {
  return { time, price: value, volume: 0, hasTrades: true };
}

describe('crossRates', () => {
  it('divides the base price by the quote price of the same hour', () => {
    const base = [price('2026-09-30T10:00:00Z', 0.5), price('2026-09-30T11:00:00Z', 0.75)];
    const quote = [price('2026-09-30T10:00:00Z', 0.25), price('2026-09-30T11:00:00Z', 0.25)];
    expect(crossRates(base, quote)).toEqual([2, 3]);
  });

  it('matches hours by instant, not by string or position', () => {
    const base = [price('2026-09-30T10:00:00Z', 1), price('2026-09-30T11:00:00Z', 1)];
    const quote = [price('2026-09-30T11:00:00.000Z', 4)];
    expect(crossRates(base, quote)).toEqual([null, 0.25]);
  });

  it('leaves a gap when the quote price is zero', () => {
    expect(crossRates([price('2026-09-30T10:00:00Z', 1)], [price('2026-09-30T10:00:00Z', 0)])).toEqual([null]);
  });

  it('leaves a gap when a token never traded (null price)', () => {
    const never = { time: '2026-09-30T10:00:00Z', price: null, volume: 0, hasTrades: false };
    expect(crossRates([price('2026-09-30T10:00:00Z', 1)], [never])).toEqual([null]);
    expect(crossRates([never], [price('2026-09-30T10:00:00Z', 1)])).toEqual([null]);
  });
});

describe('periodChange', () => {
  it('compares the first and last known values', () => {
    expect(periodChange([null, 2, null, 3, null])).toBe(50);
  });

  it('is null without two known values', () => {
    expect(periodChange([null, 2])).toBeNull();
    expect(periodChange([])).toBeNull();
  });
});
