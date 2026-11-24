'use client';

import { cn } from '@posts/ui';
import {
  BadgeCheck,
  Ban,
  BarChart3,
  CalendarDays,
  ChevronRight,
  CreditCard,
  FolderKanban,
  FolderOpen,
  GitBranch,
  Inbox,
  LayoutDashboard,
  Megaphone,
  MessageSquarePlus,
  MessagesSquare,
  PenLine,
  Radio,
  Search,
  Settings,
  Sparkles,
  Star,
  Terminal,
  Users,
  Workflow,
  type LucideIcon,
} from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';

import { WorkspaceSwitcher } from './workspace-switcher';
import { UserAccountMenu } from './user-account-menu';
import { NavSubmenu } from './nav-submenu';
import { useNavAccess } from './use-nav-access';
import { useNavBadges } from './use-nav-badges';
import {
  activeItemId,
  buildNav,
  filterNav,
  formatBadge,
  quickActions,
  readFavorites,
  requestCreateWorkspace,
  requestOpenCommandPalette,
  toggleFavorite,
  toggleOpenId,
  type NavBadgeKey,
  type NavIcon,
  type NavItem,
} from './nav-model';

const ICONS: Record<NavIcon, LucideIcon> = {
  dashboard: LayoutDashboard,
  calendar: CalendarDays,
  inbox: Inbox,
  messenger: MessagesSquare,
  'create-post': PenLine,
  'create-story': Sparkles,
  posts: Megaphone,
  stories: Sparkles,
  communities: Users,
  analytics: BarChart3,
  automations: Workflow,
  grabber: Workflow,
  suggest: MessageSquarePlus,
  antispam: Ban,
  crosspost: GitBranch,
  ord: BadgeCheck,
  scripts: Terminal,
  channels: Radio,
  media: FolderOpen,
  projects: FolderKanban,
  team: Users,
  settings: Settings,
  billing: CreditCard,
};

type AppSidebarProps = {
  workspaceId: string;
  className?: string;
  mode?: 'sidebar' | 'drawer';
};

type OpenMenu = {
  id: string;
  anchor: HTMLElement;
};

export function AppSidebar({ workspaceId, className, mode = 'sidebar' }: AppSidebarProps) {
  const pathname = usePathname() ?? '';
  const access = useNavAccess(workspaceId);
  const items = useMemo(() => filterNav(buildNav(workspaceId), access), [access, workspaceId]);
  const actions = useMemo(() => quickActions(workspaceId, access), [access, workspaceId]);
  const suggestVisible = items.some((item) => item.badge === 'suggest');
  const badges = useNavBadges(workspaceId, suggestVisible);
  const counts: Partial<Record<NavBadgeKey, number | undefined>> = { suggest: badges.suggest };
  const [openId, setOpenId] = useState<string | null>(null);
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [favorites, setFavorites] = useState<string[]>([]);

  useEffect(() => {
    setFavorites(readFavorites());
  }, []);

  useEffect(() => {
    setOpenId(null);
    setAnchor(null);
  }, [pathname]);

  useEffect(() => {
    if (!openId) {
      return;
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpenId(null);
        setAnchor(null);
      }
    };
    const onPointer = (event: MouseEvent) => {
      const target = event.target as Element | null;
      if (target?.closest('[data-nav-menu], [data-nav-chevron]')) {
        return;
      }
      setOpenId(null);
      setAnchor(null);
    };
    document.addEventListener('keydown', onKey);
    document.addEventListener('mousedown', onPointer);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('mousedown', onPointer);
    };
  }, [openId]);

  const activeId = activeItemId(items, pathname);
  const pinned = favorites
    .map((id) => items.find((item) => item.id === id))
    .filter((item): item is NavItem => Boolean(item));
  const p0 = items.filter((item) => item.priority === 'p0');
  const p1 = items.filter((item) => item.priority === 'p1');
  const p2 = items.filter((item) => item.priority === 'p2');
  const openItem = items.find((item) => item.id === openId);

  const closeMenu = () => {
    setOpenId(null);
    setAnchor(null);
  };

  const renderItem = (item: NavItem) => {
    const Icon = ICONS[item.icon];
    const active = item.id === activeId;
    const open = openId === item.id;
    const menuId = `nav-menu-${item.id}`;
    const badge = item.badge ? formatBadge(counts[item.badge]) : null;
    const pinnedOn = favorites.includes(item.id);
    const hasChildren = (item.children?.length ?? 0) > 0;
    return (
      <div key={item.id} data-testid={item.testId}>
        <div
          className={cn(
            'flex h-8 items-center gap-0.5 rounded-md pr-1 text-sm',
            active ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-muted hover:text-foreground',
          )}
        >
          <Link
            href={item.href}
            data-testid={`nav-item-${item.id}`}
            aria-current={active ? 'page' : undefined}
            className="flex h-8 min-w-0 flex-1 items-center gap-2 rounded-md px-2 font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            onClick={closeMenu}
          >
            <Icon className="h-4 w-4 shrink-0" aria-hidden />
            <span className="truncate">{item.label}</span>
            {badge ? (
              <span
                className="ml-auto inline-flex min-w-5 items-center justify-center rounded-full bg-primary px-1.5 text-[10px] font-semibold leading-4 text-primary-foreground"
                data-testid={`nav-badge-${item.id}`}
              >
                {badge}
              </span>
            ) : null}
          </Link>
          <button
            type="button"
            className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-pressed={pinnedOn}
            aria-label={pinnedOn ? `Открепить: ${item.label}` : `Закрепить: ${item.label}`}
            data-testid={`nav-pin-${item.id}`}
            onClick={() => setFavorites(toggleFavorite(item.id))}
          >
            <Star className={cn('h-3.5 w-3.5', pinnedOn && 'fill-current text-primary')} aria-hidden />
          </button>
          {hasChildren ? (
            <button
              type="button"
              data-nav-chevron
              data-testid={`nav-chevron-${item.id}`}
              className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-haspopup="menu"
              aria-expanded={open}
              aria-controls={open ? menuId : undefined}
              aria-label={`Подпункты: ${item.label}`}
              onClick={(event) => {
                const row = event.currentTarget.parentElement;
                if (!row) {
                  return;
                }
                const next = toggleOpenId(openId, item.id);
                setOpenId(next);
                setAnchor(next ? row : null);
              }}
              onKeyDown={(event) => {
                if (event.key === 'ArrowDown' && !open) {
                  event.preventDefault();
                  const row = event.currentTarget.parentElement;
                  if (!row) {
                    return;
                  }
                  setOpenId(item.id);
                  setAnchor(row);
                }
              }}
            >
              <ChevronRight
                className={cn('h-4 w-4', mode === 'drawer' && open && 'rotate-90')}
                aria-hidden
              />
            </button>
          ) : (
            <span className="inline-flex h-7 w-7 shrink-0" aria-hidden />
          )}
        </div>
        {mode === 'drawer' && open && item.children ? (
          <ul
            id={menuId}
            role="menu"
            aria-label={item.label}
            data-nav-menu
            className="ml-4 border-l border-border py-1 pl-2"
            onKeyDown={(event) => {
              const links = event.currentTarget.querySelectorAll<HTMLElement>('[role="menuitem"]');
              const current = Array.from(links).indexOf(document.activeElement as HTMLElement);
              if (event.key === 'ArrowDown') {
                event.preventDefault();
                links[(current + 1) % links.length]?.focus();
              } else if (event.key === 'ArrowUp') {
                event.preventDefault();
                links[(current - 1 + links.length) % links.length]?.focus();
              } else if (event.key === 'Escape') {
                event.preventDefault();
                closeMenu();
              }
            }}
          >
            {item.children.map((child) => (
              <li key={child.id}>
                <Link
                  href={child.href}
                  role="menuitem"
                  data-testid={`nav-child-${child.id}`}
                  className="block rounded-md px-2 py-1.5 text-sm text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  onClick={closeMenu}
                >
                  {child.label}
                </Link>
              </li>
            ))}
          </ul>
        ) : null}
      </div>
    );
  };

  const openMenu: OpenMenu | null =
    mode === 'sidebar' && openItem && anchor && openItem.children && openItem.children.length > 0
      ? { id: openItem.id, anchor }
      : null;

  return (
    <aside
      className={cn(
        'flex h-full w-sidebar flex-col border-r border-border bg-card/80 backdrop-blur-sm',
        className,
      )}
      aria-label="Sidebar"
    >
      <div className={cn('shrink-0 space-y-3 border-b border-border p-3', mode === 'drawer' && 'pr-10')}>
        <UserAccountMenu workspaceId={workspaceId} />
        <WorkspaceSwitcher currentWorkspaceId={workspaceId} />
        <button
          type="button"
          onClick={requestOpenCommandPalette}
          className="flex h-8 w-full items-center gap-2 rounded-md border border-border px-2 text-sm text-muted-foreground hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          data-testid="nav-search"
        >
          <Search className="h-4 w-4 shrink-0" aria-hidden />
          <span className="flex-1 text-left">Поиск</span>
          <span className="text-[10px] tracking-wide">Ctrl K</span>
        </button>
        <div className="flex flex-col gap-1" data-testid="nav-quick-actions">
          {actions.map((action) =>
            action.href ? (
              <Link
                key={action.id}
                href={action.href}
                className="flex h-8 items-center rounded-md px-2 text-sm font-medium text-foreground hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {action.label}
              </Link>
            ) : (
              <button
                key={action.id}
                type="button"
                className="flex h-8 items-center rounded-md px-2 text-left text-sm font-medium text-foreground hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                onClick={requestCreateWorkspace}
              >
                {action.label}
              </button>
            ),
          )}
        </div>
      </div>
      <nav className="flex-1 space-y-0.5 overflow-y-auto p-2" aria-label="Main">
        {pinned.length > 0 ? (
          <div className="mb-2" data-testid="nav-pinned">
            <p className="px-2 py-1 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
              Закреплено
            </p>
            {pinned.map((item) => {
              const Icon = ICONS[item.icon];
              return (
                <Link
                  key={`pin-${item.id}`}
                  href={item.href}
                  data-testid={`nav-pinned-${item.id}`}
                  className="flex h-8 items-center gap-2 rounded-md px-2 text-sm font-medium text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <Icon className="h-4 w-4 shrink-0" aria-hidden />
                  <span className="truncate">{item.label}</span>
                </Link>
              );
            })}
          </div>
        ) : null}
        <div data-testid="nav-p0">{p0.map(renderItem)}</div>
        <div data-testid="nav-p1">{p1.map(renderItem)}</div>
        {p2.length > 0 ? <div role="separator" className="my-2 border-t border-border" data-testid="nav-p2-divider" /> : null}
        <div data-testid="nav-p2">{p2.map(renderItem)}</div>
      </nav>
      {openMenu && openItem?.children ? (
        <NavSubmenu
          items={openItem.children}
          label={openItem.label}
          menuId={`nav-menu-${openItem.id}`}
          anchor={openMenu.anchor}
          pathname={pathname}
          onClose={closeMenu}
        />
      ) : null}
    </aside>
  );
}
