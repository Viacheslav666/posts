import type { PublicationState, PostStatus } from './status';

/**
 * Unified Russian labels for post / story / publication statuses.
 * Keep in sync with `messages/ru.json` → posts.status / posts.publicationState.
 */
export const POST_STATUS_LABELS_RU: Record<PostStatus, string> = {
  draft: 'Черновик',
  pending_approval: 'На согласовании',
  approved: 'Согласован',
  scheduled: 'Запланирован',
  publishing: 'Публикуется',
  published: 'Опубликован',
  partially_published: 'Частично',
  failed: 'Ошибка',
  paused: 'Пауза',
  archived: 'Архив',
  deleted_from_network: 'Удалён из сети',
};

export const PUBLICATION_STATE_LABELS_RU: Record<PublicationState, string> = {
  pending: 'Ожидает',
  queued: 'В очереди',
  in_progress: 'В работе',
  succeeded: 'Успешно',
  failed_retryable: 'Ошибка (повтор)',
  failed_permanent: 'Ошибка',
  cancelled: 'Отменено',
  waiting_for_channel: 'Ждёт канал',
};

/** Raw English enums that must never appear as badge text in the UI. */
export const RAW_STATUS_ENUMS = [
  ...Object.keys(POST_STATUS_LABELS_RU),
  ...Object.keys(PUBLICATION_STATE_LABELS_RU),
] as const;

export function postStatusLabelRu(status?: string | null): string {
  if (!status) return POST_STATUS_LABELS_RU.draft;
  return POST_STATUS_LABELS_RU[status as PostStatus] ?? status;
}

export function publicationStateLabelRu(state?: string | null): string {
  if (!state) return PUBLICATION_STATE_LABELS_RU.pending;
  return PUBLICATION_STATE_LABELS_RU[state as PublicationState] ?? state;
}
