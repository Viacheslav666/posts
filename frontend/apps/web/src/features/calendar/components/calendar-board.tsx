'use client';

import {
  DndContext,
  DragOverlay,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core';
import { CSS } from '@dnd-kit/utilities';
import type { CalendarItem } from '@posts/api-client';
import { CalendarEventCard, cn } from '@posts/ui';
import { format, isSameDay, isSameMonth, isToday } from 'date-fns';
import { ru } from 'date-fns/locale';
import {
  useCallback,
  useMemo,
  useState,
  type CSSProperties,
  type KeyboardEvent,
} from 'react';
import { useTranslations } from 'next-intl';

import {
  buildMonthGrid,
  buildWeekDays,
  dayKey,
  hourSlots,
  type CalendarViewMode,
} from '../model/date-range';
import {
  combineDayAndTime,
  createPendingMove,
  eventDragId,
  parseEventDragId,
  parseSlotDropId,
  slotDropId,
  type PendingMove,
} from '../model/dnd';

export type CalendarGhost = {
  id: string;
  title: string;
  dayKey: string;
};

export type CalendarBoardProps = {
  items: CalendarItem[];
  mode: CalendarViewMode;
  anchor: Date;
  canMove: boolean;
  onMoveRequest: (pending: PendingMove) => void;
  onEventClick: (id: string) => void;
  ghosts?: CalendarGhost[];
  selectedId?: string | null;
};

function isMovableStatus(status?: string): boolean {
  return status !== 'published' && status !== 'publishing' && status !== 'partially_published';
}

function formatEventTime(iso?: string): string {
  if (!iso) return '';
  return format(new Date(iso), 'HH:mm');
}

function groupByDay(items: CalendarItem[]): Map<string, CalendarItem[]> {
  const map = new Map<string, CalendarItem[]>();
  for (const item of items) {
    if (!item.scheduledAt || !item.id) continue;
    const key = dayKey(item.scheduledAt);
    const list = map.get(key) ?? [];
    list.push(item);
    map.set(key, list);
  }
  for (const list of map.values()) {
    list.sort((a, b) => (a.scheduledAt ?? '').localeCompare(b.scheduledAt ?? ''));
  }
  return map;
}

function DraggableEvent({
  item,
  canDrag,
  selected,
  onClick,
}: {
  item: CalendarItem;
  canDrag: boolean;
  selected?: boolean;
  onClick: () => void;
}) {
  const id = item.id!;
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({
    id: eventDragId(id),
    disabled: !canDrag,
    data: { item },
  });

  const style: CSSProperties | undefined = transform
    ? { transform: CSS.Translate.toString(transform), opacity: isDragging ? 0.4 : 1 }
    : isDragging
      ? { opacity: 0.4 }
      : undefined;

  return (
    <CalendarEventCard
      ref={setNodeRef}
      style={style}
      time={formatEventTime(item.scheduledAt)}
      title={item.title ?? '—'}
      networks={item.networks}
      status={item.status}
      contentKind={item.contentKind === 'story' ? 'story' : 'post'}
      selected={selected}
      onClick={onClick}
      {...(canDrag ? { ...listeners, ...attributes } : {})}
    />
  );
}

function DayCell({
  day,
  items,
  ghosts,
  canMove,
  inMonth,
  focused,
  tabIndex,
  onFocusDay,
  onKeyDown,
  onEventClick,
  selectedId,
  droppableId,
}: {
  day: Date;
  items: CalendarItem[];
  ghosts: CalendarGhost[];
  canMove: boolean;
  inMonth: boolean;
  focused: boolean;
  tabIndex: number;
  onFocusDay: () => void;
  onKeyDown: (event: KeyboardEvent<HTMLDivElement>) => void;
  onEventClick: (id: string) => void;
  selectedId?: string | null;
  droppableId: string;
}) {
  const { setNodeRef, isOver } = useDroppable({ id: droppableId });
  const label = format(day, 'd MMMM yyyy', { locale: ru });

  return (
    <div
      ref={setNodeRef}
      role="gridcell"
      tabIndex={tabIndex}
      aria-label={label}
      aria-selected={focused}
      data-day={dayKey(day)}
      onFocus={onFocusDay}
      onKeyDown={onKeyDown}
      className={cn(
        'min-h-[7rem] border border-border/70 bg-background p-1 outline-none transition-colors',
        !inMonth && 'bg-muted/30 text-muted-foreground',
        isToday(day) && 'bg-[hsl(var(--status-scheduled)/0.06)]',
        isOver && 'ring-2 ring-ring ring-inset',
        focused && 'ring-2 ring-ring ring-inset',
      )}
    >
      <div className="mb-1 flex items-center justify-between px-0.5">
        <span
          className={cn(
            'inline-flex h-6 w-6 items-center justify-center rounded-full text-xs font-medium',
            isToday(day) && 'bg-primary text-primary-foreground',
          )}
        >
          {format(day, 'd')}
        </span>
      </div>
      <div className="flex flex-col gap-1">
        {ghosts.map((ghost) => (
          <div
            key={ghost.id}
            className="rounded-md border border-dashed border-border px-2 py-1 text-[11px] text-muted-foreground"
            data-testid="calendar-ghost"
          >
            {ghost.title}
          </div>
        ))}
        {items.map((item) => (
          <DraggableEvent
            key={item.id}
            item={item}
            canDrag={canMove && isMovableStatus(item.status)}
            selected={selectedId === item.id}
            onClick={() => item.id && onEventClick(item.id)}
          />
        ))}
      </div>
    </div>
  );
}

function HourSlot({
  day,
  hour,
  items,
  canMove,
  onEventClick,
  selectedId,
}: {
  day: Date;
  hour: number;
  items: CalendarItem[];
  canMove: boolean;
  onEventClick: (id: string) => void;
  selectedId?: string | null;
}) {
  const key = dayKey(day);
  const dropId = slotDropId(key, hour);
  const { setNodeRef, isOver } = useDroppable({ id: dropId });
  const slotItems = items.filter((item) => {
    if (!item.scheduledAt) return false;
    return new Date(item.scheduledAt).getHours() === hour;
  });

  return (
    <div
      ref={setNodeRef}
      className={cn(
        'min-h-[3rem] border-b border-border/50 px-1 py-0.5',
        isOver && 'bg-accent/50',
      )}
      data-hour={hour}
    >
      <div className="flex flex-col gap-1">
        {slotItems.map((item) => (
          <DraggableEvent
            key={item.id}
            item={item}
            canDrag={canMove && isMovableStatus(item.status)}
            selected={selectedId === item.id}
            onClick={() => item.id && onEventClick(item.id)}
          />
        ))}
      </div>
    </div>
  );
}

export function CalendarBoard({
  items,
  mode,
  anchor,
  canMove,
  onMoveRequest,
  onEventClick,
  ghosts = [],
  selectedId,
}: CalendarBoardProps) {
  const t = useTranslations('calendar');
  const byDay = useMemo(() => groupByDay(items), [items]);
  const monthDays = useMemo(() => buildMonthGrid(anchor), [anchor]);
  const weekDays = useMemo(() => buildWeekDays(anchor), [anchor]);
  const dayColumns = mode === 'day' ? [anchor] : weekDays;
  const hours = useMemo(() => hourSlots(), []);

  const [focusIndex, setFocusIndex] = useState(0);
  const [activeItem, setActiveItem] = useState<CalendarItem | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
  );

  const ghostsByDay = useMemo(() => {
    const map = new Map<string, CalendarGhost[]>();
    for (const ghost of ghosts) {
      const list = map.get(ghost.dayKey) ?? [];
      list.push(ghost);
      map.set(ghost.dayKey, list);
    }
    return map;
  }, [ghosts]);

  const focusDays = mode === 'month' ? monthDays : dayColumns;

  const handleKeyDown = useCallback(
    (index: number, event: KeyboardEvent<HTMLDivElement>) => {
      const cols = mode === 'month' ? 7 : dayColumns.length;
      let next = index;
      if (event.key === 'ArrowRight') next = index + 1;
      else if (event.key === 'ArrowLeft') next = index - 1;
      else if (event.key === 'ArrowDown') next = index + cols;
      else if (event.key === 'ArrowUp') next = index - cols;
      else return;
      event.preventDefault();
      if (next < 0 || next >= focusDays.length) return;
      setFocusIndex(next);
      const el = document.querySelector<HTMLElement>(`[data-day="${dayKey(focusDays[next]!)}"]`);
      el?.focus();
    },
    [dayColumns.length, focusDays, mode],
  );

  const onDragStart = (event: DragStartEvent) => {
    const postId = parseEventDragId(String(event.active.id));
    const item = items.find((entry) => entry.id === postId) ?? null;
    setActiveItem(item);
  };

  const onDragEnd = (event: DragEndEvent) => {
    setActiveItem(null);
    if (!canMove || !event.over) return;
    const postId = parseEventDragId(String(event.active.id));
    const drop = parseSlotDropId(String(event.over.id));
    if (!postId || !drop) return;
    const item = items.find((entry) => entry.id === postId);
    if (!item || !isMovableStatus(item.status)) return;

    const toScheduledAt = combineDayAndTime(drop.dayKey, drop.hour, item.scheduledAt);
    if (item.scheduledAt && dayKey(item.scheduledAt) === drop.dayKey && drop.hour === undefined) {
      const sameDay = isSameDay(new Date(item.scheduledAt), new Date(toScheduledAt));
      if (sameDay && formatEventTime(item.scheduledAt) === formatEventTime(toScheduledAt)) {
        return;
      }
    }

    onMoveRequest(
      createPendingMove({
        item,
        toScheduledAt,
        version: 0,
        hasRecurrence: false,
      }),
    );
  };

  const weekdayLabels = useMemo(() => {
    const start = buildWeekDays(anchor);
    return start.map((d) => format(d, 'EEEEEE', { locale: ru }));
  }, [anchor]);

  return (
    <DndContext sensors={sensors} onDragStart={onDragStart} onDragEnd={onDragEnd}>
      <div data-testid="calendar-board" className="w-full overflow-x-auto">
        {mode === 'month' ? (
          <div role="grid" aria-label={t('board.monthLabel')} className="min-w-[640px]">
            <div className="grid grid-cols-7 border-b border-border bg-muted/40">
              {weekdayLabels.map((label) => (
                <div
                  key={label}
                  className="px-2 py-2 text-center text-xs font-medium uppercase tracking-wide text-muted-foreground"
                >
                  {label}
                </div>
              ))}
            </div>
            <div className="grid grid-cols-7">
              {monthDays.map((day, index) => {
                const key = dayKey(day);
                return (
                  <DayCell
                    key={key}
                    day={day}
                    items={byDay.get(key) ?? []}
                    ghosts={ghostsByDay.get(key) ?? []}
                    canMove={canMove}
                    inMonth={isSameMonth(day, anchor)}
                    focused={focusIndex === index}
                    tabIndex={focusIndex === index ? 0 : -1}
                    onFocusDay={() => setFocusIndex(index)}
                    onKeyDown={(event) => handleKeyDown(index, event)}
                    onEventClick={onEventClick}
                    selectedId={selectedId}
                    droppableId={slotDropId(key)}
                  />
                );
              })}
            </div>
          </div>
        ) : (
          <div
            role="grid"
            aria-label={mode === 'week' ? t('board.weekLabel') : t('board.dayLabel')}
            className="min-w-[720px]"
          >
            <div
              className="grid border-b border-border bg-muted/40"
              style={{ gridTemplateColumns: `3.5rem repeat(${dayColumns.length}, minmax(0, 1fr))` }}
            >
              <div className="px-1 py-2" />
              {dayColumns.map((day, index) => (
                <div
                  key={dayKey(day)}
                  role="columnheader"
                  tabIndex={focusIndex === index ? 0 : -1}
                  aria-label={format(day, 'd MMMM yyyy', { locale: ru })}
                  onFocus={() => setFocusIndex(index)}
                  onKeyDown={(event) => handleKeyDown(index, event)}
                  className={cn(
                    'px-2 py-2 text-center text-sm font-medium outline-none',
                    isToday(day) && 'text-[hsl(var(--status-scheduled))]',
                  )}
                >
                  <div className="text-xs uppercase text-muted-foreground">
                    {format(day, 'EEEEEE', { locale: ru })}
                  </div>
                  <div>{format(day, 'd MMM', { locale: ru })}</div>
                </div>
              ))}
            </div>
            <div
              className="grid"
              style={{ gridTemplateColumns: `3.5rem repeat(${dayColumns.length}, minmax(0, 1fr))` }}
            >
              <div className="flex flex-col">
                {hours.map((hour) => (
                  <div
                    key={hour}
                    className="flex min-h-[3rem] items-start justify-end border-b border-border/50 pr-2 pt-1 text-[11px] tabular-nums text-muted-foreground"
                  >
                    {String(hour).padStart(2, '0')}:00
                  </div>
                ))}
              </div>
              {dayColumns.map((day) => {
                const key = dayKey(day);
                const dayItems = byDay.get(key) ?? [];
                return (
                  <div key={key} className="border-l border-border/60">
                    {(ghostsByDay.get(key) ?? []).map((ghost) => (
                      <div
                        key={ghost.id}
                        className="mx-1 mt-1 rounded-md border border-dashed border-border px-2 py-1 text-[11px] text-muted-foreground"
                      >
                        {ghost.title}
                      </div>
                    ))}
                    {hours.map((hour) => (
                      <HourSlot
                        key={`${key}-${hour}`}
                        day={day}
                        hour={hour}
                        items={dayItems}
                        canMove={canMove}
                        onEventClick={onEventClick}
                        selectedId={selectedId}
                      />
                    ))}
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>
      <DragOverlay>
        {activeItem ? (
          <CalendarEventCard
            time={formatEventTime(activeItem.scheduledAt)}
            title={activeItem.title ?? '—'}
            networks={activeItem.networks}
            status={activeItem.status}
            contentKind={activeItem.contentKind === 'story' ? 'story' : 'post'}
            className="shadow-lg"
          />
        ) : null}
      </DragOverlay>
    </DndContext>
  );
}
