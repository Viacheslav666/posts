'use client';

import type { CreatePostRequest, OrdProvider } from '@posts/api-client';
import { Button, toast } from '@posts/ui';
import Link from 'next/link';
import { useCallback, useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { PlanLimitPaywall } from '@/features/billing/components/plan-limit-paywall';
import { registerPostOrd } from '@/features/editor/api/posts-api';
import { AdMarkingForm } from '@/features/editor/components/ad-marking-form';
import { useWorkspaceChannels } from '@/features/channels/hooks/use-workspace-channels';
import { useOrdConnections } from '@/features/ord/hooks/use-ord-queries';
import { isBillingFrozen, isPlanLimitExceeded } from '@/shared/lib/errors';
import { isValidInn } from '@/shared/lib/inn';
import { useCan } from '@/shared/hooks/use-can';

import { createStoryPost } from '../api/stories-api';
import { createStoryDraft, mediaKindFromItem, withStoryLink } from '../model/defaults';
import { STORY_TTL_OPTIONS } from '../model/link-texts';
import {
  selectedNetworksIncludeOk,
  selectedNetworksSupportAds,
  validateStoryDraft,
} from '../model/validation';
import type { StoryDraft } from '../model/types';
import { StoryChannelPicker } from './story-channel-picker';
import { StoryMediaBlock } from './story-media-block';
import { StoryOptionsForm } from './story-options-form';
import { StoryPreview } from './story-preview';

type StoryEditorProps = {
  workspaceId: string;
};

function buildCreateRequest(
  draft: StoryDraft,
  channels: { id: string; network: 'vk' | 'tg' | 'ok' | 'max' | 'rutube' }[],
  mode: 'draft' | 'schedule' | 'now',
): CreatePostRequest {
  const scheduledAt =
    mode === 'draft'
      ? undefined
      : mode === 'now'
        ? new Date().toISOString()
        : draft.scheduledAt;

  const story = withStoryLink(
    {
      ...draft.story,
      mediaKind: mediaKindFromItem(draft.media),
      autoDeleteAfterMinutes: draft.autoDeleteAfterMinutes ?? undefined,
      autoDeleteAt: draft.autoDeleteAt ?? undefined,
      ads: draft.isAd ? draft.ad : undefined,
    },
    draft.linkText,
    draft.linkUrl,
  );

  return {
    workspaceId: draft.workspaceId,
    title: draft.title || draft.media?.filename || 'История',
    contentKind: 'story',
    baseContent: {
      text: '',
      mediaIds: draft.media?.mediaId ? [draft.media.mediaId] : [],
    },
    scheduledAt,
    timezone: draft.timezone,
    story,
    ad: draft.isAd,
    adMarking: draft.isAd ? draft.ad : undefined,
    variants: draft.selectedChannelIds
      .map((channelId) => {
        const channel = channels.find((item) => item.id === channelId);
        if (!channel || (channel.network !== 'vk' && channel.network !== 'ok')) {
          return null;
        }
        return {
          channelId,
          network: channel.network,
          content: {
            text: '',
            mediaIds: draft.media?.mediaId ? [draft.media.mediaId] : [],
          },
        };
      })
      .filter((item): item is NonNullable<typeof item> => item != null),
  };
}

export function StoryEditor({ workspaceId }: StoryEditorProps) {
  const t = useTranslations('stories');
  const [draft, setDraft] = useState<StoryDraft>(() => createStoryDraft(workspaceId));
  const [submitting, setSubmitting] = useState(false);
  const [registering, setRegistering] = useState(false);
  const [showPaywall, setShowPaywall] = useState(false);

  const { channels, isLoading: channelsLoading, refetch } = useWorkspaceChannels(workspaceId);
  const ordConnections = useOrdConnections(workspaceId, true);
  const canCreate = useCan('posts.create', workspaceId);
  const canSchedule = useCan('posts.schedule', workspaceId);
  const canScheduleAd = useCan('posts.ad.schedule', workspaceId);

  const patchDraft = useCallback((patch: Partial<StoryDraft>) => {
    setDraft((current) => ({
      ...current,
      ...patch,
      updatedAt: new Date().toISOString(),
    }));
  }, []);

  const issues = useMemo(() => validateStoryDraft(draft, channels), [draft, channels]);
  const showOkTtlWarning = selectedNetworksIncludeOk(draft.selectedChannelIds, channels);
  const adsSupported = selectedNetworksSupportAds(draft.selectedChannelIds, channels);

  const hasOrdConnection = Boolean(
    ordConnections.data?.items?.some(
      (item) => item.hasApiKey && (item.status === 'active' || item.status === 'ok'),
    ),
  );
  const erid = draft.ad.erid;
  const eridStatus: 'idle' | 'missing' | 'ready' | 'error' = !draft.isAd
    ? 'idle'
    : erid
      ? 'ready'
      : 'missing';

  const scheduleBlockedReason = useMemo(() => {
    if (issues.length > 0) {
      return t(issues[0]!.messageKey as Parameters<typeof t>[0]);
    }
    if (draft.isAd) {
      if (!canScheduleAd) return t('ad.noPermission');
      if (!adsSupported) return t('validation.adsNetworks');
      if (!hasOrdConnection) return t('ad.ordNotConfigured');
      if (!isValidInn(draft.ad.inn ?? '')) return t('ad.innInvalid');
      if (!(draft.ad.kktuCodes?.length)) return t('ad.kktuRequired');
      if (!erid) return t('ad.eridMissingBlock');
    }
    return null;
  }, [adsSupported, canScheduleAd, draft, erid, hasOrdConnection, issues, t]);

  const ttlLabel =
    STORY_TTL_OPTIONS.find((item) => item.value === draft.story.ttl)?.label ?? '24 часа';

  const previewChannelName = channels.find((channel) =>
    draft.selectedChannelIds.includes(channel.id),
  )?.name;

  const submit = async (mode: 'draft' | 'schedule' | 'now') => {
    if (!canCreate) {
      toast({
        title: t('actions.blockedTitle'),
        description: t('actions.noCreatePermission'),
        variant: 'destructive',
      });
      return;
    }
    if (mode !== 'draft') {
      if (!canSchedule) {
        toast({
          title: t('actions.blockedTitle'),
          description: t('actions.noSchedulePermission'),
          variant: 'destructive',
        });
        return;
      }
      if (scheduleBlockedReason) {
        toast({
          title: t('actions.blockedTitle'),
          description: scheduleBlockedReason,
          variant: 'destructive',
        });
        return;
      }
    } else if (!draft.media) {
      toast({
        title: t('actions.blockedTitle'),
        description: t('validation.mediaRequired'),
        variant: 'destructive',
      });
      return;
    }

    setSubmitting(true);
    try {
      const post = await createStoryPost(buildCreateRequest(draft, channels, mode));
      toast({
        title: mode === 'draft' ? t('actions.draftSaved') : t('actions.scheduled'),
        description: post.title ?? post.id,
      });
    } catch (error) {
      if (isPlanLimitExceeded(error) || isBillingFrozen(error)) {
        setShowPaywall(true);
      }
      const message = error instanceof Error ? error.message : t('actions.error');
      toast({
        title: t('actions.errorTitle'),
        description: message,
        variant: 'destructive',
      });
    } finally {
      setSubmitting(false);
    }
  };

  const registerErid = async () => {
    setRegistering(true);
    try {
      const created = await createStoryPost({
        ...buildCreateRequest(draft, channels, 'draft'),
        ad: true,
        adMarking: draft.ad,
      });
      const result = await registerPostOrd(created.id ?? '');
      patchDraft({ ad: { ...draft.ad, erid: result.erid } });
      toast({
        title: t('ad.registerOk'),
        description: result.erid ? `erid: ${result.erid}` : undefined,
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : t('ad.eridError');
      toast({
        title: t('ad.eridError'),
        description: message,
        variant: 'destructive',
      });
    } finally {
      setRegistering(false);
    }
  };

  const scheduleDisabled =
    Boolean(scheduleBlockedReason) || submitting || !canSchedule || !canCreate;

  return (
    <div className="flex h-full min-h-[70vh] flex-col gap-4" data-testid="story-editor">
      {showPaywall ? (
        <PlanLimitPaywall workspaceId={workspaceId} variant="hard" limitKey="stories_month" />
      ) : null}
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t('editorTitle')}</h1>
          <p className="text-sm text-muted-foreground">{t('editorDescription')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button asChild variant="outline">
            <Link href={`/app/${workspaceId}/stories/batch`}>{t('actions.batch')}</Link>
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={!canCreate || submitting || !draft.media}
            onClick={() => void submit('draft')}
            data-testid="story-save-draft"
          >
            {t('actions.saveDraft')}
          </Button>
          <Button
            type="button"
            variant="secondary"
            disabled={scheduleDisabled}
            title={scheduleBlockedReason ?? undefined}
            data-testid="story-schedule"
            onClick={() => void submit('schedule')}
          >
            {t('actions.schedule')}
          </Button>
          <Button
            type="button"
            disabled={scheduleDisabled}
            title={scheduleBlockedReason ?? undefined}
            data-testid="story-publish"
            onClick={() => void submit('now')}
          >
            {t('actions.publish')}
          </Button>
        </div>
      </header>

      {scheduleBlockedReason && (draft.isAd || issues.length > 0) ? (
        <p
          className="rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm"
          role="status"
          data-testid="story-schedule-block"
        >
          {scheduleBlockedReason}
        </p>
      ) : null}

      {!canSchedule ? (
        <p
          className="rounded-md border border-border bg-muted/40 px-3 py-2 text-sm text-muted-foreground"
          role="status"
          data-testid="story-rbac-author-hint"
        >
          {t('actions.authorHint')}
        </p>
      ) : null}

      <div className="grid flex-1 gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(280px,320px)]">
        <div className="flex min-w-0 flex-col gap-4">
          <StoryChannelPicker
            workspaceId={workspaceId}
            channels={channels}
            loading={channelsLoading}
            selectedIds={draft.selectedChannelIds}
            onChange={(selectedChannelIds) => patchDraft({ selectedChannelIds })}
            onConnected={() => void refetch()}
          />

          <StoryMediaBlock
            workspaceId={workspaceId}
            media={draft.media}
            onChange={(media) =>
              patchDraft({
                media,
                story: { ...draft.story, mediaKind: mediaKindFromItem(media) },
              })
            }
          />

          <StoryOptionsForm
            draft={draft}
            showOkTtlWarning={showOkTtlWarning}
            onChange={patchDraft}
          />

          {adsSupported ? (
            <AdMarkingForm
              workspaceId={workspaceId}
              enabled={draft.isAd}
              ad={draft.ad}
              provider={draft.ordProvider}
              hasOrdConnection={hasOrdConnection}
              eridStatus={eridStatus}
              erid={erid}
              registering={registering}
              onEnabledChange={(isAd) => patchDraft({ isAd })}
              onAdChange={(ad) => patchDraft({ ad })}
              onProviderChange={(ordProvider: OrdProvider) => patchDraft({ ordProvider })}
              onRegister={() => void registerErid()}
            />
          ) : (
            <p className="text-sm text-muted-foreground" data-testid="story-ads-unavailable">
              {t('ad.unavailable')}
            </p>
          )}
        </div>

        <StoryPreview
          media={draft.media}
          linkText={draft.linkText}
          channelName={previewChannelName}
          adEnabled={draft.isAd}
          erid={erid}
          advertiserName={draft.ad.advertiserName}
          ttlLabel={ttlLabel}
        />
      </div>
    </div>
  );
}
