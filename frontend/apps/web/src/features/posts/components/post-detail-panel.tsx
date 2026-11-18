'use client';

import type { Post, Publication } from '@posts/api-client';
import {
  AdMarkingSlot,
  Button,
  EmptyState,
  NetworkIcon,
  PostStatusBadge,
  Skeleton,
  toast,
  type SocialNetwork,
} from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ExternalLink, RotateCcw } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';

import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';
import { usePublicationUpdates } from '@/shared/ws/use-publication-updates';

import {
  getPost,
  listPostPublications,
  retryPublication,
} from '../api/posts-api';
import { isRetryablePublication } from '../model/status';

type PostDetailPanelProps = {
  workspaceId: string;
  postId: string;
};

function networkIcon(network?: string): SocialNetwork {
  if (network === 'tg' || network === 'telegram') return 'telegram';
  if (network === 'ok' || network === 'max' || network === 'rutube') return network;
  return 'vk';
}

export function PostDetailPanel({ workspaceId, postId }: PostDetailPanelProps) {
  const t = useTranslations('posts');
  const queryClient = useQueryClient();
  const canRetry = useCan('posts.retry_publish', workspaceId);

  usePublicationUpdates({ workspaceId, postId, enabled: true });

  const postQuery = useQuery({
    queryKey: queryKeys.posts.detail(workspaceId, postId),
    queryFn: () => getPost(postId),
  });

  const pubsQuery = useQuery({
    queryKey: queryKeys.publications.byPost(workspaceId, postId),
    queryFn: () => listPostPublications(postId),
  });

  const retryMutation = useMutation({
    mutationFn: (publicationId: string) => retryPublication(publicationId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.publications.byPost(workspaceId, postId),
      });
      await queryClient.invalidateQueries({
        queryKey: queryKeys.posts.detail(workspaceId, postId),
      });
      toast({ title: t('detail.retrySuccess') });
    },
    onError: (error: Error) => {
      toast({
        title: t('detail.retryError'),
        description: error.message,
        variant: 'destructive',
      });
    },
  });

  if (postQuery.isLoading || pubsQuery.isLoading) {
    return (
      <div className="space-y-3" data-testid="post-detail-loading">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  const post = postQuery.data;
  if (!post) {
    return (
      <EmptyState title={t('detail.notFoundTitle')} description={t('detail.notFoundBody')} />
    );
  }

  const publications = pubsQuery.data?.items ?? [];
  const adMarking = readAdMarking(post);

  return (
    <div className="space-y-6" data-testid="post-detail-panel">
      <header className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className="text-xl font-semibold tracking-tight">
            {post.title || t('untitled')}
          </h2>
          <PostStatusBadge
            status={post.status ?? 'draft'}
            label={t(`status.${post.status ?? 'draft'}`)}
          />
        </div>
        {post.scheduledAt ? (
          <p className="text-sm text-muted-foreground">
            {t('detail.scheduledAt', {
              value: format(new Date(post.scheduledAt), 'd MMM yyyy, HH:mm', { locale: ru }),
            })}
          </p>
        ) : null}
        {post.baseContent?.text ? (
          <p className="max-w-prose whitespace-pre-wrap text-sm text-foreground/90">
            {post.baseContent.text}
          </p>
        ) : null}
        <AdMarkingSlot
          enabled={adMarking.enabled}
          erid={adMarking.erid}
          advertiserName={adMarking.advertiserName}
        />
      </header>

      <section aria-labelledby="publications-heading" className="space-y-3">
        <h3 id="publications-heading" className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
          {t('detail.publications')}
        </h3>
        {publications.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('detail.noPublications')}</p>
        ) : (
          <ul className="divide-y divide-border rounded-md border border-border">
            {publications.map((pub) => (
              <PublicationRow
                key={pub.id}
                publication={pub}
                canRetry={canRetry}
                busy={retryMutation.isPending}
                onRetry={() => pub.id && retryMutation.mutate(pub.id)}
                labels={{
                  attempts: t('detail.attempts', { count: pub.attempts ?? 0 }),
                  retry: t('detail.retry'),
                  openRemote: t('detail.openRemote'),
                  error: pub.errorMessage,
                }}
                stateLabel={t(`publicationState.${pub.state ?? 'pending'}`)}
              />
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

type PublicationRowProps = {
  publication: Publication;
  canRetry: boolean;
  busy?: boolean;
  onRetry: () => void;
  stateLabel: string;
  labels: {
    attempts: string;
    retry: string;
    openRemote: string;
    error?: string;
  };
};

function PublicationRow({
  publication,
  canRetry,
  busy,
  onRetry,
  stateLabel,
  labels,
}: PublicationRowProps) {
  return (
    <li
      className="flex flex-wrap items-start gap-3 px-3 py-3"
      data-testid="publication-row"
      data-publication-state={publication.state}
    >
      <NetworkIcon network={networkIcon(publication.network)} className="mt-0.5 h-5 w-5" />
      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <PostStatusBadge status={publication.state ?? 'pending'} label={stateLabel} />
          <span className="text-xs text-muted-foreground">{labels.attempts}</span>
        </div>
        {labels.error ? (
          <p className="text-sm text-destructive" role="alert">
            {labels.error}
            {publication.errorCode ? ` (${publication.errorCode})` : ''}
          </p>
        ) : null}
        {publication.remoteUrl ? (
          <a
            href={publication.remoteUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1 text-sm text-primary underline-offset-2 hover:underline"
          >
            <ExternalLink className="h-3.5 w-3.5" aria-hidden />
            {labels.openRemote}
          </a>
        ) : null}
      </div>
      {canRetry && isRetryablePublication(publication.state) ? (
        <Button
          type="button"
          size="sm"
          variant="secondary"
          disabled={busy}
          onClick={onRetry}
          data-testid="publication-retry"
        >
          <RotateCcw className="h-4 w-4" aria-hidden />
          {labels.retry}
        </Button>
      ) : null}
    </li>
  );
}

/** Read future ad-marking fields without breaking when OpenAPI lacks them. */
function readAdMarking(post: Post): {
  enabled: boolean;
  erid?: string;
  advertiserName?: string;
} {
  const extended = post as Post & {
    adMarking?: { enabled?: boolean; erid?: string; advertiserName?: string };
  };
  if (extended.adMarking) {
    return {
      enabled: Boolean(extended.adMarking.enabled || extended.ad),
      erid: extended.adMarking.erid,
      advertiserName: extended.adMarking.advertiserName,
    };
  }
  return { enabled: Boolean(post.ad) };
}
