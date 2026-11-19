'use client';

import type { SuggestItem } from '@posts/api-client';
import { Badge, Button, cn } from '@posts/ui';
import { useTranslations } from 'next-intl';

import { ReasonChips } from '@/features/automations-common';

type ItemCardProps = {
  item: SuggestItem;
  selected: boolean;
  canModerate: boolean;
  onToggle: (id: string) => void;
  onApprove: (id: string) => void;
  onApproveEdit: (item: SuggestItem) => void;
  onReject: (id: string, deleteRemote: boolean) => void;
  busy?: boolean;
};

export function ItemCard({
  item,
  selected,
  canModerate,
  onToggle,
  onApprove,
  onApproveEdit,
  onReject,
  busy,
}: ItemCardProps) {
  const t = useTranslations('automations.suggest.queue');
  const id = item.id!;
  const pending = item.status === 'pending_moderation';
  const authorUrl = item.authorVkId ? `https://vk.com/id${item.authorVkId}` : undefined;

  return (
    <article
      data-testid={`suggest-item-${id}`}
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
              data-testid={`suggest-item-select-${id}`}
            />
          ) : null}
          <div className="min-w-0 space-y-1">
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant={pending ? 'scheduled' : 'published'}>
                {t(`itemStatus.${item.status ?? 'pending_moderation'}`)}
              </Badge>
              {authorUrl ? (
                <a
                  href={authorUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs text-primary underline-offset-2 hover:underline"
                  data-testid={`suggest-author-${id}`}
                >
                  {t('author', { id: item.authorVkId })}
                </a>
              ) : null}
              {item.createdAt ? (
                <time className="text-xs text-muted-foreground" dateTime={item.createdAt}>
                  {new Date(item.createdAt).toLocaleString('ru-RU')}
                </time>
              ) : null}
            </div>
            <p className="whitespace-pre-wrap text-sm text-foreground">
              {(item.text ?? '').slice(0, 420)}
              {(item.text?.length ?? 0) > 420 ? '…' : ''}
            </p>
            {(item.attachments?.length ?? 0) > 0 ? (
              <p className="text-xs text-muted-foreground">
                {t('attachments', { count: item.attachments!.length })}
              </p>
            ) : null}
            <ReasonChips codes={item.reasons ?? []} />
          </div>
        </div>
        {canModerate && pending ? (
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              disabled={busy}
              onClick={() => onApprove(id)}
              data-testid={`suggest-approve-${id}`}
            >
              {t('approve')}
            </Button>
            <Button
              size="sm"
              variant="outline"
              disabled={busy}
              onClick={() => onApproveEdit(item)}
              data-testid={`suggest-approve-edit-${id}`}
            >
              {t('approveEdit')}
            </Button>
            <Button
              size="sm"
              variant="destructive"
              disabled={busy}
              onClick={() => onReject(id, true)}
              data-testid={`suggest-reject-delete-${id}`}
            >
              {t('rejectDelete')}
            </Button>
            <Button
              size="sm"
              variant="outline"
              disabled={busy}
              onClick={() => onReject(id, false)}
              data-testid={`suggest-reject-keep-${id}`}
            >
              {t('rejectKeep')}
            </Button>
          </div>
        ) : null}
      </div>
    </article>
  );
}
