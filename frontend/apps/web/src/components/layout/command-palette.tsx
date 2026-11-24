'use client';

import { cn } from '@posts/ui';
import { Search } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useRef, useState } from 'react';

import {
  buildNav,
  filterNav,
  OPEN_COMMAND_PALETTE_EVENT,
  quickActions,
  requestCreateWorkspace,
  searchNav,
} from './nav-model';
import { useNavAccess } from './use-nav-access';

type CommandPaletteProps = {
  workspaceId: string;
};

export function CommandPalette({ workspaceId }: CommandPaletteProps) {
  const router = useRouter();
  const access = useNavAccess(workspaceId);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [index, setIndex] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  const items = useMemo(() => filterNav(buildNav(workspaceId), access), [access, workspaceId]);
  const actions = useMemo(() => quickActions(workspaceId, access), [access, workspaceId]);
  const hits = useMemo(() => searchNav(items, query), [items, query]);
  const visibleActions = query.trim()
    ? actions.filter((action) => action.label.toLocaleLowerCase('ru-RU').includes(query.trim().toLocaleLowerCase('ru-RU')))
    : actions;
  const rows = [
    ...visibleActions.map((action) => ({ kind: 'action' as const, action })),
    ...hits.map((hit) => ({ kind: 'hit' as const, hit })),
  ];

  useEffect(() => {
    const onOpen = () => {
      setQuery('');
      setIndex(0);
      setOpen(true);
    };
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setOpen((current) => {
          if (current) {
            return false;
          }
          setQuery('');
          setIndex(0);
          return true;
        });
      }
    };
    window.addEventListener(OPEN_COMMAND_PALETTE_EVENT, onOpen);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener(OPEN_COMMAND_PALETTE_EVENT, onOpen);
      window.removeEventListener('keydown', onKey);
    };
  }, []);

  useEffect(() => {
    if (open) {
      inputRef.current?.focus();
    }
  }, [open]);

  useEffect(() => {
    setIndex(0);
  }, [query]);

  if (!open) {
    return null;
  }

  const go = (href: string) => {
    setOpen(false);
    router.push(href);
  };

  const activate = (rowIndex: number) => {
    const row = rows[rowIndex];
    if (!row) {
      return;
    }
    if (row.kind === 'action') {
      setOpen(false);
      if (row.action.event === 'create-workspace') {
        requestCreateWorkspace();
        return;
      }
      if (row.action.href) {
        router.push(row.action.href);
      }
      return;
    }
    go(row.hit.href);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/40 p-4 pt-[12vh]" onMouseDown={() => setOpen(false)}>
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Поиск по меню"
        data-testid="command-palette"
        className="w-full max-w-lg overflow-hidden rounded-lg border border-border bg-popover shadow-md"
        onMouseDown={(event) => event.stopPropagation()}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            event.preventDefault();
            setOpen(false);
          } else if (event.key === 'ArrowDown') {
            event.preventDefault();
            setIndex((current) => (rows.length === 0 ? 0 : (current + 1) % rows.length));
          } else if (event.key === 'ArrowUp') {
            event.preventDefault();
            setIndex((current) => (rows.length === 0 ? 0 : (current - 1 + rows.length) % rows.length));
          } else if (event.key === 'Enter') {
            event.preventDefault();
            activate(index);
          }
        }}
      >
        <label className="flex items-center gap-2 border-b border-border px-3">
          <Search className="h-4 w-4 text-muted-foreground" aria-hidden />
          <input
            ref={inputRef}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Поиск по меню"
            aria-label="Поиск по меню"
            className="h-11 w-full bg-transparent text-sm outline-none"
            data-testid="command-palette-input"
          />
        </label>
        <ul className="max-h-80 overflow-y-auto p-1" role="listbox" aria-label="Результаты">
          {rows.length === 0 ? (
            <li className="px-3 py-6 text-center text-sm text-muted-foreground">Ничего не найдено</li>
          ) : (
            rows.map((row, rowIndex) => {
              const label = row.kind === 'action' ? row.action.label : row.hit.label;
              const hint = row.kind === 'hit' ? row.hit.parentLabel : 'Быстрое действие';
              return (
                <li key={row.kind === 'action' ? row.action.id : row.hit.id}>
                  <button
                    type="button"
                    role="option"
                    aria-selected={rowIndex === index}
                    className={cn(
                      'flex w-full items-center justify-between gap-3 rounded-sm px-2 py-2 text-left text-sm',
                      rowIndex === index ? 'bg-accent text-accent-foreground' : 'hover:bg-accent',
                    )}
                    onMouseEnter={() => setIndex(rowIndex)}
                    onClick={() => activate(rowIndex)}
                  >
                    <span>{label}</span>
                    {hint ? <span className="truncate text-xs text-muted-foreground">{hint}</span> : null}
                  </button>
                </li>
              );
            })
          )}
        </ul>
      </div>
    </div>
  );
}
