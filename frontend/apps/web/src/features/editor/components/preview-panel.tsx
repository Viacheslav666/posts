'use client';

import {
  renderFromCanonicalHtml,
  type NetworkId,
  unicodeLength,
  stripTags,
} from '@posts/domain';
import { AdMarkingSlot, Button, PostPreview, type PostPreviewNetwork } from '@posts/ui';
import { useDeferredValue, useMemo } from 'react';
import { useTranslations } from 'next-intl';

import type { EditorMediaItem } from '@/features/media/model/types';

import { sanitizePreviewHtml } from '../lib/sanitize';

type PreviewPanelProps = {
  html: string;
  network: NetworkId;
  onNetworkChange: (network: 'vk' | 'tg' | 'ok' | 'max') => void;
  channelName?: string;
  media?: EditorMediaItem[];
  adEnabled?: boolean;
  erid?: string;
  advertiserName?: string;
};

const PREVIEW_NETWORKS: Array<'vk' | 'tg' | 'ok' | 'max'> = ['vk', 'tg', 'ok', 'max'];

const toPreviewNetwork: Record<'vk' | 'tg' | 'ok' | 'max', PostPreviewNetwork> = {
  vk: 'vk',
  tg: 'telegram',
  ok: 'ok',
  max: 'max',
};

export function PreviewPanel({
  html,
  network,
  onNetworkChange,
  channelName,
  media = [],
  adEnabled = false,
  erid,
  advertiserName,
}: PreviewPanelProps) {
  const t = useTranslations('editor.preview');
  const deferredHtml = useDeferredValue(html);
  const deferredMedia = useDeferredValue(media);

  const rendered = useMemo(() => renderFromCanonicalHtml(deferredHtml), [deferredHtml]);

  const previewNetwork: 'vk' | 'tg' | 'ok' | 'max' =
    network === 'rutube' ? 'vk' : network;

  const content =
    previewNetwork === 'tg'
      ? {
          html: sanitizePreviewHtml(rendered.telegram.html),
          text: rendered.telegram.plain_text,
          length: rendered.telegram.plain_length,
        }
      : previewNetwork === 'max'
        ? {
            html: sanitizePreviewHtml(rendered.max.html),
            text: rendered.max.plain_text,
            length: rendered.max.plain_length,
          }
        : previewNetwork === 'ok'
          ? {
              html: undefined,
              text: rendered.ok.plain,
              length: unicodeLength(rendered.ok.plain),
            }
          : {
              html: undefined,
              text: rendered.vk.plain,
              length: unicodeLength(stripTags(deferredHtml)),
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
