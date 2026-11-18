import { expandSpintax } from './spintax';

export type EditorVariableId =
  | 'date'
  | 'time'
  | 'weekday'
  | 'day'
  | 'month'
  | 'year'
  | 'group_id'
  | 'group_name'
  | 'birthdays'
  | 'именинники'
  | 'hyperlink'
  | 'json'
  | 'rand';

export type EditorVariableDef = {
  id: EditorVariableId;
  /** Text inserted into the editor. */
  insert: string;
  /** Autocomplete trigger label (without braces for simple tags). */
  label: string;
  /** i18n key under editor.variables.items.* */
  descriptionKey: string;
};

/** Catalog for insert menu + `{` autocomplete (FE-18). */
export const EDITOR_VARIABLES: EditorVariableDef[] = [
  { id: 'date', insert: '{date}', label: '{date}', descriptionKey: 'date' },
  { id: 'time', insert: '{time}', label: '{time}', descriptionKey: 'time' },
  { id: 'weekday', insert: '{weekday}', label: '{weekday}', descriptionKey: 'weekday' },
  { id: 'day', insert: '{day}', label: '{day}', descriptionKey: 'day' },
  { id: 'month', insert: '{month}', label: '{month}', descriptionKey: 'month' },
  { id: 'year', insert: '{year}', label: '{year}', descriptionKey: 'year' },
  { id: 'group_id', insert: '{group_id}', label: '{group_id}', descriptionKey: 'group_id' },
  {
    id: 'group_name',
    insert: '{group_name}',
    label: '{group_name}',
    descriptionKey: 'group_name',
  },
  {
    id: 'birthdays',
    insert: '{birthdays}',
    label: '{birthdays}',
    descriptionKey: 'birthdays',
  },
  {
    id: 'именинники',
    insert: '{именинники}',
    label: '{именинники}',
    descriptionKey: 'imeninniki',
  },
  {
    id: 'hyperlink',
    insert: '{hyperlink:Текст|https://example.com}',
    label: '{hyperlink:…}',
    descriptionKey: 'hyperlink',
  },
  {
    id: 'json',
    insert: '{json:path.to.value}',
    label: '{json:…}',
    descriptionKey: 'json',
  },
  {
    id: 'rand',
    insert: '[rand]вариант 1|вариант 2[/rand]',
    label: '[rand]',
    descriptionKey: 'rand',
  },
];

export type VariableRenderContext = {
  at?: Date | string;
  timezone?: string;
  groupId?: string;
  groupName?: string;
  birthdays?: string;
  json?: Record<string, unknown>;
  /** Prefer HTML `<a>` for hyperlinks (tg/max). */
  htmlNetwork?: boolean;
  spintaxSeed?: string;
};

const WEEKDAYS_RU = [
  'воскресенье',
  'понедельник',
  'вторник',
  'среда',
  'четверг',
  'пятница',
  'суббота',
];

const MONTHS_RU = [
  'января',
  'февраля',
  'марта',
  'апреля',
  'мая',
  'июня',
  'июля',
  'августа',
  'сентября',
  'октября',
  'ноября',
  'декабря',
];

function resolveZone(timezone?: string): string {
  if (!timezone?.trim()) return 'Europe/Moscow';
  try {
    Intl.DateTimeFormat(undefined, { timeZone: timezone.trim() });
    return timezone.trim();
  } catch {
    return 'Europe/Moscow';
  }
}

function zonedParts(at: Date, timeZone: string) {
  const fmt = new Intl.DateTimeFormat('en-GB', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    weekday: 'short',
    hour12: false,
  });
  const parts = fmt.formatToParts(at);
  const get = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((p) => p.type === type)?.value ?? '';
  const year = Number(get('year'));
  const month = Number(get('month'));
  const day = Number(get('day'));
  const hour = get('hour').padStart(2, '0');
  const minute = get('minute').padStart(2, '0');
  // weekday via calendar day in zone
  const asUtc = Date.UTC(year, month - 1, day, 12, 0, 0);
  const weekdayIndex = new Date(asUtc).getUTCDay();
  return {
    date: `${String(day).padStart(2, '0')}.${String(month).padStart(2, '0')}.${year}`,
    time: `${hour}:${minute}`,
    weekday: WEEKDAYS_RU[weekdayIndex] ?? '',
    day: String(day),
    month: MONTHS_RU[month - 1] ?? '',
    year: String(year),
  };
}

function lookupJson(root: Record<string, unknown> | undefined, path: string): string {
  if (!root || !path) return '';
  let current: unknown = root;
  for (const part of path.split('.')) {
    if (current && typeof current === 'object' && part in (current as object)) {
      current = (current as Record<string, unknown>)[part];
    } else {
      return '';
    }
  }
  return current == null ? '' : String(current);
}

const SIMPLE =
  /\{(date|time|weekday|day|month|year|group_id|group_name|birthdays|именинники)\}/giu;
const HYPERLINK = /\{(?:hyperlink|link):([^|}]+)\|([^}]+)\}/gi;
const JSON_TAG = /\{json:([a-zA-Z0-9_.\-[\]]+)\}/gi;

/**
 * Client-side variable/[rand] expansion for instant editor preview.
 * Prefer POST /api/v1/posts/preview when online; this mirrors BE-29 for offline/MSW fallback.
 */
export function renderEditorVariables(
  template: string,
  context: VariableRenderContext = {},
): string {
  if (!template) return '';
  const at =
    context.at instanceof Date
      ? context.at
      : context.at
        ? new Date(context.at)
        : new Date();
  const zone = resolveZone(context.timezone);
  const parts = Number.isNaN(at.getTime())
    ? zonedParts(new Date(), zone)
    : zonedParts(at, zone);

  const values: Record<string, string> = {
    date: parts.date,
    time: parts.time,
    weekday: parts.weekday,
    day: parts.day,
    month: parts.month,
    year: parts.year,
    group_id: context.groupId ?? '',
    group_name: context.groupName ?? '',
    birthdays: context.birthdays ?? '',
    именинники: context.birthdays ?? '',
  };

  let result = template.replace(SIMPLE, (_m, key: string) => {
    const k = key.toLowerCase();
    if (key === 'именинники' || k === 'именинники') {
      return context.birthdays ?? '';
    }
    return values[k] ?? '';
  });

  result = result.replace(HYPERLINK, (_m, text: string, url: string) => {
    const t = text.trim();
    const u = url.trim();
    return context.htmlNetwork
      ? `<a href="${u.replace(/"/g, '&quot;')}">${t}</a>`
      : `${t} (${u})`;
  });

  result = result.replace(JSON_TAG, (_m, path: string) => lookupJson(context.json, path));

  return expandSpintax(result, context.spintaxSeed ?? 'preview');
}

export function filterVariables(query: string): EditorVariableDef[] {
  const q = query.trim().toLowerCase().replace(/^\{/, '');
  if (!q) return EDITOR_VARIABLES;
  return EDITOR_VARIABLES.filter(
    (item) =>
      item.label.toLowerCase().includes(q) ||
      item.id.toLowerCase().includes(q) ||
      item.insert.toLowerCase().includes(q),
  );
}

/** Detect open `{…` token before cursor in plain text. */
export function openBraceQuery(textBeforeCursor: string): string | null {
  const match = textBeforeCursor.match(/\{([^{}\n]*)$/);
  if (!match) return null;
  // Don't open when already a complete known tag closed
  return match[1] ?? '';
}
