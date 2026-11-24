import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { useUploadQueue } from './use-upload-queue';

vi.mock('../api/media-api', () => ({
  createUpload: vi.fn(),
  completeUpload: vi.fn(),
  presignUploadPart: vi.fn(),
}));

import { completeUpload, createUpload } from '../api/media-api';

const SIGNED =
  'https://socialposts.ru/s3/posts-media/workspaces/w/media/m/IMG_9203.jpeg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=abc';

class FakeXHR {
  static instances: FakeXHR[] = [];
  upload = {
    onprogress: null as ((event: ProgressEvent) => void) | null,
  };
  status = 200;
  timeout = 0;
  url = '';
  requestHeaders: Record<string, string> = {};
  etag: string | null = null;
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  ontimeout: (() => void) | null = null;
  onabort: (() => void) | null = null;

  open(_method: string, url: string) {
    this.url = url;
    FakeXHR.instances.push(this);
  }

  setRequestHeader(name: string, value: string) {
    this.requestHeaders[name] = value;
  }

  getResponseHeader(name: string) {
    if (name.toLowerCase() === 'etag') return this.etag;
    return null;
  }

  abort() {
    this.onabort?.();
  }

  send() {
    const part = new URL(this.url).searchParams.get('partNumber');
    if (part) {
      this.etag = `etag-${part}`;
    }
    this.upload.onprogress?.({ lengthComputable: true, loaded: 10, total: 10 } as ProgressEvent);
    this.onload?.();
  }
}

describe('useUploadQueue', () => {
  beforeAll(() => {
    if (typeof Blob !== 'undefined' && typeof Blob.prototype.arrayBuffer !== 'function') {
      Blob.prototype.arrayBuffer = function arrayBuffer(this: Blob) {
        return new Promise((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(reader.result as ArrayBuffer);
          reader.onerror = () => reject(reader.error);
          reader.readAsArrayBuffer(this);
        });
      };
    }
  });

  beforeEach(() => {
    FakeXHR.instances = [];
    vi.stubGlobal('XMLHttpRequest', FakeXHR);
    vi.mocked(createUpload).mockReset();
    vi.mocked(completeUpload).mockReset();
  });

  it('puts a small photo to the exact presigned URL and completes without a multipart id', async () => {
    vi.mocked(createUpload).mockResolvedValue({
      mediaId: '019a0000-0004-7000-8000-000000000099',
      uploadUrl: SIGNED,
      uploadId: null,
      headers: { 'Content-Type': 'image/jpeg', 'x-amz-upload-id': 'upload-should-not-be-used' },
    } as never);
    vi.mocked(completeUpload).mockResolvedValue({ id: '019a0000-0004-7000-8000-000000000099', status: 'processing' });

    const file = new File([new Uint8Array([1, 2, 3, 4])], 'IMG_9203.jpeg', { type: '' });
    const { result } = renderHook(() => useUploadQueue('019a0000-0002-7000-8000-000000000002'));

    act(() => {
      result.current.enqueue([file]);
    });

    await waitFor(() => {
      expect(result.current.items[0]).toMatchObject({ status: 'ready', progress: 1 });
    });

    expect(createUpload).toHaveBeenCalledWith(
      expect.objectContaining({ mime: 'image/jpeg', filename: 'IMG_9203.jpeg' }),
    );
    expect(FakeXHR.instances).toHaveLength(1);
    expect(FakeXHR.instances[0]?.url).toBe(SIGNED);
    expect(FakeXHR.instances[0]?.requestHeaders['Content-Type']).toBe('image/jpeg');
    expect(completeUpload).toHaveBeenCalledWith('019a0000-0004-7000-8000-000000000099', {});
    expect(result.current.items[0]?.progress).toBe(1);
  });

  it('uploads multipart parts to their presigned URLs and completes with the real upload id', async () => {
    const parts = [1, 2].map((partNumber) => ({
      partNumber,
      url: `https://socialposts.ru/s3/posts-media/key?partNumber=${partNumber}&uploadId=mpu-real&X-Amz-Signature=sig${partNumber}`,
    }));
    vi.mocked(createUpload).mockResolvedValue({
      mediaId: '019a0000-0004-7000-8000-000000000088',
      uploadUrl: parts[0]!.url,
      uploadId: 'mpu-real',
      headers: { 'x-posts-part-size': '4' },
      parts,
    } as never);
    vi.mocked(completeUpload).mockResolvedValue({ id: '019a0000-0004-7000-8000-000000000088', status: 'processing' });

    const file = new File([new Uint8Array(6)], 'clip.mp4', { type: 'video/mp4' });
    const { result } = renderHook(() => useUploadQueue('019a0000-0002-7000-8000-000000000002'));
    act(() => {
      result.current.enqueue([file]);
    });

    await waitFor(() => {
      expect(result.current.items[0]).toMatchObject({ status: 'ready' });
    });

    expect(FakeXHR.instances.map((xhr) => xhr.url)).toEqual(parts.map((part) => part.url));
    expect(completeUpload).toHaveBeenCalledWith('019a0000-0004-7000-8000-000000000088', {
      uploadId: 'mpu-real',
      etags: ['etag-1', 'etag-2'],
    });
  });
});
