/**
 * TanStack Query key factory (see `03-frontend.md` §4).
 * Keep keys hierarchical so features can invalidate whole subtrees.
 */
export const queryKeys = {
  root: ['posts'] as const,

  me: {
    all: () => [...queryKeys.root, 'me'] as const,
    profile: () => [...queryKeys.me.all(), 'profile'] as const,
    sessions: (cursor?: string) => [...queryKeys.me.all(), 'sessions', { cursor }] as const,
  },

  networks: {
    all: () => [...queryKeys.root, 'networks'] as const,
    capabilities: () => [...queryKeys.networks.all(), 'capabilities'] as const,
  },

  workspaces: {
    all: () => [...queryKeys.root, 'workspaces'] as const,
    list: (cursor?: string) => [...queryKeys.workspaces.all(), 'list', { cursor }] as const,
    detail: (workspaceId: string) => [...queryKeys.workspaces.all(), workspaceId] as const,
    members: (workspaceId: string, cursor?: string) =>
      [...queryKeys.workspaces.detail(workspaceId), 'members', { cursor }] as const,
    invitations: (workspaceId: string, cursor?: string) =>
      [...queryKeys.workspaces.detail(workspaceId), 'invitations', { cursor }] as const,
    audit: (workspaceId: string, cursor?: string) =>
      [...queryKeys.workspaces.detail(workspaceId), 'audit', { cursor }] as const,
  },

  projects: {
    all: (workspaceId: string) => [...queryKeys.workspaces.detail(workspaceId), 'projects'] as const,
    list: (workspaceId: string, cursor?: string) =>
      [...queryKeys.projects.all(workspaceId), 'list', { cursor }] as const,
    detail: (workspaceId: string, projectId: string) =>
      [...queryKeys.projects.all(workspaceId), projectId] as const,
  },

  channels: {
    all: (workspaceId: string) => [...queryKeys.workspaces.detail(workspaceId), 'channels'] as const,
    list: (workspaceId: string, cursor?: string) =>
      [...queryKeys.channels.all(workspaceId), 'list', { cursor }] as const,
    detail: (workspaceId: string, channelId: string) =>
      [...queryKeys.channels.all(workspaceId), channelId] as const,
    sets: (workspaceId: string) => [...queryKeys.channels.all(workspaceId), 'sets'] as const,
  },

  posts: {
    all: (workspaceId: string) => [...queryKeys.workspaces.detail(workspaceId), 'posts'] as const,
    list: (workspaceId: string, filters?: Record<string, unknown>) =>
      [...queryKeys.posts.all(workspaceId), 'list', filters ?? {}] as const,
    detail: (workspaceId: string, postId: string) =>
      [...queryKeys.posts.all(workspaceId), postId] as const,
  },

  calendar: {
    all: (workspaceId: string) => [...queryKeys.workspaces.detail(workspaceId), 'calendar'] as const,
    range: (
      workspaceId: string,
      from: string,
      to: string,
      filters?: Record<string, unknown>,
    ) => [...queryKeys.calendar.all(workspaceId), { from, to, ...(filters ?? {}) }] as const,
  },

  publications: {
    all: (workspaceId: string) =>
      [...queryKeys.workspaces.detail(workspaceId), 'publications'] as const,
    list: (workspaceId: string, filters?: Record<string, unknown>) =>
      [...queryKeys.publications.all(workspaceId), 'list', filters ?? {}] as const,
    byPost: (workspaceId: string, postId: string) =>
      [...queryKeys.publications.all(workspaceId), 'post', postId] as const,
    detail: (workspaceId: string, publicationId: string) =>
      [...queryKeys.publications.all(workspaceId), publicationId] as const,
    attempts: (workspaceId: string, publicationId: string) =>
      [...queryKeys.publications.detail(workspaceId, publicationId), 'attempts'] as const,
  },

  media: {
    all: (workspaceId: string) => [...queryKeys.workspaces.detail(workspaceId), 'media'] as const,
    list: (workspaceId: string, filters?: Record<string, unknown>) =>
      [...queryKeys.media.all(workspaceId), 'list', filters ?? {}] as const,
    detail: (workspaceId: string, mediaId: string) =>
      [...queryKeys.media.all(workspaceId), mediaId] as const,
    quota: (workspaceId: string) => [...queryKeys.media.all(workspaceId), 'quota'] as const,
    folders: (workspaceId: string) => [...queryKeys.media.all(workspaceId), 'folders'] as const,
  },

  notifications: {
    all: () => [...queryKeys.root, 'notifications'] as const,
    list: (cursor?: string) => [...queryKeys.notifications.all(), 'list', { cursor }] as const,
  },

  billing: {
    all: (workspaceId: string) => [...queryKeys.workspaces.detail(workspaceId), 'billing'] as const,
    plans: () => [...queryKeys.root, 'billing', 'plans'] as const,
    subscription: (workspaceId: string) =>
      [...queryKeys.billing.all(workspaceId), 'subscription'] as const,
  },

  ingest: {
    all: (workspaceId: string) => [...queryKeys.workspaces.detail(workspaceId), 'ingest'] as const,
    rules: (workspaceId: string, projectId: string, filters?: Record<string, unknown>) =>
      [...queryKeys.ingest.all(workspaceId), 'rules', projectId, filters ?? {}] as const,
    rule: (workspaceId: string, ruleId: string) =>
      [...queryKeys.ingest.all(workspaceId), 'rule', ruleId] as const,
    runs: (workspaceId: string, ruleId: string, cursor?: string) =>
      [...queryKeys.ingest.rule(workspaceId, ruleId), 'runs', { cursor }] as const,
    items: (workspaceId: string, ruleId: string, filters?: Record<string, unknown>) =>
      [...queryKeys.ingest.rule(workspaceId, ruleId), 'items', filters ?? {}] as const,
    item: (workspaceId: string, itemId: string) =>
      [...queryKeys.ingest.all(workspaceId), 'item', itemId] as const,
    queue: (workspaceId: string, filters?: Record<string, unknown>) =>
      [...queryKeys.ingest.all(workspaceId), 'queue', filters ?? {}] as const,
  },
} as const;
