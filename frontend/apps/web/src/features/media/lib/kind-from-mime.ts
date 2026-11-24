import type { MediaKind } from '../model/types';

export function kindFromMime(mime: string): MediaKind {
  if (mime.startsWith('image/')) return 'image';
  if (mime.startsWith('video/')) return 'video';
  if (mime.startsWith('audio/')) return 'audio';
  return 'doc';
}

export { isSupportedUploadMime } from './upload-mime';
