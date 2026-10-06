import { describe, expect, it } from 'vitest';

import { formatDaysUntil } from './subscription-format';

describe('formatDaysUntil', () => {
  it('dice quanto manca all’addebito', () => {
    expect(formatDaysUntil('2026-10-05', '2026-10-05')).toBe('oggi');
    expect(formatDaysUntil('2026-10-05', '2026-10-06')).toBe('domani');
    expect(formatDaysUntil('2026-10-05', '2026-10-20')).toBe('tra 15 giorni');
  });
});
