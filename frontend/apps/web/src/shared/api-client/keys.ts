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
    list: (workspaceId: string, cursor?: string | Record<string, unknown>) =>
      [...queryKeys.channels.all(workspaceId), 'list', typeof cursor === 'string' || cursor === undefined ? { cursor } : cursor] as const,
    detail: (workspaceId: string, channelId: string) =>
      [...queryKeys.channels.all(workspaceId), channelId] as const,
    sets: (workspaceId: string) => [...queryKeys.channels.all(workspaceId), 'sets'] as const,
    eventSubscription: (workspaceId: string, channelId: string) =>
      [...queryKeys.channels.detail(workspaceId, channelId), 'event-subscription'] as const,
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
    all: (workspaceId: string) =>
      [...queryKeys.workspaces.detail(workspaceId), 'notifications'] as const,
    list: (workspaceId: string, filters?: { unreadOnly?: boolean; cursor?: string }) =>
      [...queryKeys.notifications.all(workspaceId), 'list', filters ?? {}] as const,
    settings: (workspaceId: string) =>
      [...queryKeys.notifications.all(workspaceId), 'settings'] as const,
    unreadCount: (workspaceId: string) =>
      [...queryKeys.notifications.all(workspaceId), 'unreadCount'] as const,
    vapid: () => [...queryKeys.root, 'notifications', 'vapid'] as const,
  },

  analytics: {
    all: (workspaceId: string) =>
      [...queryKeys.workspaces.detail(workspaceId), 'analytics'] as const,
    posts: (workspaceId: string, year: number) =>
      [...queryKeys.analytics.all(workspaceId), 'posts', { year }] as const,
  },

  billing: {
    all: (workspaceId: string) => [...queryKeys.workspaces.detail(workspaceId), 'billing'] as const,
    plans: () => [...queryKeys.root, 'billing', 'plans'] as const,
    usage: (workspaceId: string) => [...queryKeys.billing.all(workspaceId), 'usage'] as const,
    wallet: (workspaceId: string) => [...queryKeys.billing.all(workspaceId), 'wallet'] as const,
    autopay: (workspaceId: string) => [...queryKeys.billing.all(workspaceId), 'autopay'] as const,
    paymentMethod: (workspaceId: string) =>
      [...queryKeys.billing.all(workspaceId), 'paymentMethod'] as const,
    transactions: (workspaceId: string, filters?: { kind?: string; cursor?: string }) =>
      [...queryKeys.billing.all(workspaceId), 'transactions', filters ?? {}] as const,
    payments: (workspaceId: string, cursor?: string) =>
      [...queryKeys.billing.all(workspaceId), 'payments', { cursor }] as const,
    invoices: (workspaceId: string, cursor?: string) =>
      [...queryKeys.billing.all(workspaceId), 'invoices', { cursor }] as const,
  },

  admin: {
    all: () => [...queryKeys.root, 'admin'] as const,
    users: (filters?: Record<string, unknown>) =>
      [...queryKeys.admin.all(), 'users', filters ?? {}] as const,
    user: (userId: string) => [...queryKeys.admin.all(), 'user', userId] as const,
    workspaces: (filters?: Record<string, unknown>) =>
      [...queryKeys.admin.all(), 'workspaces', filters ?? {}] as const,
    workspace: (workspaceId: string) =>
      [...queryKeys.admin.all(), 'workspace', workspaceId] as const,
    plans: () => [...queryKeys.admin.all(), 'plans'] as const,
    promos: (filters?: Record<string, unknown>) =>
      [...queryKeys.admin.all(), 'promos', filters ?? {}] as const,
    queues: () => [...queryKeys.admin.all(), 'queues'] as const,
    queueMessages: (queue: string, filters?: Record<string, unknown>) =>
      [...queryKeys.admin.all(), 'queues', queue, 'messages', filters ?? {}] as const,
    featureFlags: () => [...queryKeys.admin.all(), 'feature-flags'] as const,
    announcements: (filters?: Record<string, unknown>) =>
      [...queryKeys.admin.all(), 'announcements', filters ?? {}] as const,
    audit: (filters?: Record<string, unknown>) =>
      [...queryKeys.admin.all(), 'audit', filters ?? {}] as const,
  },

  ord: {
    all: (workspaceId: string) => [...queryKeys.workspaces.detail(workspaceId), 'ord'] as const,
    connections: (workspaceId: string) =>
      [...queryKeys.ord.all(workspaceId), 'connections'] as const,
    contractors: (workspaceId: string) =>
      [...queryKeys.ord.all(workspaceId), 'contractors'] as const,
    contracts: (workspaceId: string) => [...queryKeys.ord.all(workspaceId), 'contracts'] as const,
    reports: (workspaceId: string, filters?: Record<string, unknown>) =>
      [...queryKeys.ord.all(workspaceId), 'reports', filters ?? {}] as const,
    creative: (workspaceId: string, postId: string) =>
      [...queryKeys.ord.all(workspaceId), 'creative', postId] as const,
    dictionaries: () => [...queryKeys.root, 'ord', 'dictionaries'] as const,
    kktu: (q = '') => [...queryKeys.ord.dictionaries(), 'kktu', q] as const,
    oksm: (q = '') => [...queryKeys.ord.dictionaries(), 'oksm', q] as const,
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

  crosspost: {
    all: (workspaceId: string) =>
      [...queryKeys.workspaces.detail(workspaceId), 'crosspost'] as const,
    rules: (workspaceId: string, projectId: string, filters?: Record<string, unknown>) =>
      [...queryKeys.crosspost.all(workspaceId), 'rules', projectId, filters ?? {}] as const,
    rule: (workspaceId: string, ruleId: string) =>
      [...queryKeys.crosspost.all(workspaceId), 'rule', ruleId] as const,
    items: (workspaceId: string, ruleId: string, filters?: Record<string, unknown>) =>
      [...queryKeys.crosspost.rule(workspaceId, ruleId), 'items', filters ?? {}] as const,
  },

  antispam: {
    all: (workspaceId: string) =>
      [...queryKeys.workspaces.detail(workspaceId), 'antispam'] as const,
    rules: (workspaceId: string, projectId: string, filters?: Record<string, unknown>) =>
      [...queryKeys.antispam.all(workspaceId), 'rules', projectId, filters ?? {}] as const,
    rule: (workspaceId: string, ruleId: string) =>
      [...queryKeys.antispam.all(workspaceId), 'rule', ruleId] as const,
    stats: (workspaceId: string, ruleId: string) =>
      [...queryKeys.antispam.rule(workspaceId, ruleId), 'stats'] as const,
    wordLists: (workspaceId: string, filters?: Record<string, unknown>) =>
      [...queryKeys.antispam.all(workspaceId), 'wordLists', filters ?? {}] as const,
    log: (workspaceId: string, filters?: Record<string, unknown>) =>
      [...queryKeys.antispam.all(workspaceId), 'log', filters ?? {}] as const,
  },
} as const;
