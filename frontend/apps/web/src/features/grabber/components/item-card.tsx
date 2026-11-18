'use client';

import type { GrabberItem } from '@posts/api-client';
import { Badge, Button, cn } from '@posts/ui';
import { useTranslations } from 'next-intl';

type ItemCardProps = {
  item: GrabberItem;
  selected: boolean;
  canModerate: boolean;
  onToggle: (id: string) => void;
  onApprove: (id: string) => void;
  onReject: (id: string) => void;
  onEdit: (item: GrabberItem) => void;
  busy?: boolean;
};

export function ItemCard({
  item,
  selected,
  canModerate,
  onToggle,
  onApprove,
  onReject,
  onEdit,
  busy,
}: ItemCardProps) {
  const t = useTranslations('grabber.queue');
  const id = item.id!;
  const pending = item.status === 'pending_moderation';

  return (
    <article
      data-testid={`grabber-item-${id}`}
      className={cn(
        'space-y-3 border-b border-border py-4 last:border-b-0',
        selected && 'bg-muted/40',
      )}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          {canModerate && pending ? (
            <input
              type="checkbox"
              className="mt-1 h-4 w-4"
              checked={selected}
              onChange={() => onToggle(id)}
              aria-label={t('selectItem')}
              data-testid={`grabber-item-select-${id}`}
            />
          ) : null}
          <div className="min-w-0 space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={pending ? 'scheduled' : 'published'}>
                {t(`itemStatus.${item.status ?? 'pending_moderation'}`)}
              </Badge>
              <span className="text-xs text-muted-foreground">
                {item.sourceName ?? item.sourceNetwork}
              </span>
              {item.detectedAt ? (
                <time className="text-xs text-muted-foreground" dateTime={item.detectedAt}>
                  {new Date(item.detectedAt).toLocaleString('ru-RU')}
                </time>
              ) : null}
            </div>
            <p className="whitespace-pre-wrap text-sm text-foreground">
              {(item.contentText ?? '').slice(0, 420)}
              {(item.contentText?.length ?? 0) > 420 ? '…' : ''}
            </p>
            <div className="flex flex-wrap gap-3 text-xs">
              {item.sourcePermalink ? (
                <a
                  href={item.sourcePermalink}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-primary underline-offset-2 hover:underline"
                >
                  {t('original')}
                </a>
              ) : null}
              {item.postId ? (
                <span className="text-muted-foreground">
                  {t('postId')}: {item.postId}
                </span>
              ) : null}
            </div>
          </div>
        </div>
        {canModerate && pending ? (
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              disabled={busy}
              onClick={() => onApprove(id)}
              data-testid={`grabber-approve-${id}`}
            >
              {t('approve')}
            </Button>
            <Button
              size="sm"
              variant="outline"
              disabled={busy}
              onClick={() => onEdit(item)}
              data-testid={`grabber-edit-${id}`}
            >
              {t('editApprove')}
            </Button>
            <Button
              size="sm"
              variant="destructive"
              disabled={busy}
              onClick={() => onReject(id)}
              data-testid={`grabber-reject-${id}`}
            >
              {t('reject')}
            </Button>
          </div>
        ) : null}
      </div>
    </article>
  );
}
