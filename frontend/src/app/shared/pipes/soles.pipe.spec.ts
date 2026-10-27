import { DurationPipe, SolesPipe } from './soles.pipe';

describe('SolesPipe', () => {
  const pipe = new SolesPipe();
  it('formatea importes en soles con dos decimales', () => {
    expect(pipe.transform(15).replace(/\s/g, ' ')).toBe('S/ 15.00');
    expect(pipe.transform(1234.5).replace(/\s/g, ' ')).toBe('S/ 1,234.50');
    expect(pipe.transform(null)).toBe('');
  });
});

describe('DurationPipe', () => {
  const pipe = new DurationPipe();
  it.each([
    [30, '1 mes'],
    [90, '3 meses'],
    [365, '1 año'],
    [730, '2 años'],
    [45, '45 días'],
    [1, '1 día'],
    [null, 'Sin duración'],
  ])('%s → %s', (days, text) => {
    expect(pipe.transform(days)).toBe(text);
  });
});
