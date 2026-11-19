export type KeywordMatchMode = 'whole_word' | 'substring';

export type KeywordListValue = {
  keywords: string[];
  matchMode: KeywordMatchMode;
};

/** Split pasted text by newlines or commas; trim and drop empties; dedupe case-insensitively. */
export function parseKeywordPaste(raw: string): string[] {
  const parts = raw
    .split(/[\n,;]+/g)
    .map((part) => part.trim())
    .filter(Boolean);
  const seen = new Set<string>();
  const result: string[] = [];
  for (const part of parts) {
    const key = part.toLocaleLowerCase('ru');
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(part);
  }
  return result;
}

export function mergeKeywords(existing: string[], incoming: string[]): string[] {
  const seen = new Set(existing.map((item) => item.toLocaleLowerCase('ru')));
  const next = [...existing];
  for (const item of incoming) {
    const key = item.toLocaleLowerCase('ru');
    if (seen.has(key)) continue;
    seen.add(key);
    next.push(item);
  }
  return next;
}

export function removeKeyword(list: string[], keyword: string): string[] {
  const key = keyword.toLocaleLowerCase('ru');
  return list.filter((item) => item.toLocaleLowerCase('ru') !== key);
}
