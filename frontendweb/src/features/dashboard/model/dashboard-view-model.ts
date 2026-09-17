import type { DecimalString } from '../../../shared/api/contracts';
import type { AgingDto } from '../../../entities/dashboard/model/dashboard.types';

const decimalPattern = /^(-?)(0|[1-9][0-9]{0,14})(?:\.([0-9]{1,4}))?$/;

function scaledValue(value: DecimalString): bigint | null {
  const match = decimalPattern.exec(value);
  if (!match) return null;
  const fraction = (match[3] ?? '').padEnd(4, '0');
  const magnitude = BigInt(match[2]) * 10_000n + BigInt(fraction || '0');
  return match[1] === '-' ? -magnitude : magnitude;
}

export function sumDecimalStrings(values: readonly DecimalString[]): DecimalString | null {
  let total = 0n;
  for (const value of values) {
    const scaled = scaledValue(value);
    if (scaled === null) return null;
    total += scaled;
  }
  const negative = total < 0n;
  const magnitude = negative ? -total : total;
  const integer = magnitude / 10_000n;
  if (integer.toString().length > 15) return null;
  const fraction = (magnitude % 10_000n).toString().padStart(4, '0').replace(/0+$/, '');
  return `${negative ? '-' : ''}${integer}${fraction ? `.${fraction}` : ''}`;
}

export function overdueAmount(aging: AgingDto): DecimalString | null {
  return sumDecimalStrings([
    aging.days1To30,
    aging.days31To60,
    aging.days61To90,
    aging.days90Plus,
  ]);
}
