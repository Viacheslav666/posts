import type { MediaKind } from '../model/types';

export function kindFromMime(mime: string): MediaKind {
  if (mime.startsWith('image/')) return 'image';
  if (mime.startsWith('video/')) return 'video';
  if (mime.startsWith('audio/')) return 'audio';
  return 'doc';
}

export function isSupportedUploadMime(mime: string): boolean {
  const allowed = [
    'image/jpeg',
    'image/png',
    'image/webp',
    'image/gif',
    'video/mp4',
    'video/quicktime',
    'application/pdf',
    'audio/mpeg',
    'audio/mp4',
  ];
  return allowed.includes(mime);
}
