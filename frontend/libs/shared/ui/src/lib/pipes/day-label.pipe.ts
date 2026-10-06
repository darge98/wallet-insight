import { Pipe, PipeTransform } from '@angular/core';

import { IsoDate } from '@wallet/shared-domain';
import { formatCompactDate, formatDayLabel, formatLongDate } from '../format/date-format';

export type DateFormat = 'day' | 'compact' | 'long';

@Pipe({ name: 'dayLabel' })
export class DayLabelPipe implements PipeTransform {
  transform(value: IsoDate | null | undefined, format: DateFormat = 'day'): string {
    if (!value) {
      return '—';
    }

    switch (format) {
      case 'compact':
        return formatCompactDate(value);
      case 'long':
        return formatLongDate(value);
      case 'day':
        return formatDayLabel(value);
    }
  }
}
