import { TokenPrice } from '../../core/models';

/**
 * Price of the base token expressed in the quote token, hour by hour, from two price series in the same asset
 * (WAVES): `base / quote`. This is a synthetic rate through WAVES, not a traded market pair.
 *
 * @returns one value per base hour; null when either token has no price, or the quote price is zero, for that hour
 */
export function crossRates(base: TokenPrice[], quote: TokenPrice[]): (number | null)[] {
  const quoteByHour = new Map(quote.map((q) => [Date.parse(q.time), q.price]));
  return base.map((b) => {
    const q = quoteByHour.get(Date.parse(b.time));
    return b.price !== null && q != null && q > 0 ? b.price / q : null;
  });
}

/** Variation in percent between the first and the last known value, null when not computable. */
export function periodChange(values: (number | null)[]): number | null {
  const known = values.filter((v): v is number => v !== null);
  if (known.length < 2 || known[0] === 0) {
    return null;
  }
  return ((known[known.length - 1] - known[0]) / known[0]) * 100;
}
