import type {
  BulkPostsRequest,
  BulkPostsResponse,
  CalendarItem,
  MoveCalendarItemRequest,
  Post,
  Publication,
} from '@posts/api-client';

import { CHANNEL_IDS } from './channel-store';
import {
  DEFAULT_PROJECT_ID,
  DEMO_USER_ID,
  PERSONAL_WORKSPACE_ID,
  SECONDARY_USER_ID,
} from './workspace-store';

export const POST_IDS = {
  morning: '019a0000-post-7000-8000-000000000001',
  failed: '019a0000-post-7000-8000-000000000002',
  paused: '019a0000-post-7000-8000-000000000003',
  published: '019a0000-post-7000-8000-000000000004',
  draft: '019a0000-post-7000-8000-000000000005',
  weekAhead: '019a0000-post-7000-8000-000000000006',
} as const;

export const PUBLICATION_IDS = {
  morningVk: '019a0000-publ-7000-8000-000000000001',
  morningTg: '019a0000-publ-7000-8000-000000000002',
  failedVk: '019a0000-publ-7000-8000-000000000003',
  pausedTg: '019a0000-publ-7000-8000-000000000004',
  publishedOk: '019a0000-publ-7000-8000-000000000005',
  weekVk: '019a0000-publ-7000-8000-000000000006',
} as const;

export type PostStatus = NonNullable<Post['status']>;
export type Network = NonNullable<CalendarItem['networks']>[number];

export type StoredPost = Post & {
  channelIds: string[];
  networks: Network[];
  /** MSW-only: future ad marking fields (FE-12). */
  adMarking?: {
    enabled: boolean;
    erid?: string;
    advertiserName?: string;
  };
};

type PostStoreState = {
  posts: StoredPost[];
  publications: Publication[];
  seq: number;
};

function hoursFromNow(hours: number): string {
  return new Date(Date.now() + hours * 60 * 60 * 1000).toISOString();
}

function daysFromNow(days: number, hour = 10): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  d.setHours(hour, 0, 0, 0);
  return d.toISOString();
}

function createInitialState(): PostStoreState {
  const now = new Date().toISOString();

  const posts: StoredPost[] = [
    {
      id: POST_IDS.morning,
      workspaceId: PERSONAL_WORKSPACE_ID,
      projectId: DEFAULT_PROJECT_ID,
      authorId: DEMO_USER_ID,
      title: 'Утренний дайджест',
      status: 'scheduled',
      scheduledAt: hoursFromNow(6),
      version: 1,
      createdAt: now,
      updatedAt: now,
      channelIds: [CHANNEL_IDS.vkNews, CHANNEL_IDS.tgNews],
      networks: ['vk', 'tg'],
      baseContent: { text: 'Доброе утро! Новости бренда за сутки.' },
      ad: false,
      adMarking: { enabled: false },
    },
    {
      id: POST_IDS.failed,
      workspaceId: PERSONAL_WORKSPACE_ID,
      projectId: DEFAULT_PROJECT_ID,
      authorId: DEMO_USER_ID,
      title: 'Акция недели',
      status: 'failed',
      scheduledAt: hoursFromNow(-2),
      version: 2,
      createdAt: now,
      updatedAt: now,
      channelIds: [CHANNEL_IDS.vkNews],
      networks: ['vk'],
      baseContent: { text: 'Скидка 20% до воскресенья.' },
      ad: true,
      adMarking: {
        enabled: true,
        erid: 'LjN8K2xxxx',
        advertiserName: 'ООО Посты',
      },
    },
    {
      id: POST_IDS.paused,
      workspaceId: PERSONAL_WORKSPACE_ID,
      projectId: DEFAULT_PROJECT_ID,
      authorId: SECONDARY_USER_ID,
      title: 'Пауза: разбор кейса',
      status: 'paused',
      scheduledAt: daysFromNow(2, 15),
      version: 1,
      createdAt: now,
      updatedAt: now,
      channelIds: [CHANNEL_IDS.tgNews],
      networks: ['tg'],
      baseContent: { text: 'Как мы публикуем 2000 постов в пик.' },
      ad: false,
    },
    {
      id: POST_IDS.published,
      workspaceId: PERSONAL_WORKSPACE_ID,
      projectId: DEFAULT_PROJECT_ID,
      authorId: DEMO_USER_ID,
      title: 'Опубликовано вчера',
      status: 'published',
      scheduledAt: hoursFromNow(-26),
      version: 1,
      createdAt: now,
      updatedAt: now,
      channelIds: [CHANNEL_IDS.okBrand],
      networks: ['ok'],
      baseContent: { text: 'Спасибо за неделю с нами!' },
      ad: false,
    },
    {
      id: POST_IDS.draft,
      workspaceId: PERSONAL_WORKSPACE_ID,
      projectId: DEFAULT_PROJECT_ID,
      authorId: DEMO_USER_ID,
      title: 'Черновик без даты',
      status: 'draft',
      version: 1,
      createdAt: now,
      updatedAt: now,
      channelIds: [CHANNEL_IDS.vkNews],
      networks: ['vk'],
      baseContent: { text: 'Текст ещё пишется…' },
      ad: false,
    },
    {
      id: POST_IDS.weekAhead,
      workspaceId: PERSONAL_WORKSPACE_ID,
      projectId: DEFAULT_PROJECT_ID,
      authorId: DEMO_USER_ID,
      title: 'План на следующую неделю',
      status: 'scheduled',
      scheduledAt: daysFromNow(7, 12),
      version: 1,
      createdAt: now,
      updatedAt: now,
      channelIds: [CHANNEL_IDS.vkNews, CHANNEL_IDS.maxBrand],
      networks: ['vk', 'max'],
      baseContent: { text: 'Анонс серии постов.' },
      ad: false,
    },
  ];

  const publications: Publication[] = [
    {
      id: PUBLICATION_IDS.morningVk,
      workspaceId: PERSONAL_WORKSPACE_ID,
      postId: POST_IDS.morning,
      channelId: CHANNEL_IDS.vkNews,
      network: 'vk',
      state: 'pending',
      runAt: hoursFromNow(6),
      attempts: 0,
      createdAt: now,
    },
    {
      id: PUBLICATION_IDS.morningTg,
      workspaceId: PERSONAL_WORKSPACE_ID,
      postId: POST_IDS.morning,
      channelId: CHANNEL_IDS.tgNews,
      network: 'tg',
      state: 'pending',
      runAt: hoursFromNow(6),
      attempts: 0,
      createdAt: now,
    },
    {
      id: PUBLICATION_IDS.failedVk,
      workspaceId: PERSONAL_WORKSPACE_ID,
      postId: POST_IDS.failed,
      channelId: CHANNEL_IDS.vkNews,
      network: 'vk',
      state: 'failed_retryable',
      runAt: hoursFromNow(-2),
      attempts: 3,
      errorCode: 'vk_api_6',
      errorMessage: 'Too many requests to VK API',
      createdAt: now,
    },
    {
      id: PUBLICATION_IDS.pausedTg,
      workspaceId: PERSONAL_WORKSPACE_ID,
      postId: POST_IDS.paused,
      channelId: CHANNEL_IDS.tgNews,
      network: 'tg',
      state: 'cancelled',
      runAt: daysFromNow(2, 15),
      attempts: 0,
      createdAt: now,
    },
    {
      id: PUBLICATION_IDS.publishedOk,
      workspaceId: PERSONAL_WORKSPACE_ID,
      postId: POST_IDS.published,
      channelId: CHANNEL_IDS.okBrand,
      network: 'ok',
      state: 'succeeded',
      runAt: hoursFromNow(-26),
      publishedAt: hoursFromNow(-26),
      remotePostId: 'ok-12345',
      remoteUrl: 'https://ok.ru/group/posts/12345',
      attempts: 1,
      createdAt: now,
    },
    {
      id: PUBLICATION_IDS.weekVk,
      workspaceId: PERSONAL_WORKSPACE_ID,
      postId: POST_IDS.weekAhead,
      channelId: CHANNEL_IDS.vkNews,
      network: 'vk',
      state: 'pending',
      runAt: daysFromNow(7, 12),
      attempts: 0,
      createdAt: now,
    },
  ];

  return { posts, publications, seq: 100 };
}

let state = createInitialState();

export function resetPostStore(): void {
  state = createInitialState();
}

function nextId(prefix: 'post' | 'publ'): string {
  state.seq += 1;
  return `019a0000-${prefix}-7000-8000-${String(state.seq).padStart(12, '0')}`;
}

export type ListPostsFilters = {
  workspaceId: string;
  from?: string;
  to?: string;
  status?: string;
  channelId?: string;
  projectId?: string;
  network?: string;
  authorId?: string;
  q?: string;
  cursor?: string;
  limit?: number;
  /** Client sort — MSW extension until OpenAPI adds `sort`. */
  sort?: 'scheduledAt_asc' | 'scheduledAt_desc' | 'title_asc' | 'updatedAt_desc';
};

function matchesFilters(post: StoredPost, filters: ListPostsFilters): boolean {
  if (post.workspaceId !== filters.workspaceId) return false;
  if (filters.status && post.status !== filters.status) return false;
  if (filters.projectId && post.projectId !== filters.projectId) return false;
  if (filters.channelId && !post.channelIds.includes(filters.channelId)) return false;
  if (filters.network && !post.networks.includes(filters.network as Network)) return false;
  if (filters.authorId && post.authorId !== filters.authorId) return false;
  if (filters.from && post.scheduledAt && post.scheduledAt < filters.from) return false;
  if (filters.to && post.scheduledAt && post.scheduledAt > filters.to) return false;
  if (filters.q) {
    const q = filters.q.toLowerCase();
    const hay = `${post.title ?? ''} ${post.baseContent?.text ?? ''}`.toLowerCase();
    if (!hay.includes(q)) return false;
  }
  return true;
}

function sortPosts(posts: StoredPost[], sort: ListPostsFilters['sort']): StoredPost[] {
  const copy = [...posts];
  switch (sort) {
    case 'title_asc':
      return copy.sort((a, b) => (a.title ?? '').localeCompare(b.title ?? '', 'ru'));
    case 'updatedAt_desc':
      return copy.sort((a, b) => (b.updatedAt ?? '').localeCompare(a.updatedAt ?? ''));
    case 'scheduledAt_asc':
      return copy.sort((a, b) => (a.scheduledAt ?? '').localeCompare(b.scheduledAt ?? ''));
    case 'scheduledAt_desc':
    default:
      return copy.sort((a, b) => (b.scheduledAt ?? '').localeCompare(a.scheduledAt ?? ''));
  }
}

export function listPosts(filters: ListPostsFilters): {
  items: StoredPost[];
  hasMore: boolean;
  nextCursor?: string;
} {
  const limit = Math.min(Math.max(filters.limit ?? 20, 1), 100);
  let filtered = sortPosts(
    state.posts.filter((post) => matchesFilters(post, filters)),
    filters.sort ?? 'scheduledAt_desc',
  );

  let start = 0;
  if (filters.cursor) {
    const idx = filtered.findIndex((p) => p.id === filters.cursor);
    start = idx >= 0 ? idx + 1 : 0;
  }

  const page = filtered.slice(start, start + limit);
  const hasMore = start + limit < filtered.length;
  return {
    items: page,
    hasMore,
    nextCursor: hasMore ? page[page.length - 1]?.id : undefined,
  };
}

export type CalendarFilters = {
  workspaceId: string;
  from: string;
  to: string;
  projectId?: string;
  channelId?: string;
  authorId?: string;
  network?: string;
  status?: string;
};

export function listCalendar(filters: CalendarFilters): CalendarItem[] {
  return state.posts
    .filter((post) => {
      if (post.workspaceId !== filters.workspaceId) return false;
      if (!post.scheduledAt) return false;
      if (post.scheduledAt < filters.from || post.scheduledAt > filters.to) return false;
      if (filters.projectId && post.projectId !== filters.projectId) return false;
      if (filters.channelId && !post.channelIds.includes(filters.channelId)) return false;
      if (filters.authorId && post.authorId !== filters.authorId) return false;
      if (filters.network && !post.networks.includes(filters.network as Network)) return false;
      if (filters.status && post.status !== filters.status) return false;
      return post.status !== 'draft' && post.status !== 'archived';
    })
    .map((post) => ({
      id: post.id,
      title: post.title,
      scheduledAt: post.scheduledAt,
      status: post.status,
      projectId: post.projectId,
      channelIds: post.channelIds,
      networks: post.networks,
    }));
}

export function getPost(postId: string): StoredPost | undefined {
  return state.posts.find((p) => p.id === postId);
}

export function listPublicationsForPost(
  postId: string,
  cursor?: string,
  limit = 20,
): { items: Publication[]; hasMore: boolean; nextCursor?: string } {
  let items = state.publications.filter((p) => p.postId === postId);
  let start = 0;
  if (cursor) {
    const idx = items.findIndex((p) => p.id === cursor);
    start = idx >= 0 ? idx + 1 : 0;
  }
  const page = items.slice(start, start + limit);
  const hasMore = start + limit < items.length;
  return {
    items: page,
    hasMore,
    nextCursor: hasMore ? page[page.length - 1]?.id : undefined,
  };
}

export function getPublication(publicationId: string): Publication | undefined {
  return state.publications.find((p) => p.id === publicationId);
}

export function moveCalendarItem(
  postId: string,
  body: MoveCalendarItemRequest,
): StoredPost | { conflict: true } | null {
  const post = getPost(postId);
  if (!post) return null;
  if (post.version !== body.version) return { conflict: true };

  post.scheduledAt = body.scheduledAt;
  post.version = (post.version ?? 0) + 1;
  post.updatedAt = new Date().toISOString();
  if (post.status === 'draft') {
    post.status = 'scheduled';
  }

  for (const pub of state.publications) {
    if (pub.postId === postId && (pub.state === 'pending' || pub.state === 'queued')) {
      pub.runAt = body.scheduledAt;
    }
  }

  return { ...post };
}

export function pausePost(postId: string, version: number): StoredPost | { conflict: true } | null {
  const post = getPost(postId);
  if (!post) return null;
  if (post.version !== version) return { conflict: true };
  post.status = 'paused';
  post.version = version + 1;
  post.updatedAt = new Date().toISOString();
  for (const pub of state.publications) {
    if (pub.postId === postId && (pub.state === 'pending' || pub.state === 'queued')) {
      pub.state = 'cancelled';
    }
  }
  return { ...post };
}

export function resumePost(postId: string, version: number): StoredPost | { conflict: true } | null {
  const post = getPost(postId);
  if (!post) return null;
  if (post.version !== version) return { conflict: true };
  post.status = 'scheduled';
  post.version = version + 1;
  post.updatedAt = new Date().toISOString();
  for (const pub of state.publications) {
    if (pub.postId === postId && pub.state === 'cancelled') {
      pub.state = 'pending';
      pub.runAt = post.scheduledAt;
    }
  }
  return { ...post };
}

export function deletePost(postId: string): boolean {
  const idx = state.posts.findIndex((p) => p.id === postId);
  if (idx < 0) return false;
  state.posts[idx]!.status = 'archived';
  state.posts[idx]!.updatedAt = new Date().toISOString();
  for (const pub of state.publications) {
    if (pub.postId === postId && pub.state !== 'succeeded') {
      pub.state = 'cancelled';
    }
  }
  return true;
}

export function duplicatePost(postId: string): StoredPost | null {
  const source = getPost(postId);
  if (!source) return null;
  const now = new Date().toISOString();
  const copy: StoredPost = {
    ...source,
    id: nextId('post'),
    title: `${source.title ?? 'Пост'} (копия)`,
    status: 'draft',
    scheduledAt: undefined,
    version: 1,
    createdAt: now,
    updatedAt: now,
    adMarking: source.adMarking ? { ...source.adMarking, erid: undefined } : undefined,
  };
  state.posts.push(copy);
  return { ...copy };
}

export function bulkPosts(body: BulkPostsRequest): BulkPostsResponse {
  const failedIds: string[] = [];
  let affected = 0;

  for (const id of body.postIds ?? []) {
    const post = getPost(id);
    if (!post) {
      failedIds.push(id);
      continue;
    }
    const version = post.version ?? 1;
    switch (body.action) {
      case 'pause': {
        const result = pausePost(id, version);
        if (!result || 'conflict' in result) failedIds.push(id);
        else affected += 1;
        break;
      }
      case 'resume': {
        const result = resumePost(id, version);
        if (!result || 'conflict' in result) failedIds.push(id);
        else affected += 1;
        break;
      }
      case 'delete': {
        if (deletePost(id)) affected += 1;
        else failedIds.push(id);
        break;
      }
      case 'reschedule': {
        if (!body.scheduledAt) {
          failedIds.push(id);
          break;
        }
        const result = moveCalendarItem(id, {
          scheduledAt: body.scheduledAt,
          version,
        });
        if (!result || 'conflict' in result) failedIds.push(id);
        else affected += 1;
        break;
      }
      default:
        failedIds.push(id);
    }
  }

  return { affected, failedIds };
}

/** MSW extension: bulk duplicate via individual clones (not in OpenAPI BulkPostsRequest). */
export function bulkDuplicate(postIds: string[]): BulkPostsResponse {
  const failedIds: string[] = [];
  let affected = 0;
  for (const id of postIds) {
    const copy = duplicatePost(id);
    if (copy) affected += 1;
    else failedIds.push(id);
  }
  return { affected, failedIds };
}

export function retryPublication(publicationId: string): Publication | null {
  const pub = getPublication(publicationId);
  if (!pub) return null;
  if (pub.state !== 'failed_retryable' && pub.state !== 'failed_permanent') {
    return null;
  }
  pub.state = 'queued';
  pub.attempts = (pub.attempts ?? 0) + 1;
  pub.errorCode = undefined;
  pub.errorMessage = undefined;
  pub.nextAttemptAt = new Date(Date.now() + 30_000).toISOString();

  const post = getPost(pub.postId ?? '');
  if (post && (post.status === 'failed' || post.status === 'partially_published')) {
    post.status = 'publishing';
    post.updatedAt = new Date().toISOString();
  }
  return { ...pub };
}

/** Simulate a STOMP publication.updated payload for polling/WS tests. */
export function applyPublicationUpdate(
  publicationId: string,
  patch: Partial<Publication>,
): Publication | null {
  const pub = getPublication(publicationId);
  if (!pub) return null;
  Object.assign(pub, patch);
  return { ...pub };
}

export function toApiPost(post: StoredPost): Post {
  const { channelIds: _c, networks: _n, adMarking, ...rest } = post;
  // `adMarking` is an MSW / FE-12 preview extension — not in OpenAPI Post yet.
  return { ...rest, ...(adMarking ? { adMarking } : {}) } as Post;
}
