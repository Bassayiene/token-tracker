import { AmountPipe, ShortIdPipe, SignedPercentPipe, trendClass } from './formatting';

describe('formatting', () => {
  it('formats amounts', () => {
    const pipe = new AmountPipe();
    expect(pipe.transform(null)).toBe('—');
    expect(pipe.transform(1234567.891)).toBe('1,234,567.89');
    expect(pipe.transform(0.000123456789)).toBe('0.00012345679');
    expect(pipe.transform(-0.5)).toBe('-0.5');
  });

  it('formats signed percentages', () => {
    const pipe = new SignedPercentPipe();
    expect(pipe.transform(1.234)).toBe('+1.23 %');
    expect(pipe.transform(-2)).toBe('-2.00 %');
    expect(pipe.transform(0)).toBe('0.00 %');
    expect(pipe.transform(undefined)).toBe('—');
  });

  it('shortens ids', () => {
    const pipe = new ShortIdPipe();
    expect(pipe.transform('3PAWwWa6GbwcJaFzwqXQN5KQm7H96Y7SHTQ')).toBe('3PAWwW…SHTQ');
    expect(pipe.transform('short')).toBe('short');
  });

  it('classifies trends', () => {
    expect(trendClass(1)).toBe('up');
    expect(trendClass(-1)).toBe('down');
    expect(trendClass(0)).toBe('');
    expect(trendClass(null)).toBe('');
  });
});
