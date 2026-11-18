import type { ButtonHTMLAttributes } from 'react';
import { forwardRef } from 'react';

import { cn } from '../lib/utils';

import { NetworkIcon, type SocialNetwork } from './network-icon';

const STATUS_ACCENT: Record<string, string> = {
  draft: 'border-l-[hsl(var(--status-draft))]',
  scheduled: 'border-l-[hsl(var(--status-scheduled))]',
  publishing: 'border-l-[hsl(var(--status-publishing))]',
  published: 'border-l-[hsl(var(--status-published))]',
  failed: 'border-l-[hsl(var(--status-failed))]',
  paused: 'border-l-[hsl(var(--status-paused))]',
  review: 'border-l-[hsl(var(--status-review))]',
  partially_published: 'border-l-[hsl(var(--status-publishing))]',
  pending_approval: 'border-l-[hsl(var(--status-review))]',
  archived: 'border-l-[hsl(var(--status-draft))]',
};

const STATUS_DOT: Record<string, string> = {
  draft: 'bg-[hsl(var(--status-draft))]',
  scheduled: 'bg-[hsl(var(--status-scheduled))]',
  publishing: 'bg-[hsl(var(--status-publishing))]',
  published: 'bg-[hsl(var(--status-published))]',
  failed: 'bg-[hsl(var(--status-failed))]',
  paused: 'bg-[hsl(var(--status-paused))]',
  review: 'bg-[hsl(var(--status-review))]',
  partially_published: 'bg-[hsl(var(--status-publishing))]',
  pending_approval: 'bg-[hsl(var(--status-review))]',
  archived: 'bg-[hsl(var(--status-draft))]',
};

function toSocialNetwork(network: string): SocialNetwork | null {
  switch (network) {
    case 'vk':
      return 'vk';
    case 'tg':
    case 'telegram':
      return 'telegram';
    case 'ok':
      return 'ok';
    case 'max':
      return 'max';
    case 'rutube':
      return 'rutube';
    default:
      return null;
  }
}

export type CalendarEventCardProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  time: string;
  title: string;
  networks?: string[];
  status?: string;
  contentKind?: 'post' | 'story';
  selected?: boolean;
};

export const CalendarEventCard = forwardRef<HTMLButtonElement, CalendarEventCardProps>(
  function CalendarEventCard(
    {
      time,
      title,
      networks = [],
      status = 'draft',
      contentKind = 'post',
      selected = false,
      className,
      type = 'button',
      ...props
    },
    ref,
  ) {
    const accent = STATUS_ACCENT[status] ?? STATUS_ACCENT.draft;
    const dot = STATUS_DOT[status] ?? STATUS_DOT.draft;
    const icons = networks
      .map(toSocialNetwork)
      .filter((n): n is SocialNetwork => n !== null);
    const isStory = contentKind === 'story';

    return (
      <button
        ref={ref}
        type={type}
        data-testid="calendar-event"
        data-status={status}
        data-content-kind={contentKind}
        className={cn(
          'flex w-full flex-col gap-0.5 rounded-md border border-border border-l-4 bg-background px-2 py-1.5 text-left text-xs shadow-sm transition-colors',
          'hover:bg-accent/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
          accent,
          isStory && 'bg-violet-500/5',
          selected && 'ring-2 ring-ring',
          className,
        )}
        {...props}
      >
        <span className="flex items-center gap-1.5">
          <span className={cn('h-1.5 w-1.5 shrink-0 rounded-full', dot)} aria-hidden />
          <span className="font-medium tabular-nums text-muted-foreground">{time}</span>
          {isStory ? (
            <span
              className="rounded bg-violet-500/15 px-1 py-0.5 text-[9px] font-semibold uppercase tracking-wide text-violet-700 dark:text-violet-300"
              data-testid="calendar-story-badge"
            >
              Story
            </span>
          ) : null}
          {icons.length > 0 ? (
            <span className="ml-auto flex items-center gap-0.5">
              {icons.map((network) => (
                <NetworkIcon
                  key={network}
                  network={network}
                  size="sm"
                  className="h-4 w-4 text-[8px]"
                />
              ))}
            </span>
          ) : null}
        </span>
        <span className="line-clamp-2 font-medium leading-snug text-foreground">{title}</span>
      </button>
    );
  },
);
