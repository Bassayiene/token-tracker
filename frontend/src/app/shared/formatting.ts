import { Pipe, PipeTransform } from '@angular/core';

/** Number with up to `maxDigits` significant digits, `—` when absent. */
@Pipe({ name: 'amount' })
export class AmountPipe implements PipeTransform {
  transform(value: number | null | undefined, maxDigits = 8): string {
    if (value === null || value === undefined) {
      return '—';
    }
    const options: Intl.NumberFormatOptions =
      Math.abs(value) >= 1 ? { maximumFractionDigits: 2 } : { maximumSignificantDigits: maxDigits };
    return new Intl.NumberFormat('en-US', options).format(value);
  }
}

/** Percentage with an explicit sign, e.g. `+1.25 %`, `—` when absent. */
@Pipe({ name: 'signedPercent' })
export class SignedPercentPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return '—';
    }
    const sign = value > 0 ? '+' : '';
    return `${sign}${value.toFixed(2)} %`;
  }
}

/** CSS class for a signed variation. */
export function trendClass(value: number | null | undefined): string {
  if (!value) {
    return '';
  }
  return value > 0 ? 'up' : 'down';
}

/** Shortens a long base58 id to `abcdef…wxyz`. */
@Pipe({ name: 'shortId' })
export class ShortIdPipe implements PipeTransform {
  transform(value: string | null | undefined, head = 6, tail = 4): string {
    if (!value) {
      return '';
    }
    return value.length <= head + tail + 1 ? value : `${value.slice(0, head)}…${value.slice(-tail)}`;
  }
}
