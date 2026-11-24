import { describe, expect, it } from 'vitest';

import { isSupportedUploadMime, resolveUploadMime } from './upload-mime';

describe('resolveUploadMime', () => {
  it('falls back to jpeg when iOS Safari leaves file.type empty', () => {
    expect(resolveUploadMime({ name: 'IMG_9203.jpeg', type: '' })).toBe('image/jpeg');
    expect(resolveUploadMime({ name: 'IMG_9203.JPEG', type: '' })).toBe('image/jpeg');
    expect(isSupportedUploadMime(resolveUploadMime({ name: 'IMG_9203.jpeg', type: '' }))).toBe(true);
  });

  it('normalizes image/jpg and accepts HEIC', () => {
    expect(resolveUploadMime({ name: 'photo.jpg', type: 'image/jpg' })).toBe('image/jpeg');
    expect(resolveUploadMime({ name: 'IMG_1.HEIC', type: '' })).toBe('image/heic');
    expect(resolveUploadMime({ name: 'IMG_1.heic', type: 'image/heic' })).toBe('image/heic');
    expect(isSupportedUploadMime('image/heic')).toBe(true);
    expect(isSupportedUploadMime('image/heif')).toBe(true);
  });

  it('keeps a real video type', () => {
    expect(resolveUploadMime({ name: 'clip.mp4', type: 'video/mp4' })).toBe('video/mp4');
  });
});
