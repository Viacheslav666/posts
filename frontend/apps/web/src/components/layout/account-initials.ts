const LETTER = /[\p{L}\p{N}]/u;

function letters(value: string, count: number): string | null {
  const found: string[] = [];
  for (const char of value) {
    if (LETTER.test(char)) {
      found.push(char);
    }
    if (found.length === count) {
      break;
    }
  }
  if (found.length === 0) {
    return null;
  }
  return found.join('').toLocaleUpperCase('ru-RU');
}

/**
 * Two initials: first + last name. A single name part uses its first two letters.
 * Without a name, the first two letters of the login (email local part).
 */
export function accountInitials(name?: string | null, email?: string | null): string | null {
  const parts = (name ?? '')
    .trim()
    .split(/\s+/)
    .filter(Boolean);
  if (parts.length >= 2) {
    const first = letters(parts[0] ?? '', 1);
    const last = letters(parts[parts.length - 1] ?? '', 1);
    const joined = `${first ?? ''}${last ?? ''}`;
    return joined ? joined.toLocaleUpperCase('ru-RU') : null;
  }
  if (parts.length === 1) {
    return letters(parts[0] ?? '', 2);
  }
  const login = (email ?? '').split('@')[0] ?? '';
  return letters(login, 2);
}

const METHOD_LABELS: Record<string, string> = {
  vk: 'VK',
  email: 'Email',
  yandex: 'Яндекс',
  telegram: 'Telegram',
};

export function loginMethodLabel(method?: string | null): string {
  if (!method) {
    return 'Email';
  }
  return METHOD_LABELS[method] ?? 'Email';
}

export function isSocialLogin(method?: string | null): boolean {
  return method === 'vk' || method === 'yandex' || method === 'telegram';
}

export function accountTooltip(name?: string | null, email?: string | null, method?: string | null): string {
  const who = name?.trim() || email?.trim() || 'Аккаунт';
  return `${who} · ${loginMethodLabel(method)}`;
}
