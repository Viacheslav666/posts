import type { CalendarItem, Post, Publication } from '@posts/api-client';

/**
 * STOMP / polling event for live publication status (03-frontend.md §4).
 */
export type PublicationUpdatedEvent = {
  type: 'publication.updated';
  publication: Publication;
  /** Optional aggregate post status patch from the server fan-out. */
  postStatus?: Post['status'];
  postId?: string;
};

export type WsConnectionState = 'idle' | 'connecting' | 'open' | 'closed' | 'polling';

export type PublicationCacheSlice = {
  byId: Record<string, Publication>;
  byPostId: Record<string, string[]>;
};

export function emptyPublicationCache(): PublicationCacheSlice {
  return { byId: {}, byPostId: {} };
}

export function indexPublications(items: Publication[]): PublicationCacheSlice {
  const byId: Record<string, Publication> = {};
  const byPostId: Record<string, string[]> = {};
  for (const item of items) {
    if (!item.id) continue;
    byId[item.id] = item;
    const postId = item.postId ?? '';
    if (!byPostId[postId]) byPostId[postId] = [];
    byPostId[postId]!.push(item.id);
  }
  return { byId, byPostId };
}

/**
 * Pure reducer: apply `publication.updated` to a publication list / detail cache.
 */
export function applyPublicationUpdated(
  cache: PublicationCacheSlice,
  event: PublicationUpdatedEvent,
): PublicationCacheSlice {
  const pub = event.publication;
  if (!pub.id) return cache;

  const byId = { ...cache.byId, [pub.id]: { ...cache.byId[pub.id], ...pub } };
  const postId = pub.postId ?? event.postId ?? '';
  const existing = cache.byPostId[postId] ?? [];
  const byPostId = {
    ...cache.byPostId,
    [postId]: existing.includes(pub.id) ? existing : [...existing, pub.id],
  };
  return { byId, byPostId };
}

export function patchPostInList(posts: Post[], event: PublicationUpdatedEvent): Post[] {
  if (!event.postStatus || !event.postId) return posts;
  return posts.map((post) =>
    post.id === event.postId ? { ...post, status: event.postStatus } : post,
  );
}

export function patchCalendarItem(
  items: CalendarItem[],
  event: PublicationUpdatedEvent,
): CalendarItem[] {
  if (!event.postStatus || !event.postId) return items;
  return items.map((item) =>
    item.id === event.postId ? { ...item, status: event.postStatus } : item,
  );
}

export type WsClientAction =
  | { type: 'connect' }
  | { type: 'connected' }
  | { type: 'disconnected' }
  | { type: 'fallback_polling' }
  | { type: 'event'; event: PublicationUpdatedEvent };

export function reduceConnectionState(
  state: WsConnectionState,
  action: WsClientAction,
): WsConnectionState {
  switch (action.type) {
    case 'connect':
      return 'connecting';
    case 'connected':
      return 'open';
    case 'disconnected':
      return state === 'polling' ? 'polling' : 'closed';
    case 'fallback_polling':
      return 'polling';
    case 'event':
      return state === 'idle' ? 'open' : state;
    default:
      return state;
  }
}
