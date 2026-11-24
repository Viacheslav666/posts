/**
 * iOS Safari often leaves `file.type` empty (camera roll JPEG/HEIC) or reports
 * non-standard types such as `image/jpg` and `image/heic`.
 */

const EXTENSION_MIME: Record<string, string> = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
  gif: 'image/gif',
  heic: 'image/heic',
  heif: 'image/heif',
  mp4: 'video/mp4',
  m4v: 'video/mp4',
  mov: 'video/quicktime',
  pdf: 'application/pdf',
  mp3: 'audio/mpeg',
  m4a: 'audio/mp4',
};

const MIME_ALIASES: Record<string, string> = {
  'image/jpg': 'image/jpeg',
  'image/pjpeg': 'image/jpeg',
  'image/heic-sequence': 'image/heic',
  'image/heif-sequence': 'image/heif',
};

const SUPPORTED = new Set([
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/gif',
  'image/heic',
  'image/heif',
  'video/mp4',
  'video/quicktime',
  'application/pdf',
  'audio/mpeg',
  'audio/mp4',
]);

/** `image/*` so iOS Safari offers HEIC and JPEG from the photo library. */
export const UPLOAD_ACCEPT =
  'image/*,.jpg,.jpeg,.png,.webp,.gif,.heic,.heif,video/mp4,video/quicktime,.mp4,.mov,.m4v,application/pdf,audio/mpeg,audio/mp4,.mp3,.m4a';

export function fileExtension(name: string): string {
  const base = name.split(/[/\\]/).pop() ?? name;
  const dot = base.lastIndexOf('.');
  if (dot <= 0 || dot === base.length - 1) {
    return '';
  }
  return base.slice(dot + 1).toLowerCase();
}

export function resolveUploadMime(file: { name: string; type?: string }): string {
  const raw = (file.type ?? '').split(';')[0]?.trim().toLowerCase() ?? '';
  if (raw && raw !== 'application/octet-stream') {
    return MIME_ALIASES[raw] ?? raw;
  }
  return EXTENSION_MIME[fileExtension(file.name)] ?? (raw || 'application/octet-stream');
}

export function isSupportedUploadMime(mime: string): boolean {
  return SUPPORTED.has(mime);
}
