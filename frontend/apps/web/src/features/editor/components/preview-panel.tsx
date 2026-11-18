'use client';

import {
  renderFromCanonicalHtml,
  stripTags,
  unicodeLength,
  type NetworkId,
} from '@posts/domain';
import { AdMarkingSlot, Button, PostPreview, type PostPreviewNetwork } from '@posts/ui';
import { useDeferredValue, useEffect, useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import type { EditorMediaItem } from '@/features/media/model/types';

import { previewPost } from '../api/posts-api';
import { containsSpintax } from '../lib/spintax';
import { sanitizePreviewHtml } from '../lib/sanitize';
import { renderEditorVariables } from '../lib/variables';

type PreviewPanelProps = {
  html: string;
  network: NetworkId;
  onNetworkChange: (network: 'vk' | 'tg' | 'ok' | 'max') => void;
  channelName?: string;
  channelExternalId?: string;
  media?: EditorMediaItem[];
  adEnabled?: boolean;
  erid?: string;
  advertiserName?: string;
  timezone?: string;
  scheduledAt?: string;
};

const PREVIEW_NETWORKS: Array<'vk' | 'tg' | 'ok' | 'max'> = ['vk', 'tg', 'ok', 'max'];

const toPreviewNetwork: Record<'vk' | 'tg' | 'ok' | 'max', PostPreviewNetwork> = {
  vk: 'vk',
  tg: 'telegram',
  ok: 'ok',
  max: 'max',
};

function newSpintaxSeed(): string {
  return `preview-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export function PreviewPanel({
  html,
  network,
  onNetworkChange,
  channelName,
  channelExternalId,
  media = [],
  adEnabled = false,
  erid,
  advertiserName,
  timezone = 'Europe/Moscow',
  scheduledAt,
}: PreviewPanelProps) {
  const t = useTranslations('editor.preview');
  const deferredHtml = useDeferredValue(html);
  const deferredMedia = useDeferredValue(media);
  const [spintaxSeed, setSpintaxSeed] = useState('preview');
  const [serverExpanded, setServerExpanded] = useState<string | null>(null);
  const [previewSource, setPreviewSource] = useState<'server' | 'client'>('client');

  const plainSource = useMemo(() => stripTags(deferredHtml), [deferredHtml]);
  const hasSpintax = containsSpintax(plainSource);

  const clientExpanded = useMemo(
    () =>
      renderEditorVariables(plainSource, {
        at: scheduledAt,
        timezone,
        groupId: channelExternalId,
        groupName: channelName,
        htmlNetwork: network === 'tg' || network === 'max',
        spintaxSeed,
      }),
    [plainSource, scheduledAt, timezone, channelExternalId, channelName, network, spintaxSeed],
  );

  useEffect(() => {
    let cancelled = false;
    const previewNetwork = network === 'rutube' ? 'vk' : network;
    void (async () => {
      try {
        const response = await previewPost({
          content: { text: deferredHtml },
          network: previewNetwork,
          timezone,
          at: scheduledAt,
          groupId: channelExternalId,
          groupName: channelName,
          spintaxSeed,
        });
        if (cancelled) return;
        setServerExpanded(response.expandedText ?? response.plainText ?? null);
        setPreviewSource('server');
      } catch {
        if (cancelled) return;
        setServerExpanded(null);
        setPreviewSource('client');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [deferredHtml, network, timezone, scheduledAt, channelExternalId, channelName, spintaxSeed]);

  const expandedPlain = serverExpanded ?? clientExpanded;

  const rendered = useMemo(() => {
    // Re-wrap expanded plain into a minimal HTML paragraph for network renderers.
    const htmlForRender =
      deferredHtml.includes('<') && !serverExpanded
        ? deferredHtml.replace(plainSource, expandedPlain)
        : `<p>${escapeHtml(expandedPlain)}</p>`;
    return renderFromCanonicalHtml(htmlForRender);
  }, [deferredHtml, plainSource, expandedPlain, serverExpanded]);

  const previewNetwork: 'vk' | 'tg' | 'ok' | 'max' =
    network === 'rutube' ? 'vk' : network;

  const content =
    previewNetwork === 'tg'
      ? {
          html: sanitizePreviewHtml(rendered.telegram.html),
          text: rendered.telegram.plain_text || expandedPlain,
          length: unicodeLength(rendered.telegram.plain_text || expandedPlain),
        }
      : previewNetwork === 'max'
        ? {
            html: sanitizePreviewHtml(rendered.max.html),
            text: rendered.max.plain_text || expandedPlain,
            length: unicodeLength(rendered.max.plain_text || expandedPlain),
          }
        : previewNetwork === 'ok'
          ? {
              html: undefined,
              text: expandedPlain || rendered.ok.plain,
              length: unicodeLength(expandedPlain || rendered.ok.plain),
            }
          : {
              html: undefined,
              text: expandedPlain || rendered.vk.plain,
              length: unicodeLength(expandedPlain || stripTags(deferredHtml)),
            };

  return (
    <section aria-label={t('label')} className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold">{t('title')}</h2>
        <div role="tablist" aria-label={t('networks')} className="flex flex-wrap gap-1">
          {PREVIEW_NETWORKS.map((item) => (
            <Button
              key={item}
              type="button"
              size="sm"
              variant={previewNetwork === item ? 'secondary' : 'ghost'}
              role="tab"
              aria-selected={previewNetwork === item}
              onClick={() => onNetworkChange(item)}
            >
              {t(`network.${item}`)}
            </Button>
          ))}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
        <span data-testid="preview-timezone">
          {t('timezone', { zone: timezone })}
        </span>
        {channelName ? (
          <span data-testid="preview-channel">{t('channel', { name: channelName })}</span>
        ) : null}
        <span data-testid="preview-source">
          {previewSource === 'server' ? t('sourceServer') : t('sourceClient')}
        </span>
        {hasSpintax ? (
          <Button
            type="button"
            size="sm"
            variant="outline"
            data-testid="preview-rand-reroll"
            onClick={() => setSpintaxSeed(newSpintaxSeed())}
          >
            {t('anotherRand')}
          </Button>
        ) : null}
      </div>

      <AdMarkingSlot
        enabled={adEnabled}
        erid={erid}
        advertiserName={advertiserName}
        className="mb-1"
      />
      <PostPreview
        network={toPreviewNetwork[previewNetwork]}
        contentHtml={
          adEnabled && content.html
            ? `<p><strong>Реклама</strong>${erid ? ` erid: ${erid}` : ''}</p>${content.html}`
            : content.html
        }
        contentText={
          adEnabled
            ? [`Реклама${erid ? ` erid: ${erid}` : ''}`, content.text].filter(Boolean).join('\n')
            : content.text
        }
        channelName={channelName}
        mode="mobile"
        className="w-full max-w-none"
      />
      {deferredMedia.length > 0 ? (
        <div
          className="grid grid-cols-2 gap-2"
          data-testid="preview-media"
          aria-label={t('media')}
        >
          {deferredMedia.slice(0, 4).map((item) =>
            item.previewUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                key={item.id}
                src={item.previewUrl}
                alt={item.filename}
                className="aspect-square w-full rounded-md object-cover"
              />
            ) : (
              <div
                key={item.id}
                className="flex aspect-square items-center justify-center rounded-md bg-muted text-xs"
              >
                {item.filename}
              </div>
            ),
          )}
        </div>
      ) : null}
      <p className="text-xs text-muted-foreground">{t('length', { count: content.length })}</p>
    </section>
  );
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
