import { nextLotConsecutive } from './lot-consecutive';

describe('nextLotConsecutive', () => {
  it('starts the block at 1 with the stage and block codes', () => {
    expect(nextLotConsecutive([], 'e02', 'M03')).toEqual({ numero: '1', codigo: 'E02-M03-L01' });
  });

  it('continues after the highest number and keeps the code pattern and padding', () => {
    const lots = [
      { numero: '7', codigo: 'E02-M03-L07' },
      { numero: '24', codigo: 'E02-M03-L24' },
      { numero: '3', codigo: 'E02-M03-L03' },
    ];
    expect(nextLotConsecutive(lots, 'E02', 'M03')).toEqual({ numero: '25', codigo: 'E02-M03-L25' });
  });

  it('follows a custom pattern from the last lot', () => {
    const lots = [{ numero: '9', codigo: 'A-009' }];
    expect(nextLotConsecutive(lots, 'E1', 'A')).toEqual({ numero: '10', codigo: 'A-010' });
  });

  it('ignores non-numeric numbers and falls back to the codes when no code ends in digits', () => {
    const lots = [{ numero: 'Esquina', codigo: 'ESQ' }];
    expect(nextLotConsecutive(lots, 'E1', 'MA')).toEqual({ numero: '1', codigo: 'E1-MA-L01' });
  });
});
