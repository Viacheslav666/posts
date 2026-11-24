import type { PermissionAction } from '@/shared/lib/permissions';

export type NavFlag = 'suggest' | 'antispam' | 'crosspost';

/** Badge slots. Only keys with a real count are rendered, and only when that count is greater than zero. */
export type NavBadgeKey = 'inbox' | 'messenger' | 'suggest' | 'antispam' | 'scripts';

export type NavIcon =
  | 'dashboard'
  | 'calendar'
  | 'inbox'
  | 'messenger'
  | 'create-post'
  | 'create-story'
  | 'posts'
  | 'stories'
  | 'communities'
  | 'analytics'
  | 'automations'
  | 'grabber'
  | 'suggest'
  | 'antispam'
  | 'crosspost'
  | 'ord'
  | 'scripts'
  | 'channels'
  | 'media'
  | 'projects'
  | 'team'
  | 'settings'
  | 'billing';

export type NavNode = {
  id: string;
  label: string;
  href: string;
  permission?: PermissionAction;
  flag?: NavFlag;
  synonyms?: string[];
};

export type NavItem = NavNode & {
  priority: 'p0' | 'p1' | 'p2';
  icon: NavIcon;
  badge?: NavBadgeKey;
  testId?: string;
  children?: NavNode[];
};

export type NavAccess = {
  can: (action: PermissionAction) => boolean;
  flags: Record<NavFlag, boolean>;
};

export type SearchHit = {
  id: string;
  label: string;
  href: string;
  parentLabel?: string;
};

export type MenuPlacement = {
  top: number;
  left: number;
  maxHeight: number;
};

export const MENU_ITEM_HEIGHT = 36;
export const MENU_WIDTH = 240;
const MENU_CHROME = 8;
const MENU_VISIBLE_CAP = 10;

export const PLACEHOLDER_TITLES: Record<string, string> = {
  inbox: 'Инбокс',
  messenger: 'Мессенджер диалогов',
  'messenger-all': 'Все диалоги',
  'messenger-unread': 'Непрочитанные',
  'messenger-sources': 'Подключённые источники',
  'messenger-archive': 'Архив',
  'post-templates': 'Шаблоны постов',
  'story-templates': 'Шаблоны историй',
  communities: 'Мои сообщества',
  'communities-table': 'Все сообщества',
  'communities-grid': 'Плитка',
  'communities-stats': 'Агрегаты и динамика',
  'communities-export': 'Экспорт',
  'analytics-posts': 'Аналитика по постам',
  'analytics-stories': 'Аналитика по историям',
  'analytics-communities': 'Аналитика по сообществам',
  funnels: 'Автоворонки',
  schedules: 'Расписания',
  scripts: 'Сервисные скрипты',
  'scripts-dogs': 'Чистка собачек',
  'scripts-blacklist': 'Чистка ЧС',
  'scripts-posts': 'Чистка постов',
  'scripts-videos': 'Чистка видео',
  'scripts-groups': 'Аналитика групп',
  'scripts-jobs': 'Запущенные задачи',
  'ord-campaigns': 'Кампании',
  'settings-integrations': 'Интеграции',
  'projects-archive': 'Архив проектов',
  'antispam-reports': 'Отчёты антиспама',
  'crosspost-queue': 'Очередь кросспостинга',
  'crosspost-reports': 'Отчёты кросспостинга',
  'channels-settings': 'Настройки каналов',
};

const FAVORITES_KEY = 'posts.nav.favorites';
export const FAVORITES_LIMIT = 10;

function soon(workspaceId: string, slug: string): string {
  return `/app/${workspaceId}/soon/${slug}`;
}

function path(workspaceId: string, suffix: string): string {
  return `/app/${workspaceId}${suffix}`;
}

export function buildNav(workspaceId: string): NavItem[] {
  const posts = path(workspaceId, '/posts');
  const stories = path(workspaceId, '/stories');
  const grabber = path(workspaceId, '/automations/grabber');
  const suggest = path(workspaceId, '/automations/suggest');
  const antispam = path(workspaceId, '/automations/antispam');
  const crosspost = path(workspaceId, '/automations/crosspost');
  const ord = path(workspaceId, '/ord');
  const team = path(workspaceId, '/team');
  const billing = path(workspaceId, '/billing');

  return [
    {
      id: 'dashboard',
      label: 'Дашборд',
      href: path(workspaceId, ''),
      priority: 'p0',
      icon: 'dashboard',
      permission: 'workspace.view',
      synonyms: ['главная', 'обзор', 'dashboard'],
    },
    {
      id: 'calendar',
      label: 'Календарь',
      href: path(workspaceId, '/calendar'),
      priority: 'p0',
      icon: 'calendar',
      permission: 'calendar.view',
      synonyms: ['расписание', 'calendar'],
    },
    {
      id: 'inbox',
      label: 'Инбокс',
      href: soon(workspaceId, 'inbox'),
      priority: 'p0',
      icon: 'inbox',
      permission: 'workspace.view',
      badge: 'inbox',
      synonyms: ['входящие', 'inbox'],
    },
    {
      id: 'messenger',
      label: 'Мессенджер диалогов',
      href: soon(workspaceId, 'messenger'),
      priority: 'p0',
      icon: 'messenger',
      permission: 'workspace.view',
      badge: 'messenger',
      synonyms: ['диалоги', 'сообщения', 'messenger'],
      children: [
        { id: 'messenger-all', label: 'Все диалоги', href: soon(workspaceId, 'messenger-all'), synonyms: ['все чаты'] },
        {
          id: 'messenger-unread',
          label: 'Непрочитанные',
          href: soon(workspaceId, 'messenger-unread'),
          synonyms: ['непрочитанные диалоги'],
        },
        {
          id: 'messenger-sources',
          label: 'Подключённые источники',
          href: soon(workspaceId, 'messenger-sources'),
          synonyms: ['источники'],
        },
        { id: 'messenger-archive', label: 'Архив', href: soon(workspaceId, 'messenger-archive'), synonyms: ['архив диалогов'] },
        {
          id: 'messenger-notifications',
          label: 'Настройки уведомлений',
          href: path(workspaceId, '/settings/notifications'),
          permission: 'notifications.manage_self',
          synonyms: ['уведомления'],
        },
      ],
    },
    {
      id: 'create-post',
      label: 'Создать пост',
      href: path(workspaceId, '/posts/new'),
      priority: 'p0',
      icon: 'create-post',
      permission: 'posts.view',
      synonyms: ['новый пост', 'написать'],
      children: [
        {
          id: 'post-now',
          label: 'Пост сейчас',
          href: path(workspaceId, '/posts/new'),
          permission: 'posts.create',
          synonyms: ['опубликовать'],
        },
        {
          id: 'post-schedule',
          label: 'Запланировать пост',
          href: path(workspaceId, '/posts/new'),
          permission: 'posts.schedule',
          synonyms: ['отложить пост'],
        },
        { id: 'post-drafts', label: 'Черновики', href: `${posts}?status=draft`, permission: 'posts.view', synonyms: ['draft'] },
        {
          id: 'post-templates',
          label: 'Шаблоны',
          href: soon(workspaceId, 'post-templates'),
          permission: 'posts.view',
          synonyms: ['шаблон поста'],
        },
        {
          id: 'post-history',
          label: 'История публикаций',
          href: `${posts}?status=published`,
          permission: 'posts.view',
          synonyms: ['опубликованные посты'],
        },
      ],
    },
    {
      id: 'create-story',
      label: 'Создать историю',
      href: path(workspaceId, '/stories/new'),
      priority: 'p0',
      icon: 'create-story',
      permission: 'posts.view',
      synonyms: ['новая история', 'сторис'],
      children: [
        {
          id: 'story-now',
          label: 'История сейчас',
          href: path(workspaceId, '/stories/new'),
          permission: 'posts.create',
          synonyms: ['опубликовать историю'],
        },
        {
          id: 'story-schedule',
          label: 'Запланировать историю',
          href: path(workspaceId, '/stories/new'),
          permission: 'posts.schedule',
          synonyms: ['отложить историю'],
        },
        {
          id: 'story-drafts',
          label: 'Черновики',
          href: `${stories}?status=draft`,
          permission: 'posts.view',
          synonyms: ['черновики историй'],
        },
        {
          id: 'story-templates',
          label: 'Шаблоны',
          href: soon(workspaceId, 'story-templates'),
          permission: 'posts.view',
          synonyms: ['шаблон истории'],
        },
        {
          id: 'story-history',
          label: 'История публикаций',
          href: `${stories}?status=published`,
          permission: 'posts.view',
          synonyms: ['опубликованные истории'],
        },
      ],
    },
    {
      id: 'posts',
      label: 'Посты',
      href: posts,
      priority: 'p0',
      icon: 'posts',
      permission: 'posts.view',
      synonyms: ['публикации', 'posts'],
      children: [
        { id: 'posts-all', label: 'Все посты', href: posts, permission: 'posts.view' },
        { id: 'posts-scheduled', label: 'Запланированные', href: `${posts}?status=scheduled`, permission: 'posts.view' },
        { id: 'posts-published', label: 'Опубликованные', href: `${posts}?status=published`, permission: 'posts.view' },
        { id: 'posts-drafts', label: 'Черновики', href: `${posts}?status=draft`, permission: 'posts.view' },
        { id: 'posts-failed', label: 'Ошибки публикации', href: `${posts}?status=failed`, permission: 'posts.view', synonyms: ['ошибки'] },
      ],
    },
    {
      id: 'stories',
      label: 'Истории',
      href: stories,
      priority: 'p0',
      icon: 'stories',
      permission: 'posts.view',
      synonyms: ['stories', 'сторис'],
      children: [
        { id: 'stories-all', label: 'Все истории', href: stories, permission: 'posts.view' },
        {
          id: 'stories-scheduled',
          label: 'Запланированные',
          href: `${stories}?status=scheduled`,
          permission: 'posts.view',
        },
        {
          id: 'stories-published',
          label: 'Опубликованные',
          href: `${stories}?status=published`,
          permission: 'posts.view',
        },
        { id: 'stories-drafts', label: 'Черновики', href: `${stories}?status=draft`, permission: 'posts.view' },
        { id: 'stories-failed', label: 'Ошибки публикации', href: `${stories}?status=failed`, permission: 'posts.view' },
      ],
    },
    {
      id: 'communities',
      label: 'Мои сообщества',
      href: soon(workspaceId, 'communities'),
      priority: 'p0',
      icon: 'communities',
      permission: 'channels.view',
      synonyms: ['сообщества', 'группы'],
      children: [
        { id: 'communities-table', label: 'Все сообщества', href: soon(workspaceId, 'communities-table'), synonyms: ['таблица'] },
        { id: 'communities-grid', label: 'Плитка', href: soon(workspaceId, 'communities-grid'), synonyms: ['иконки'] },
        {
          id: 'communities-stats',
          label: 'Агрегаты и динамика',
          href: soon(workspaceId, 'communities-stats'),
          synonyms: ['динамика'],
        },
        { id: 'communities-export', label: 'Экспорт', href: soon(workspaceId, 'communities-export') },
      ],
    },
    {
      id: 'analytics',
      label: 'Аналитика',
      href: path(workspaceId, '/analytics'),
      priority: 'p0',
      icon: 'analytics',
      permission: 'analytics.view',
      synonyms: ['статистика', 'analytics'],
      children: [
        { id: 'analytics-summary', label: 'Сводная', href: path(workspaceId, '/analytics'), permission: 'analytics.view', synonyms: ['сводка'] },
        {
          id: 'analytics-posts',
          label: 'По постам',
          href: soon(workspaceId, 'analytics-posts'),
          permission: 'analytics.view',
        },
        {
          id: 'analytics-stories',
          label: 'По историям',
          href: soon(workspaceId, 'analytics-stories'),
          permission: 'analytics.view',
        },
        {
          id: 'analytics-communities',
          label: 'По сообществам',
          href: soon(workspaceId, 'analytics-communities'),
          permission: 'analytics.view',
        },
      ],
    },
    {
      id: 'automations',
      label: 'Автоматизации',
      href: path(workspaceId, '/automations'),
      priority: 'p1',
      icon: 'automations',
      permission: 'automations.view',
      testId: 'nav-automations-group',
      synonyms: ['автоматизация', 'сценарии'],
      children: [
        {
          id: 'automations-scenarios',
          label: 'Сценарии',
          href: path(workspaceId, '/automations'),
          permission: 'automations.view',
          synonyms: ['хаб автоматизаций'],
        },
        {
          id: 'automations-funnels',
          label: 'Автоворонки',
          href: soon(workspaceId, 'funnels'),
          permission: 'automations.view',
          synonyms: ['воронки'],
        },
        {
          id: 'automations-schedules',
          label: 'Расписания',
          href: soon(workspaceId, 'schedules'),
          permission: 'automations.view',
        },
        {
          id: 'automations-grabber',
          label: 'Граббер',
          href: grabber,
          permission: 'automations.view',
          synonyms: ['grabber'],
        },
        {
          id: 'automations-suggest',
          label: 'Предложка',
          href: suggest,
          permission: 'automations.view',
          flag: 'suggest',
          synonyms: ['предложка'],
        },
        {
          id: 'automations-antispam',
          label: 'Антиспам',
          href: antispam,
          permission: 'automations.view',
          flag: 'antispam',
        },
        {
          id: 'automations-crosspost',
          label: 'Кросспостинг',
          href: crosspost,
          permission: 'automations.view',
          flag: 'crosspost',
        },
      ],
    },
    {
      id: 'grabber',
      label: 'Граббер',
      href: grabber,
      priority: 'p1',
      icon: 'grabber',
      permission: 'automations.view',
      testId: 'nav-automations-grabber',
      synonyms: ['grabber', 'парсер', 'источники'],
      children: [
        { id: 'grabber-sources', label: 'Источники', href: `${grabber}?tab=rules`, permission: 'automations.view' },
        { id: 'grabber-jobs', label: 'Задания', href: `${grabber}?tab=queue`, permission: 'automations.view' },
        { id: 'grabber-results', label: 'Результаты', href: `${grabber}?tab=queue`, permission: 'automations.view' },
      ],
    },
    {
      id: 'suggest',
      label: 'Предложка',
      href: suggest,
      priority: 'p1',
      icon: 'suggest',
      permission: 'automations.view',
      flag: 'suggest',
      badge: 'suggest',
      testId: 'nav-automations-suggest',
      synonyms: ['предложка', 'suggest', 'модерация'],
      children: [
        { id: 'suggest-new', label: 'Новые', href: suggest, permission: 'automations.view', synonyms: ['новые предложения'] },
        {
          id: 'suggest-moderation',
          label: 'На модерации',
          href: suggest,
          permission: 'automations.view',
          synonyms: ['модерация'],
        },
        { id: 'suggest-rejected', label: 'Отклонённые', href: suggest, permission: 'automations.view' },
        { id: 'suggest-settings', label: 'Настройки', href: suggest, permission: 'automations.manage' },
      ],
    },
    {
      id: 'antispam',
      label: 'Антиспам',
      href: antispam,
      priority: 'p1',
      icon: 'antispam',
      permission: 'automations.view',
      flag: 'antispam',
      badge: 'antispam',
      testId: 'nav-automations-antispam',
      synonyms: ['спам', 'antispam'],
      children: [
        { id: 'antispam-events', label: 'События', href: antispam, permission: 'automations.view' },
        { id: 'antispam-rules', label: 'Правила', href: antispam, permission: 'automations.view' },
        {
          id: 'antispam-lists',
          label: 'Чёрные списки',
          href: antispam,
          permission: 'automations.manage',
          synonyms: ['словари', 'чёрный список'],
        },
        {
          id: 'antispam-reports',
          label: 'Отчёты',
          href: soon(workspaceId, 'antispam-reports'),
          permission: 'automations.view',
        },
      ],
    },
    {
      id: 'crosspost',
      label: 'Кросспостинг',
      href: crosspost,
      priority: 'p1',
      icon: 'crosspost',
      permission: 'automations.view',
      flag: 'crosspost',
      testId: 'nav-automations-crosspost',
      synonyms: ['репост', 'crosspost'],
      children: [
        { id: 'crosspost-rules', label: 'Правила', href: crosspost, permission: 'automations.view' },
        {
          id: 'crosspost-queue',
          label: 'Очередь',
          href: soon(workspaceId, 'crosspost-queue'),
          permission: 'automations.view',
        },
        {
          id: 'crosspost-reports',
          label: 'Отчёты',
          href: soon(workspaceId, 'crosspost-reports'),
          permission: 'automations.view',
        },
      ],
    },
    {
      id: 'ord',
      label: 'Реклама / ОРД',
      href: ord,
      priority: 'p1',
      icon: 'ord',
      permission: 'ord.view',
      synonyms: ['реклама', 'маркировка', 'erid', 'орд'],
      children: [
        { id: 'ord-campaigns', label: 'Кампании', href: soon(workspaceId, 'ord-campaigns'), permission: 'ord.view' },
        { id: 'ord-marking', label: 'Маркировка', href: `${ord}?tab=connection`, permission: 'ord.view', synonyms: ['erid'] },
        {
          id: 'ord-reports',
          label: 'Отчёты ОРД',
          href: `${ord}?tab=reports`,
          permission: 'ord.reports.run',
          synonyms: ['отчёты'],
        },
      ],
    },
    {
      id: 'scripts',
      label: 'Сервисные скрипты',
      href: soon(workspaceId, 'scripts'),
      priority: 'p1',
      icon: 'scripts',
      permission: 'automations.manage',
      badge: 'scripts',
      synonyms: ['скрипты', 'чистка'],
      children: [
        { id: 'scripts-dogs', label: 'Чистка собачек', href: soon(workspaceId, 'scripts-dogs') },
        { id: 'scripts-blacklist', label: 'Чистка ЧС', href: soon(workspaceId, 'scripts-blacklist') },
        { id: 'scripts-posts', label: 'Чистка постов', href: soon(workspaceId, 'scripts-posts') },
        { id: 'scripts-videos', label: 'Чистка видео', href: soon(workspaceId, 'scripts-videos') },
        { id: 'scripts-groups', label: 'Аналитика групп', href: soon(workspaceId, 'scripts-groups') },
        { id: 'scripts-jobs', label: 'Запущенные задачи', href: soon(workspaceId, 'scripts-jobs') },
      ],
    },
    {
      id: 'channels',
      label: 'Каналы',
      href: path(workspaceId, '/channels'),
      priority: 'p1',
      icon: 'channels',
      permission: 'channels.view',
      synonyms: ['подключения', 'channels'],
      children: [
        { id: 'channels-list', label: 'Список каналов', href: path(workspaceId, '/channels'), permission: 'channels.view' },
        {
          id: 'channels-connect',
          label: 'Подключения',
          href: path(workspaceId, '/channels/connect'),
          permission: 'channels.manage',
        },
        {
          id: 'channels-settings',
          label: 'Настройки',
          href: soon(workspaceId, 'channels-settings'),
          permission: 'channels.manage',
        },
      ],
    },
    {
      id: 'media',
      label: 'Медиатека',
      href: path(workspaceId, '/media'),
      priority: 'p1',
      icon: 'media',
      permission: 'media.view',
      synonyms: ['файлы', 'медиа', 'media'],
      children: [
        { id: 'media-files', label: 'Файлы', href: path(workspaceId, '/media'), permission: 'media.view' },
        { id: 'media-folders', label: 'Папки', href: path(workspaceId, '/media'), permission: 'media.view' },
        { id: 'media-uploads', label: 'Загрузки', href: path(workspaceId, '/media'), permission: 'media.manage' },
      ],
    },
    {
      id: 'projects',
      label: 'Проекты',
      href: path(workspaceId, '/projects'),
      priority: 'p2',
      icon: 'projects',
      permission: 'projects.view',
      synonyms: ['проекты'],
      children: [
        { id: 'projects-list', label: 'Список проектов', href: path(workspaceId, '/projects'), permission: 'projects.view' },
        {
          id: 'projects-archive',
          label: 'Архив',
          href: soon(workspaceId, 'projects-archive'),
          permission: 'projects.view',
        },
      ],
    },
    {
      id: 'team',
      label: 'Команда',
      href: team,
      priority: 'p2',
      icon: 'team',
      permission: 'members.view',
      synonyms: ['участники', 'сотрудники'],
      children: [
        { id: 'team-members', label: 'Участники', href: `${team}?tab=members`, permission: 'members.view' },
        { id: 'team-roles', label: 'Роли', href: `${team}?tab=members`, permission: 'members.update', synonyms: ['права'] },
        {
          id: 'team-invites',
          label: 'Приглашения',
          href: `${team}?tab=invitations`,
          permission: 'members.invite',
        },
      ],
    },
    {
      id: 'settings',
      label: 'Настройки',
      href: path(workspaceId, '/settings'),
      priority: 'p2',
      icon: 'settings',
      permission: 'workspace.view',
      synonyms: ['settings', 'параметры'],
      children: [
        { id: 'settings-profile', label: 'Профиль', href: path(workspaceId, '/settings/profile'), synonyms: ['аккаунт'] },
        {
          id: 'settings-workspace',
          label: 'Пространство',
          href: path(workspaceId, '/settings/workspace'),
          permission: 'workspace.update',
        },
        {
          id: 'settings-integrations',
          label: 'Интеграции',
          href: soon(workspaceId, 'settings-integrations'),
          permission: 'workspace.update',
        },
        {
          id: 'settings-notifications',
          label: 'Уведомления',
          href: path(workspaceId, '/settings/notifications'),
          permission: 'notifications.manage_self',
        },
      ],
    },
    {
      id: 'billing',
      label: 'Биллинг',
      href: billing,
      priority: 'p2',
      icon: 'billing',
      permission: 'billing.view',
      synonyms: ['оплата', 'тариф', 'подписка'],
      children: [
        { id: 'billing-plan', label: 'Тариф', href: `${billing}#billing-plan`, permission: 'billing.view' },
        { id: 'billing-payments', label: 'Платежи', href: `${billing}#billing-payments`, permission: 'billing.view' },
        { id: 'billing-docs', label: 'Документы', href: `${billing}#billing-documents`, permission: 'billing.view', synonyms: ['счета'] },
      ],
    },
  ];
}

function nodeAllowed(node: NavNode, access: NavAccess): boolean {
  if (node.flag && !access.flags[node.flag]) {
    return false;
  }
  if (node.permission && !access.can(node.permission)) {
    return false;
  }
  return true;
}

export function filterNav(items: NavItem[], access: NavAccess): NavItem[] {
  const visible: NavItem[] = [];
  for (const item of items) {
    if (!nodeAllowed(item, access)) {
      continue;
    }
    const children = (item.children ?? []).filter((child) => nodeAllowed(child, access));
    if (item.children && item.children.length > 0 && children.length === 0) {
      continue;
    }
    visible.push({ ...item, children });
  }
  return visible;
}

export function toggleOpenId(current: string | null, id: string): string | null {
  return current === id ? null : id;
}

function fold(value: string): string {
  return value.trim().toLocaleLowerCase('ru-RU').replaceAll('ё', 'е');
}

function matches(label: string, synonyms: string[] | undefined, query: string): boolean {
  const haystack = [label, ...(synonyms ?? [])].map(fold);
  return haystack.some((entry) => entry.includes(query));
}

export function searchNav(items: NavItem[], rawQuery: string): SearchHit[] {
  const query = fold(rawQuery);
  if (!query) {
    return [];
  }
  const hits: SearchHit[] = [];
  for (const item of items) {
    if (matches(item.label, item.synonyms, query)) {
      hits.push({ id: item.id, label: item.label, href: item.href });
    }
    for (const child of item.children ?? []) {
      if (matches(child.label, child.synonyms, query)) {
        hits.push({ id: child.id, label: child.label, href: child.href, parentLabel: item.label });
      }
    }
  }
  return hits;
}

export function formatBadge(count: number | undefined): string | null {
  if (count == null || count <= 0) {
    return null;
  }
  return count > 99 ? '99+' : String(count);
}

export function placeMenu(
  anchor: { top: number; right: number; bottom: number; left: number },
  viewport: { width: number; height: number },
  itemCount: number,
): MenuPlacement {
  const full = Math.max(1, itemCount) * MENU_ITEM_HEIGHT + MENU_CHROME;
  const cap = MENU_VISIBLE_CAP * MENU_ITEM_HEIGHT + MENU_CHROME;
  const desired = Math.min(full, cap);
  const gap = 4;
  const spaceRight = viewport.width - anchor.right;
  const spaceLeft = anchor.left;
  const openLeft = spaceRight < MENU_WIDTH + gap && spaceLeft > spaceRight;
  let left = openLeft ? anchor.left - MENU_WIDTH - gap : anchor.right + gap;
  left = Math.max(8, Math.min(left, viewport.width - MENU_WIDTH - 8));
  const spaceBelow = viewport.height - anchor.top;
  const spaceAbove = anchor.bottom;
  const openUp = spaceBelow < desired && spaceAbove > spaceBelow;
  const available = Math.max(MENU_ITEM_HEIGHT + MENU_CHROME, (openUp ? spaceAbove : spaceBelow) - 8);
  const maxHeight = Math.min(cap, full, available);
  let top = openUp ? anchor.bottom - maxHeight : anchor.top;
  top = Math.max(8, Math.min(top, viewport.height - maxHeight - 8));
  return { top, left, maxHeight };
}

export function readFavorites(): string[] {
  if (typeof window === 'undefined') {
    return [];
  }
  try {
    const raw = window.localStorage.getItem(FAVORITES_KEY);
    const parsed = raw ? (JSON.parse(raw) as unknown) : [];
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter((entry): entry is string => typeof entry === 'string').slice(0, FAVORITES_LIMIT);
  } catch {
    return [];
  }
}

export function writeFavorites(ids: string[]): string[] {
  const next = ids.slice(0, FAVORITES_LIMIT);
  window.localStorage.setItem(FAVORITES_KEY, JSON.stringify(next));
  return next;
}

export function toggleFavorite(id: string): string[] {
  const current = readFavorites();
  if (current.includes(id)) {
    return writeFavorites(current.filter((entry) => entry !== id));
  }
  if (current.length >= FAVORITES_LIMIT) {
    return current;
  }
  return writeFavorites([...current, id]);
}

export type QuickAction = {
  id: string;
  label: string;
  href?: string;
  event?: 'create-workspace';
};

export function quickActions(workspaceId: string, access: NavAccess): QuickAction[] {
  const actions: QuickAction[] = [];
  if (access.can('posts.create')) {
    actions.push({
      id: 'quick-post',
      label: 'Создать пост',
      href: path(workspaceId, '/posts/new'),
    });
    actions.push({
      id: 'quick-story',
      label: 'Создать историю',
      href: path(workspaceId, '/stories/new'),
    });
  }
  if (access.can('projects.manage')) {
    actions.push({
      id: 'quick-project',
      label: 'Создать проект',
      href: path(workspaceId, '/projects'),
    });
  }
  actions.push({ id: 'quick-workspace', label: 'Создать пространство', event: 'create-workspace' });
  return actions;
}

export const OPEN_COMMAND_PALETTE_EVENT = 'posts:open-command-palette';
export const OPEN_CREATE_WORKSPACE_EVENT = 'posts:open-create-workspace';

export function requestOpenCommandPalette(): void {
  window.dispatchEvent(new Event(OPEN_COMMAND_PALETTE_EVENT));
}

export function requestCreateWorkspace(): void {
  window.dispatchEvent(new Event(OPEN_CREATE_WORKSPACE_EVENT));
}

/** Longest matching path wins so /posts/new highlights «Создать пост», not «Посты». */
export function activeItemId(items: NavItem[], pathname: string): string | null {
  let best: { id: string; length: number } | null = null;
  for (const item of items) {
    const length = matchLength(item.href, pathname);
    if (length == null) {
      continue;
    }
    if (!best || length > best.length) {
      best = { id: item.id, length };
    }
  }
  return best?.id ?? null;
}

function matchLength(href: string, pathname: string): number | null {
  const pathOnly = href.split(/[?#]/)[0] ?? href;
  if (pathname === pathOnly) {
    return pathOnly.length + 1;
  }
  const segments = pathOnly.split('/').filter(Boolean);
  const isDashboard = segments.length === 2 && segments[0] === 'app';
  if (isDashboard) {
    return null;
  }
  if (pathname.startsWith(`${pathOnly}/`)) {
    return pathOnly.length;
  }
  return null;
}
