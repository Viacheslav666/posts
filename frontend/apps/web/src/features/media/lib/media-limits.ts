import type { NetworkId } from '@posts/domain';

import type { EditorMediaItem, MediaKind } from '../model/types';

/**
 * Loose capabilities snapshot for media validation.
 * OpenAPI `MediaLimits.photo/video` are `additionalProperties` stubs (`Record<string, never>`),
 * while the AN-2 fixture carries nested `{ value }` fields — we accept both at runtime.
 */
export type MediaCapabilitiesSnapshot = {
  networks?: Array<{
    id?: NetworkId;
    name?: string;
    media?: {
      maxAttachmentsPerPost?: number;
      photo?: Record<string, unknown>;
      video?: Record<string, unknown>;
    };
  }>;
};

export type MediaValidationIssue = {
  code: string;
  severity: 'error' | 'warning';
  network?: NetworkId;
  message: string;
};

type MediaLimitBag = {
  maxAttachments?: number;
  photoMaxCount?: number;
  photoMaxBytes?: number;
  photoFormats?: string[];
  videoMaxCount?: number;
  videoMaxBytes?: number;
  videoFormats?: string[];
};

function asNumber(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (value && typeof value === 'object' && 'value' in value) {
    const nested = (value as { value?: unknown }).value;
    return typeof nested === 'number' ? nested : undefined;
  }
  return undefined;
}

function asStringArray(value: unknown): string[] | undefined {
  if (Array.isArray(value) && value.every((item) => typeof item === 'string')) {
    return value as string[];
  }
  if (value && typeof value === 'object' && 'value' in value) {
    const nested = (value as { value?: unknown }).value;
    if (Array.isArray(nested) && nested.every((item) => typeof item === 'string')) {
      return nested as string[];
    }
  }
  return undefined;
}

type MediaLimitsInput = NonNullable<
  NonNullable<MediaCapabilitiesSnapshot['networks']>[number]['media']
>;

function readMediaBag(media: MediaLimitsInput | undefined): MediaLimitBag {
  if (!media) {
    return {};
  }
  const photo = media.photo;
  const video = media.video;
  return {
    maxAttachments: media.maxAttachmentsPerPost,
    photoMaxCount: asNumber(photo?.max_count_per_post ?? photo?.maxCountPerPost),
    photoMaxBytes: asNumber(photo?.max_file_bytes ?? photo?.maxFileBytes),
    photoFormats: asStringArray(photo?.formats)?.map((item) => item.toLowerCase()),
    videoMaxCount: asNumber(video?.max_count_per_post ?? video?.maxCountPerPost),
    videoMaxBytes: asNumber(video?.max_file_bytes ?? video?.maxFileBytes),
    videoFormats: asStringArray(video?.formats)?.map((item) => item.toLowerCase()),
  };
}

function extensionOf(filename: string): string {
  const idx = filename.lastIndexOf('.');
  return idx >= 0 ? filename.slice(idx + 1).toLowerCase() : '';
}

function mimeToFormatHint(mime: string): string {
  if (mime === 'image/jpeg') return 'jpg';
  if (mime === 'image/png') return 'png';
  if (mime === 'image/gif') return 'gif';
  if (mime === 'image/webp') return 'webp';
  if (mime === 'video/mp4') return 'mp4';
  if (mime === 'video/quicktime') return 'mov';
  return mime.split('/')[1] ?? mime;
}

/**
 * Validate editor attachments against GET /networks/capabilities media limits.
 * Front-end convenience only — backend remains authoritative.
 */
export function validateMediaAgainstCapabilities(
  items: EditorMediaItem[],
  capabilities: MediaCapabilitiesSnapshot | undefined,
  selectedNetworks: NetworkId[],
): MediaValidationIssue[] {
  const issues: MediaValidationIssue[] = [];
  if (!capabilities?.networks?.length || selectedNetworks.length === 0) {
    return issues;
  }

  const images = items.filter((item) => item.kind === 'image');
  const videos = items.filter((item) => item.kind === 'video');

  for (const networkId of selectedNetworks) {
    const network = capabilities.networks.find((item) => item.id === networkId);
    if (!network) {
      continue;
    }
    const limits = readMediaBag(network.media);

    if (limits.maxAttachments !== undefined && items.length > limits.maxAttachments) {
      issues.push({
        code: 'media.max_attachments',
        severity: 'error',
        network: networkId,
        message: `${network.name ?? networkId}: не более ${limits.maxAttachments} вложений (сейчас ${items.length})`,
      });
    }

    if (limits.photoMaxCount !== undefined && images.length > limits.photoMaxCount) {
      issues.push({
        code: 'media.photo_count',
        severity: 'error',
        network: networkId,
        message: `${network.name ?? networkId}: не более ${limits.photoMaxCount} фото`,
      });
    }

    if (limits.videoMaxCount !== undefined && videos.length > limits.videoMaxCount) {
      issues.push({
        code: 'media.video_count',
        severity: 'error',
        network: networkId,
        message: `${network.name ?? networkId}: не более ${limits.videoMaxCount} видео`,
      });
    }

    for (const item of images) {
      if (limits.photoMaxBytes !== undefined && (item.sizeBytes ?? 0) > limits.photoMaxBytes) {
        issues.push({
          code: 'media.photo_size',
          severity: 'error',
          network: networkId,
          message: `${item.filename}: превышает лимит фото ${network.name ?? networkId}`,
        });
      }
      if (limits.photoFormats?.length) {
        const hint = mimeToFormatHint(item.mime);
        const ext = extensionOf(item.filename);
        const ok = limits.photoFormats.some(
          (format) => format === hint || format === ext || format === 'jpeg' && hint === 'jpg',
        );
        if (!ok) {
          issues.push({
            code: 'media.photo_format',
            severity: 'error',
            network: networkId,
            message: `${item.filename}: формат не поддержан в ${network.name ?? networkId}`,
          });
        }
      }
    }

    for (const item of videos) {
      if (limits.videoMaxBytes !== undefined && (item.sizeBytes ?? 0) > limits.videoMaxBytes) {
        issues.push({
          code: 'media.video_size',
          severity: 'error',
          network: networkId,
          message: `${item.filename}: превышает лимит видео ${network.name ?? networkId}`,
        });
      }
      if (limits.videoFormats?.length) {
        const hint = mimeToFormatHint(item.mime);
        const ext = extensionOf(item.filename);
        const ok = limits.videoFormats.some((format) => format === hint || format === ext);
        if (!ok) {
          issues.push({
            code: 'media.video_format',
            severity: 'error',
            network: networkId,
            message: `${item.filename}: формат видео не поддержан в ${network.name ?? networkId}`,
          });
        }
      }
    }
  }

  return issues;
}

export function countByKind(items: EditorMediaItem[], kind: MediaKind): number {
  return items.filter((item) => item.kind === kind).length;
}
