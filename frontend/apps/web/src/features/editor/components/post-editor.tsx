'use client';

import {
  hasBlockingValidationErrors,
  stripTags,
  unicodeLength,
  validateCanonicalHtml,
  type NetworkId,
  type TiptapNode,
} from '@posts/domain';
import { Button } from '@posts/ui';
import { useCallback, useEffect, useMemo, useState, useTransition } from 'react';
import { useTranslations } from 'next-intl';

import {
  useWorkspaceChannels,
  type EditorChannelOption,
} from '@/features/channels/hooks/use-workspace-channels';

import { toTextCapabilities, useNetworkCapabilities } from '../hooks/use-network-capabilities';
import { useDraftAutosave } from '../hooks/use-draft-autosave';
import { loadDraft } from '../model/draft-db';
import { EMPTY_DOC, type EditorDraft, type EditorVariant } from '../model/types';
import { EditorMediaBlock } from '@/features/media/components/editor-media-block';

import { AutosaveIndicator } from './autosave-indicator';
import { ChannelPicker } from './channel-picker';
import { PreviewPanel } from './preview-panel';
import { TipTapEditor } from './tip-tap-editor';
import { ValidationPanel } from './validation-panel';
import { VariantTabs } from './variant-tabs';

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

export function PostEditor({ workspaceId, draftId = 'new' }: PostEditorProps) {
  const t = useTranslations('editor');
  const [draft, setDraft] = useState<EditorDraft | null>(null);
  const [hydrated, setHydrated] = useState(false);
  const [, startTransition] = useTransition();
  const capabilitiesQuery = useNetworkCapabilities();
  const { channels, isLoading: channelsLoading } = useWorkspaceChannels(workspaceId);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      const stored = await loadDraft(workspaceId, draftId);
      if (cancelled) {
        return;
      }
      const draftValue = stored ?? createDraft(workspaceId, draftId);
      setDraft({ ...draftValue, media: draftValue.media ?? [] });
      setHydrated(true);
    })();
    return () => {
      cancelled = true;
    };
  }, [workspaceId, draftId]);

  // Default selection once channels arrive (new drafts start empty).
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

  const previewChannelName = useMemo(() => {
    if (!draft) {
      return undefined;
    }
    const match = channels.find(
      (channel) =>
        draft.selectedChannelIds.includes(channel.id) &&
        (channel.network === draft.previewNetwork ||
          (draft.previewNetwork === 'tg' && channel.network === 'tg')),
    );
    return match?.name;
  }, [draft, channels]);

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

  if (!draft || !hydrated) {
    return <p className="text-sm text-muted-foreground">{t('loading')}</p>;
  }

  const editingVariant =
    draft.activeVariant !== 'base'
      ? draft.variants.find((item) => item.network === draft.activeVariant)
      : undefined;

  return (
    <div className="flex h-full min-h-[70vh] flex-col gap-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{t('title')}</h1>
          <AutosaveIndicator status={status} lastSavedAt={lastSavedAt} />
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" disabled={blocked}>
            {t('actions.saveDraft')}
          </Button>
          <Button type="button" variant="secondary" disabled={blocked}>
            {t('actions.schedule')}
          </Button>
          <Button type="button" disabled={blocked}>
            {t('actions.publish')}
          </Button>
        </div>
      </header>

      <div className="grid flex-1 gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(280px,360px)]">
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

          <EditorMediaBlock
            workspaceId={workspaceId}
            items={draft.media}
            selectedNetworks={selectedNetworks}
            onChange={(media) =>
              setDraft((current) =>
                current
                  ? { ...current, media, updatedAt: new Date().toISOString() }
                  : current,
              )
            }
          />

          <ValidationPanel issues={issues} charCount={charCount} />
        </div>

        <PreviewPanel
          html={activeHtml}
          network={draft.previewNetwork}
          channelName={previewChannelName}
          media={draft.media}
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
