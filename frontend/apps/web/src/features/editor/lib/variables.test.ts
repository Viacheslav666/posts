import { describe, expect, it } from 'vitest';

import {
  filterVariables,
  openBraceQuery,
  renderEditorVariables,
} from './variables';

describe('variables', () => {
  it('renders date/time in Europe/Moscow', () => {
    // 2026-11-18T12:00:00Z = 15:00 Moscow
    const out = renderEditorVariables('{date} {time} {weekday} {day} {month} {year}', {
      at: '2026-11-18T12:00:00.000Z',
      timezone: 'Europe/Moscow',
      spintaxSeed: 'fixed',
    });
    expect(out).toContain('18.11.2026');
    expect(out).toContain('15:00');
    expect(out).toContain('среда');
    expect(out).toContain('18');
    expect(out).toContain('ноября');
    expect(out).toContain('2026');
  });

  it('renders group and birthdays tags', () => {
    const out = renderEditorVariables('id={group_id} name={group_name} b={именинники}', {
      groupId: '-123',
      groupName: 'Тест',
      birthdays: 'Анна, Борис',
      spintaxSeed: 'x',
    });
    expect(out).toBe('id=-123 name=Тест b=Анна, Борис');
  });

  it('expands hyperlink and json', () => {
    const out = renderEditorVariables(
      '{hyperlink:Купить|https://ex.com} {json:offer.price}',
      {
        json: { offer: { price: '100' } },
        spintaxSeed: 'x',
      },
    );
    expect(out).toContain('Купить (https://ex.com)');
    expect(out).toContain('100');
  });

  it('expands [rand] after variables', () => {
    const out = renderEditorVariables('Сегодня {date}: [rand]a|b[/rand]', {
      at: '2026-01-01T00:00:00.000Z',
      timezone: 'UTC',
      spintaxSeed: 'seed',
    });
    expect(out).toMatch(/Сегодня 01\.01\.2026: [ab]/);
  });

  it('filters catalog and detects open brace', () => {
    expect(filterVariables('group').some((v) => v.id === 'group_id')).toBe(true);
    expect(openBraceQuery('hello {da')).toBe('da');
    expect(openBraceQuery('hello {date}')).toBeNull();
  });
});
