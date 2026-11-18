/**
 * Russian labels for ORD report row / ERID check statuses.
 * Keep in sync with `messages/ru.json` → ord.reports.status / eridStatus.
 */

export const ORD_REPORT_STATUS_LABELS_RU: Record<string, string> = {
  draft: 'Черновик',
  submitted: 'Отправлен в ОРД',
  error: 'Ошибка ЕРИР',
  running: 'Выполняется',
  succeeded: 'Успешно',
};

export const ORD_ERID_STATUS_LABELS_RU: Record<string, string> = {
  registered: 'Зарегистрирован',
  draft: 'Черновик',
  registering: 'Регистрация',
  error: 'Ошибка',
  not_found: 'Не найден в ОРД',
  unknown: 'Статус неизвестен',
  found: 'Найден',
};

/** Raw English enums that must never appear as badge text. */
export const RAW_ORD_REPORT_STATUS_ENUMS = [
  'draft',
  'submitted',
  'error',
  'running',
  'succeeded',
  'not_found',
  'unknown',
  'registering',
  'registered',
] as const;

export function ordReportStatusLabelRu(status?: string | null): string {
  if (!status) return ORD_REPORT_STATUS_LABELS_RU.draft;
  return ORD_REPORT_STATUS_LABELS_RU[status] ?? status;
}

export function ordEridStatusLabelRu(status?: string | null): string {
  if (!status) return ORD_ERID_STATUS_LABELS_RU.unknown;
  return ORD_ERID_STATUS_LABELS_RU[status] ?? status;
}

/**
 * Map free-text / provider ERIR error into a short Russian explanation.
 * Unknown provider text is returned as-is (already user-facing from ORD).
 */
export function mapErirErrorMessage(error?: string | null): string | null {
  if (!error?.trim()) return null;
  const raw = error.trim();
  const lower = raw.toLowerCase();

  if (lower.includes('timeout') || lower.includes('timed out')) {
    return 'Таймаут ответа ОРД / ЕРИР. Повторите позже.';
  }
  if (lower.includes('rate') && lower.includes('limit')) {
    return 'Превышен лимит запросов к ОРД. Повторите позже.';
  }
  if (lower.includes('unauthorized') || lower.includes('forbidden') || lower.includes('api key')) {
    return 'Ошибка авторизации в ОРД. Проверьте API-ключ подключения.';
  }
  if (lower.includes('validation') || lower.includes('invalid')) {
    return 'ОРД отклонил данные: проверьте реквизиты креатива и акта.';
  }
  if (lower.includes('not found') || lower.includes('не найден')) {
    return 'Запись не найдена в ОРД / ЕРИР.';
  }
  if (lower.includes('erir') || lower.includes('ерир')) {
    return `Ошибка ЕРИР: ${raw}`;
  }
  return raw;
}

export const ORD_PROBLEM_CODE_LABELS_RU: Record<string, string> = {
  ord_not_configured: 'ОРД не настроен для пространства. Подключите провайдера на вкладке «Подключение».',
  ord_validation_failed: 'ОРД отклонил запрос: проверьте период и данные креативов.',
  ord_provider_unavailable: 'Провайдер ОРД временно недоступен. Повторите позже.',
  insufficient_permissions: 'Недостаточно прав для этого действия.',
  not_found: 'Запись не найдена.',
};
