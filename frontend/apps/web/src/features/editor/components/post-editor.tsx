'use client';

import type { CreatePostRequest, OrdProvider } from '@posts/api-client';
import {
  hasBlockingValidationErrors,
  stripTags,
  unicodeLength,
  validateCanonicalHtml,
  type NetworkId,
  type TiptapNode,
} from '@posts/domain';
import { Button, toast } from '@posts/ui';
import { useCallback, useEffect, useMemo, useState, useTransition } from 'react';
import { useTranslations } from 'next-intl';

import {
  useWorkspaceChannels,
  type EditorChannelOption,
} from '@/features/channels/hooks/use-workspace-channels';
import { useOrdConnections } from '@/features/ord/hooks/use-ord-queries';
import {
  type AdFieldErrors,
  mapServerAdErrors,
} from '@/features/ord/lib/ad-marking-validation';
import { EditorMediaBlock } from '@/features/media/components/editor-media-block';
import { PlanLimitPaywall } from '@/features/billing/components/plan-limit-paywall';
import { isValidInn } from '@/shared/lib/inn';
import { isBillingFrozen, isPlanLimitExceeded } from '@/shared/lib/errors';
import { useCan } from '@/shared/hooks/use-can';

import { createPost, registerPostOrd } from '../api/posts-api';
import { toTextCapabilities, useNetworkCapabilities } from '../hooks/use-network-capabilities';
import { useDraftAutosave } from '../hooks/use-draft-autosave';
import { validateRecurrenceTimes } from '../lib/recurrence-times';
import { invalidVkStringAttachments } from '../lib/vk-string-attachments';
import { createDefaultPostOptions } from '../model/defaults';
import { loadDraft } from '../model/draft-db';
import { EMPTY_DOC, type EditorDraft, type EditorVariant } from '../model/types';

import { AdMarkingForm } from './ad-marking-form';
import { AutosaveIndicator } from './autosave-indicator';
import { ChannelPicker } from './channel-picker';
import { PostOptionsPanel } from './post-options-panel';
import { PreviewPanel } from './preview-panel';
import { TipTapEditor } from './tip-tap-editor';
import { ValidationPanel } from './validation-panel';
import { VariantTabs } from './variant-tabs';
import { VkStringAttachmentsField } from './vk-string-attachments-field';

type PostEditorProps = {
  workspaceId: string;
  draftId?: string;
};

function createDraft(workspaceId: string, draftId: string): EditorDraft {
  return {
    id: draftId,
    workspaceId,
    updatedAt: new Date().toISOString(),
    baseHtml: '',
    baseDoc: EMPTY_DOC,
    selectedChannelIds: [],
    variants: [],
    previewNetwork: 'tg',
    activeVariant: 'base',
    media: [],
    stringAttachments: [],
    publicationKind: 'post',
    options: createDefaultPostOptions(),
  };
}

function normalizeDraft(draft: EditorDraft, workspaceId: string, draftId: string): EditorDraft {
  return {
    ...createDraft(workspaceId, draftId),
    ...draft,
    media: draft.media ?? [],
    stringAttachments: draft.stringAttachments ?? [],
    publicationKind: draft.publicationKind ?? 'post',
    options: {
      ...createDefaultPostOptions(),
      ...(draft.options ?? {}),
      variant: {
        ...createDefaultPostOptions().variant,
        ...(draft.options?.variant ?? {}),
      },
      ad: {
        ...createDefaultPostOptions().ad,
        ...(draft.options?.ad ?? {}),
      },
      recurrence: {
        ...createDefaultPostOptions().recurrence,
        ...(draft.options?.recurrence ?? {}),
      },
    },
  };
}

function networksFromSelection(
  channelIds: string[],
  channels: EditorChannelOption[],
): NetworkId[] {
  const set = new Set<NetworkId>();
  for (const id of channelIds) {
    const channel = channels.find((item) => item.id === id);
    if (channel) {
      set.add(channel.network);
    }
  }
  return [...set];
}

function buildCreateRequest(
  draft: EditorDraft,
  channels: EditorChannelOption[],
  mode: 'draft' | 'schedule' | 'now',
): CreatePostRequest {
  const scheduledAt =
    mode === 'draft'
      ? undefined
      : mode === 'now'
        ? new Date().toISOString()
        : draft.options.scheduledAt;

  const variants = draft.selectedChannelIds
    .map((channelId) => {
      const channel = channels.find((item) => item.id === channelId);
      if (!channel || channel.network === 'rutube') {
        return null;
      }
      const overridden = draft.variants.find(
        (item) => item.network === channel.network && item.overridden,
      );
      const mediaIds = draft.media.map((item) => item.mediaId);
      const stringAttachments =
        channel.network === 'vk' && draft.stringAttachments.length
          ? draft.stringAttachments
          : undefined;
      return {
        channelId,
        network: channel.network,
        content: {
          text: overridden?.html ?? draft.baseHtml,
          mediaIds,
          stringAttachments,
        },
        options: draft.options.variant,
      };
    })
    .filter((item): item is NonNullable<typeof item> => item != null);

  const baseMediaIds = draft.media.map((item) => item.mediaId);
  const vkSelected = draft.selectedChannelIds.some((id) => {
    const channel = channels.find((item) => item.id === id);
    return channel?.network === 'vk';
  });

  return {
    workspaceId: draft.workspaceId,
    title: draft.options.title || stripTags(draft.baseHtml).slice(0, 80),
    baseContent: {
      text: draft.baseHtml,
      mediaIds: baseMediaIds,
      stringAttachments:
        vkSelected && draft.stringAttachments.length ? draft.stringAttachments : undefined,
    },
    scheduledAt,
    timezone: draft.options.timezone,
    recurrence: draft.options.recurrence,
    stopAt: draft.options.stopAt ?? undefined,
    removeAfterPublish: draft.options.removeAfterPublish,
    repost: draft.options.repost ?? undefined,
    contentKind: 'post',
    ad: draft.options.isAd,
    adMarking: draft.options.isAd ? draft.options.ad : undefined,
    variants,
  };
}

export function PostEditor({ workspaceId, draftId = 'new' }: PostEditorProps) {
  const t = useTranslations('editor');
  const [draft, setDraft] = useState<EditorDraft | null>(null);
  const [hydrated, setHydrated] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [registering, setRegistering] = useState(false);
  const [adFieldErrors, setAdFieldErrors] = useState<AdFieldErrors>({});
  const [showPaywall, setShowPaywall] = useState(false);
  const [, startTransition] = useTransition();
  const capabilitiesQuery = useNetworkCapabilities();
  const { channels, isLoading: channelsLoading } = useWorkspaceChannels(workspaceId);
  const ordConnections = useOrdConnections(workspaceId, true);
  const canSchedule = useCan('posts.schedule', workspaceId);
  const canScheduleAd = useCan('posts.ad.schedule', workspaceId);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const stored = await loadDraft(workspaceId, draftId);
      if (cancelled) {
        return;
      }
      const draftValue = stored
        ? normalizeDraft(stored, workspaceId, draftId)
        : createDraft(workspaceId, draftId);
      setDraft(draftValue);
      setHydrated(true);
    })();
    return () => {
      cancelled = true;
    };
  }, [workspaceId, draftId]);

  useEffect(() => {
    if (!draft || channels.length === 0) {
      return;
    }
    if (draft.selectedChannelIds.length > 0) {
      return;
    }
    const defaults = channels.slice(0, 2).map((channel) => channel.id);
    setDraft((current) =>
      current && current.selectedChannelIds.length === 0
        ? { ...current, selectedChannelIds: defaults, updatedAt: new Date().toISOString() }
        : current,
    );
  }, [channels, draft]);

  const { status, lastSavedAt } = useDraftAutosave(draft, hydrated);

  const selectedNetworks = useMemo(
    () => (draft ? networksFromSelection(draft.selectedChannelIds, channels) : []),
    [draft, channels],
  );

  const activeHtml = useMemo(() => {
    if (!draft) {
      return '';
    }
    if (draft.activeVariant === 'base') {
      return draft.baseHtml;
    }
    const variant = draft.variants.find((item) => item.network === draft.activeVariant);
    return variant?.overridden ? variant.html : draft.baseHtml;
  }, [draft]);

  const textCapabilities = useMemo(
    () => toTextCapabilities(capabilitiesQuery.data),
    [capabilitiesQuery.data],
  );

  const issues = useMemo(
    () =>
      validateCanonicalHtml(activeHtml, textCapabilities, {
        selectedNetworks,
      }),
    [activeHtml, textCapabilities, selectedNetworks],
  );

  const charCount = unicodeLength(stripTags(activeHtml));
  const blocked = hasBlockingValidationErrors(issues);

  const hasOrdConnection = Boolean(
    ordConnections.data?.items?.some(
      (item) => item.hasApiKey && (item.status === 'active' || item.status === 'ok'),
    ),
  );

  const erid = draft?.options.ad.erid;
  const eridStatus: 'idle' | 'missing' | 'ready' | 'error' = !draft?.options.isAd
    ? 'idle'
    : erid
      ? 'ready'
      : 'missing';

  const scheduleBlockedReason = useMemo(() => {
    if (!draft) return null;
    if (blocked) return t('actions.blockedValidation');
    if (draft.selectedChannelIds.length === 0) return t('actions.blockedChannels');
    if (draft.options.isAd) {
      if (!canScheduleAd) return t('ad.noPermission');
      if (!hasOrdConnection) return t('ad.ordNotConfigured');
      if (!isValidInn(draft.options.ad.inn ?? '')) return t('ad.innInvalid');
      if (!(draft.options.ad.kktuCodes?.length)) return t('ad.kktuRequired');
      if (!erid) return t('ad.eridMissingBlock');
    }
    return null;
  }, [blocked, canScheduleAd, draft, erid, hasOrdConnection, t]);

  const previewChannel = useMemo(() => {
    if (!draft) {
      return undefined;
    }
    return channels.find(
      (channel) =>
        draft.selectedChannelIds.includes(channel.id) &&
        (channel.network === draft.previewNetwork ||
          (draft.previewNetwork === 'tg' && channel.network === 'tg')),
    );
  }, [draft, channels]);

  const hasVkChannel = selectedNetworks.includes('vk');

  const overriddenMap = useMemo(() => {
    const map: Partial<Record<NetworkId, boolean>> = {};
    for (const variant of draft?.variants ?? []) {
      map[variant.network] = variant.overridden;
    }
    return map;
  }, [draft]);

  const updateBase = useCallback((payload: { html: string; doc: TiptapNode }) => {
    startTransition(() => {
      setDraft((current) =>
        current
          ? {
              ...current,
              baseHtml: payload.html,
              baseDoc: payload.doc,
              updatedAt: new Date().toISOString(),
            }
          : current,
      );
    });
  }, []);

  const updateVariantContent = useCallback(
    (network: NetworkId, payload: { html: string; doc: TiptapNode }) => {
      startTransition(() => {
        setDraft((current) => {
          if (!current) {
            return current;
          }
          const existing = current.variants.find((item) => item.network === network);
          const nextVariant: EditorVariant = {
            network,
            overridden: true,
            html: payload.html,
            doc: payload.doc,
          };
          const variants = existing
            ? current.variants.map((item) => (item.network === network ? nextVariant : item))
            : [...current.variants, nextVariant];
          return {
            ...current,
            variants,
            updatedAt: new Date().toISOString(),
          };
        });
      });
    },
    [],
  );

  const submit = async (mode: 'draft' | 'schedule' | 'now') => {
    if (!draft) return;
    if (mode !== 'draft' && scheduleBlockedReason) {
      toast({
        title: t('actions.blockedTitle'),
        description: scheduleBlockedReason,
        variant: 'destructive',
      });
      return;
    }
    if (mode !== 'draft' && !canSchedule) {
      toast({
        title: t('actions.blockedTitle'),
        description: t('actions.noSchedulePermission'),
        variant: 'destructive',
      });
      return;
    }
    const timesCheck = validateRecurrenceTimes(draft.options.recurrence.times);
    if (!timesCheck.ok) {
      toast({
        title: t('actions.blockedTitle'),
        description: t('options.times.invalidError', {
          times: [...timesCheck.invalid, ...timesCheck.duplicates].join(', ') || '—',
        }),
        variant: 'destructive',
      });
      return;
    }
    if (selectedNetworks.includes('vk')) {
      const bad = invalidVkStringAttachments(draft.stringAttachments);
      if (bad.length) {
        toast({
          title: t('actions.blockedTitle'),
          description: t('stringAttachments.invalid', { tokens: bad.join(', ') }),
          variant: 'destructive',
        });
        return;
      }
    }

    setSubmitting(true);
    try {
      const body = buildCreateRequest(draft, channels, mode);
      const post = await createPost(body);
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
    if (!draft) return;
    setRegistering(true);
    setAdFieldErrors({});
    try {
      // Persist draft post with ad payload first so ORD register can read it.
      const created = await createPost({
        ...buildCreateRequest(draft, channels, 'draft'),
        ad: true,
        adMarking: draft.options.ad,
      });
      const result = await registerPostOrd(created.id ?? '');
      setDraft((current) =>
        current
          ? {
              ...current,
              options: {
                ...current.options,
                ad: { ...current.options.ad, erid: result.erid },
              },
              updatedAt: new Date().toISOString(),
            }
          : current,
      );
      toast({
        title: t('ad.registerOk'),
        description: result.erid ? `erid: ${result.erid}` : undefined,
      });
    } catch (error) {
      setAdFieldErrors(mapServerAdErrors(error));
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

  if (!draft || !hydrated) {
    return <p className="text-sm text-muted-foreground">{t('loading')}</p>;
  }

  const editingVariant =
    draft.activeVariant !== 'base'
      ? draft.variants.find((item) => item.network === draft.activeVariant)
      : undefined;

  const scheduleDisabled = Boolean(scheduleBlockedReason) || submitting || !canSchedule;

  return (
    <div className="flex h-full min-h-[70vh] flex-col gap-4" data-testid="post-editor">
      {showPaywall ? (
        <PlanLimitPaywall workspaceId={workspaceId} variant="hard" limitKey="posts_month" />
      ) : null}
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t('title')}</h1>
          <AutosaveIndicator status={status} lastSavedAt={lastSavedAt} />
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant="outline"
            disabled={blocked || submitting}
            onClick={() => void submit('draft')}
          >
            {t('actions.saveDraft')}
          </Button>
          <Button
            type="button"
            variant="secondary"
            disabled={scheduleDisabled}
            title={scheduleBlockedReason ?? undefined}
            data-testid="editor-schedule"
            onClick={() => void submit('schedule')}
          >
            {t('actions.schedule')}
          </Button>
          <Button
            type="button"
            disabled={scheduleDisabled}
            title={scheduleBlockedReason ?? undefined}
            data-testid="editor-publish"
            onClick={() => void submit('now')}
          >
            {t('actions.publish')}
          </Button>
        </div>
      </header>

      {scheduleBlockedReason && draft.options.isAd ? (
        <p
          className="rounded-md border border-amber-500/40 bg-amber-500/10 px-3 py-2 text-sm"
          role="status"
          data-testid="ad-schedule-block"
        >
          {scheduleBlockedReason}
        </p>
      ) : null}

      <div className="grid flex-1 gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(280px,360px)]">
        <div className="flex min-w-0 flex-col gap-4">
          <ChannelPicker
            channels={channels}
            loading={channelsLoading}
            selectedIds={draft.selectedChannelIds}
            onChange={(ids) =>
              setDraft((current) =>
                current
                  ? { ...current, selectedChannelIds: ids, updatedAt: new Date().toISOString() }
                  : current,
              )
            }
          />

          <VariantTabs
            networks={selectedNetworks.filter((network) => network !== 'rutube')}
            active={draft.activeVariant}
            overridden={overriddenMap}
            onChange={(value) =>
              setDraft((current) =>
                current
                  ? { ...current, activeVariant: value, updatedAt: new Date().toISOString() }
                  : current,
              )
            }
          />

          {draft.activeVariant === 'base' ? (
            <TipTapEditor
              key={`base-${draft.id}`}
              initialDoc={draft.baseDoc}
              placeholder={t('placeholder')}
              aria-label={t('editorLabel')}
              onChange={updateBase}
            />
          ) : (
            <div className="space-y-2">
              <div className="flex flex-wrap items-center gap-2">
                <p className="text-sm text-muted-foreground">{t('variants.hint')}</p>
                {!editingVariant?.overridden ? (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() =>
                      updateVariantContent(draft.activeVariant as NetworkId, {
                        html: draft.baseHtml,
                        doc: draft.baseDoc ?? EMPTY_DOC,
                      })
                    }
                  >
                    {t('variants.override')}
                  </Button>
                ) : (
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    onClick={() =>
                      setDraft((current) =>
                        current
                          ? {
                              ...current,
                              variants: current.variants.map((item) =>
                                item.network === current.activeVariant
                                  ? { ...item, overridden: false, html: '', doc: null }
                                  : item,
                              ),
                              updatedAt: new Date().toISOString(),
                            }
                          : current,
                      )
                    }
                  >
                    {t('variants.reset')}
                  </Button>
                )}
              </div>
              <TipTapEditor
                key={`${draft.activeVariant}-${editingVariant?.overridden ? 'own' : 'base'}`}
                initialDoc={
                  editingVariant?.overridden
                    ? editingVariant.doc
                    : (draft.baseDoc ?? EMPTY_DOC)
                }
                placeholder={t('placeholder')}
                aria-label={t('editorLabel')}
                editable={Boolean(editingVariant?.overridden)}
                onChange={(payload) =>
                  updateVariantContent(draft.activeVariant as NetworkId, payload)
                }
              />
            </div>
          )}

          {hasVkChannel ? (
            <label
              className="flex flex-col gap-1 text-sm"
              data-testid="publication-kind"
            >
              <span className="font-medium">{t('publicationKind.label')}</span>
              <select
                className="h-10 max-w-xs rounded-md border border-input bg-background px-3 text-sm"
                value={draft.publicationKind}
                aria-label={t('publicationKind.label')}
                aria-describedby="publication-kind-hint"
                onChange={(event) => {
                  const publicationKind = event.target.value as 'post' | 'clip';
                  setDraft((current) => {
                    if (!current) return current;
                    const media =
                      publicationKind === 'clip'
                        ? current.media.map((item) =>
                            item.kind === 'video' || item.kind === 'clip'
                              ? { ...item, kind: 'clip' as const }
                              : item,
                          )
                        : current.media.map((item) =>
                            item.kind === 'clip' ? { ...item, kind: 'video' as const } : item,
                          );
                    return {
                      ...current,
                      publicationKind,
                      media,
                      updatedAt: new Date().toISOString(),
                    };
                  });
                }}
              >
                <option value="post">{t('publicationKind.post')}</option>
                <option value="clip">{t('publicationKind.clip')}</option>
              </select>
              <span id="publication-kind-hint" className="text-xs text-muted-foreground">
                {draft.publicationKind === 'clip'
                  ? t('publicationKind.clipHint')
                  : t('publicationKind.postHint')}
              </span>
            </label>
          ) : null}

          <EditorMediaBlock
            workspaceId={workspaceId}
            items={draft.media}
            selectedNetworks={selectedNetworks}
            publicationKind={draft.publicationKind}
            onChange={(media) =>
              setDraft((current) =>
                current
                  ? { ...current, media, updatedAt: new Date().toISOString() }
                  : current,
              )
            }
          />

          <VkStringAttachmentsField
            visible={hasVkChannel}
            value={draft.stringAttachments}
            onChange={(stringAttachments) =>
              setDraft((current) =>
                current
                  ? { ...current, stringAttachments, updatedAt: new Date().toISOString() }
                  : current,
              )
            }
          />

          <PostOptionsPanel
            options={draft.options}
            networks={selectedNetworks}
            onChange={(options) =>
              setDraft((current) =>
                current
                  ? { ...current, options, updatedAt: new Date().toISOString() }
                  : current,
              )
            }
          />

          <AdMarkingForm
            workspaceId={workspaceId}
            enabled={draft.options.isAd}
            ad={draft.options.ad}
            provider={draft.options.ordProvider}
            hasOrdConnection={hasOrdConnection}
            eridStatus={eridStatus}
            erid={erid}
            registering={registering}
            fieldErrors={adFieldErrors}
            previewBody={stripTags(draft.baseHtml || '') || 'Текст поста https://example.com/offer'}
            onEnabledChange={(isAd) =>
              setDraft((current) =>
                current
                  ? {
                      ...current,
                      options: { ...current.options, isAd },
                      updatedAt: new Date().toISOString(),
                    }
                  : current,
              )
            }
            onAdChange={(ad) => {
              setAdFieldErrors({});
              setDraft((current) =>
                current
                  ? {
                      ...current,
                      options: { ...current.options, ad },
                      updatedAt: new Date().toISOString(),
                    }
                  : current,
              );
            }}
            onProviderChange={(ordProvider: OrdProvider) =>
              setDraft((current) =>
                current
                  ? {
                      ...current,
                      options: { ...current.options, ordProvider },
                      updatedAt: new Date().toISOString(),
                    }
                  : current,
              )
            }
            onRegister={() => void registerErid()}
          />

          <ValidationPanel issues={issues} charCount={charCount} />
        </div>

        <PreviewPanel
          html={activeHtml}
          network={draft.previewNetwork}
          channelName={previewChannel?.name}
          channelExternalId={previewChannel?.externalId}
          media={draft.media}
          adEnabled={draft.options.isAd}
          erid={erid}
          advertiserName={draft.options.ad.advertiserName}
          timezone={draft.options.timezone}
          scheduledAt={draft.options.scheduledAt}
          onNetworkChange={(network) =>
            setDraft((current) =>
              current
                ? { ...current, previewNetwork: network, updatedAt: new Date().toISOString() }
                : current,
            )
          }
        />
      </div>
    </div>
  );
}
