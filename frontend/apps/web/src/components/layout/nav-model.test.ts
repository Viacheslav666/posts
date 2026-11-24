import { describe, expect, it } from 'vitest';

import type { PermissionAction } from '@/shared/lib/permissions';

import {
  buildNav,
  filterNav,
  formatBadge,
  placeMenu,
  searchNav,
  toggleOpenId,
  type NavAccess,
} from './nav-model';

const WORKSPACE = 'ws';

function access(granted: PermissionAction[], flags: Partial<NavAccess['flags']> = {}): NavAccess {
  const allow = new Set(granted);
  return {
    can: (action) => allow.has(action),
    flags: {
      suggest: flags.suggest ?? true,
      antispam: flags.antispam ?? true,
      crosspost: flags.crosspost ?? true,
    },
  };
}

const VIEWER: PermissionAction[] = [
  'workspace.view',
  'members.view',
  'projects.view',
  'channels.view',
  'media.view',
  'posts.view',
  'calendar.view',
  'analytics.view',
  'ord.view',
  'automations.view',
  'notifications.manage_self',
];

describe('filterNav', () => {
  it('hides billing and create actions a viewer cannot use', () => {
    const visible = filterNav(buildNav(WORKSPACE), access(VIEWER));
    const ids = visible.map((item) => item.id);
    expect(ids).not.toContain('billing');
    expect(ids).not.toContain('scripts');
    expect(ids.indexOf('dashboard')).toBeLessThan(ids.indexOf('analytics'));
    expect(ids.indexOf('media')).toBeLessThan(ids.indexOf('projects'));

    const createPost = visible.find((item) => item.id === 'create-post');
    const childIds = createPost?.children?.map((child) => child.id) ?? [];
    expect(childIds).not.toContain('post-now');
    expect(childIds).not.toContain('post-schedule');
    expect(childIds).toContain('post-drafts');

    const ord = visible.find((item) => item.id === 'ord');
    expect(ord?.children?.map((child) => child.id)).not.toContain('ord-reports');
  });

  it('hides flagged automations when the feature is off', () => {
    const visible = filterNav(buildNav(WORKSPACE), access(VIEWER, { suggest: false, antispam: false }));
    expect(visible.map((item) => item.id)).not.toContain('suggest');
    expect(visible.map((item) => item.id)).not.toContain('antispam');
    const automations = visible.find((item) => item.id === 'automations');
    const childIds = automations?.children?.map((child) => child.id) ?? [];
    expect(childIds).toContain('automations-grabber');
    expect(childIds).not.toContain('automations-suggest');
  });
});

describe('menu behaviour helpers', () => {
  it('keeps a single open dropdown id', () => {
    expect(toggleOpenId(null, 'posts')).toBe('posts');
    expect(toggleOpenId('posts', 'stories')).toBe('stories');
    expect(toggleOpenId('stories', 'stories')).toBeNull();
  });

  it('searches items and subitems by name and synonym', () => {
    const items = filterNav(
      buildNav(WORKSPACE),
      access([...VIEWER, 'posts.create', 'billing.view', 'automations.manage']),
    );
    const hits = searchNav(items, 'предложка');
    expect(hits.some((hit) => hit.id === 'suggest')).toBe(true);
    const drafts = searchNav(items, 'черновики');
    expect(drafts.some((hit) => hit.parentLabel === 'Посты')).toBe(true);
    expect(drafts.some((hit) => hit.parentLabel === 'Истории')).toBe(true);
  });

  it('places the menu to the left and caps the height at ten rows', () => {
    const placement = placeMenu(
      { top: 100, right: 1000, bottom: 132, left: 760 },
      { width: 1024, height: 200 },
      14,
    );
    expect(placement.left).toBeLessThan(760);
    expect(placement.maxHeight).toBeLessThanOrEqual(10 * 36 + 8);
  });

  it('hides empty badges', () => {
    expect(formatBadge(0)).toBeNull();
    expect(formatBadge(undefined)).toBeNull();
    expect(formatBadge(4)).toBe('4');
    expect(formatBadge(120)).toBe('99+');
  });
});
