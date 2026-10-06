import { Pipe, PipeTransform } from '@angular/core';

import { Money } from '@wallet/shared-domain';
import { formatMoney } from '../format/format';

export type MoneyFormat = 'plain' | 'signed' | 'compact' | 'signed-compact' | 'rounded';

/**
 * Formatta un `Money` secondo il locale dell'app.
 *
 * Il formato è passato come stringa (non come oggetto) per non invalidare la
 * pipe pura a ogni ciclo di change detection.
 */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(value: Money | null | undefined, format: MoneyFormat = 'plain'): string {
    if (!value) {
      return '—';
    }

    switch (format) {
      case 'signed':
        return formatMoney(value, { signed: true });
      case 'compact':
        return formatMoney(value, { compact: true });
      case 'signed-compact':
        return formatMoney(value, { signed: true, compact: true });
      case 'rounded':
        return formatMoney(value, { decimals: false });
      case 'plain':
        return formatMoney(value);
    }
  }
}
