'use client';

import { Badge, cn } from '@posts/ui';
import { useVirtualizer } from '@tanstack/react-virtual';
import { Play } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useMemo, useRef } from 'react';

import type { MediaLibraryItem } from '../model/types';
import { MediaPreview } from './media-preview';

const COLUMNS = 4;
const ROW_HEIGHT = 168;
/** Below this count, skip virtualization (tests / small libraries). */
const VIRTUALIZE_THRESHOLD = 48;

type MediaGridProps = {
  items: MediaLibraryItem[];
  selectedIds?: Set<string>;
  onSelect?: (item: MediaLibraryItem) => void;
  onOpen?: (item: MediaLibraryItem) => void;
  selectable?: boolean;
};

function MediaCard({
  item,
  selected,
  selectable,
  onSelect,
  onOpen,
  statusLabel,
}: {
  item: MediaLibraryItem;
  selected?: boolean;
  selectable?: boolean;
  onSelect?: (item: MediaLibraryItem) => void;
  onOpen?: (item: MediaLibraryItem) => void;
  statusLabel: string;
}) {
  const id = item.id ?? '';
  return (
    <button
      key={id}
      type="button"
      className={cn(
        'group flex h-[144px] flex-col overflow-hidden rounded-md border border-border bg-card text-left transition-shadow focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
        selected && 'ring-2 ring-primary',
      )}
      onClick={() => {
        if (selectable && onSelect) {
          onSelect(item);
        } else {
          onOpen?.(item);
        }
      }}
      onDoubleClick={() => onOpen?.(item)}
      aria-pressed={selectable ? selected : undefined}
      data-testid={`media-card-${id}`}
    >
      <div className="relative h-24 overflow-hidden bg-muted/40 text-muted-foreground">
        <MediaPreview previewUrl={item.previewUrl} kind={item.kind} />
        {item.kind === 'video' || item.kind === 'clip' ? (
          <span
            className="pointer-events-none absolute bottom-1.5 right-1.5 inline-flex h-6 w-6 items-center justify-center rounded-full bg-black/70 text-white shadow-sm"
            data-testid="media-play-badge"
            aria-hidden
          >
            <Play className="h-3 w-3 translate-x-px fill-current" />
          </span>
        ) : null}
        <Badge
          variant={
            item.status === 'ready'
              ? 'published'
              : item.status === 'failed'
                ? 'failed'
                : 'scheduled'
          }
          className="absolute left-2 top-2 text-[10px]"
        >
          {statusLabel}
        </Badge>
      </div>
      <div className="truncate px-2 py-2 text-xs font-medium">{item.filename ?? id}</div>
    </button>
  );
}

export function MediaGrid({
  items,
  selectedIds,
  onSelect,
  onOpen,
  selectable,
}: MediaGridProps) {
  const t = useTranslations('media.status');
  const parentRef = useRef<HTMLDivElement>(null);

  const rows = useMemo(() => {
    const result: MediaLibraryItem[][] = [];
    for (let i = 0; i < items.length; i += COLUMNS) {
      result.push(items.slice(i, i + COLUMNS));
    }
    return result;
  }, [items]);

  const virtualizer = useVirtualizer({
    count: items.length > VIRTUALIZE_THRESHOLD ? rows.length : 0,
    getScrollElement: () => parentRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 4,
  });

  if (items.length <= VIRTUALIZE_THRESHOLD) {
    return (
      <div
        className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4"
        data-testid="media-grid"
      >
        {items.map((item) => (
          <MediaCard
            key={item.id}
            item={item}
            selected={item.id ? selectedIds?.has(item.id) : false}
            selectable={selectable}
            onSelect={onSelect}
            onOpen={onOpen}
            statusLabel={t(item.status ?? 'uploading')}
          />
        ))}
      </div>
    );
  }

  return (
    <div
      ref={parentRef}
      className="h-[min(70vh,720px)] overflow-auto rounded-md border border-border"
      data-testid="media-grid"
    >
      <div className="relative w-full" style={{ height: `${virtualizer.getTotalSize()}px` }}>
        {virtualizer.getVirtualItems().map((virtualRow) => {
          const row = rows[virtualRow.index] ?? [];
          return (
            <div
              key={virtualRow.key}
              className="absolute left-0 top-0 grid w-full grid-cols-4 gap-3 px-3"
              style={{
                transform: `translateY(${virtualRow.start}px)`,
                height: `${virtualRow.size}px`,
                paddingTop: 12,
              }}
            >
              {row.map((item) => (
                <MediaCard
                  key={item.id}
                  item={item}
                  selected={item.id ? selectedIds?.has(item.id) : false}
                  selectable={selectable}
                  onSelect={onSelect}
                  onOpen={onOpen}
                  statusLabel={t(item.status ?? 'uploading')}
                />
              ))}
            </div>
          );
        })}
      </div>
    </div>
  );
}
