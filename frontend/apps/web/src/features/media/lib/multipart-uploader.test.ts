import { describe, expect, it, vi } from 'vitest';

import {
  DEFAULT_PART_SIZE,
  parseUploadSessionHeaders,
  planParts,
  UploadCancelledError,
  uploadMultipart,
} from './multipart-uploader';

function makeFile(size: number, name = 'clip.bin'): File {
  const buffer = new Uint8Array(size);
  for (let i = 0; i < size; i += 1) {
    buffer[i] = i % 256;
  }
  return new File([buffer], name, { type: 'application/octet-stream' });
}

describe('planParts', () => {
  it('splits into equal parts with a smaller last part', () => {
    const parts = planParts(12_000_000, 5_000_000);
    expect(parts).toHaveLength(3);
    expect(parts[0]).toEqual({ partNumber: 1, start: 0, end: 5_000_000 });
    expect(parts[1]).toEqual({ partNumber: 2, start: 5_000_000, end: 10_000_000 });
    expect(parts[2]).toEqual({ partNumber: 3, start: 10_000_000, end: 12_000_000 });
  });

  it('returns a single part when file fits', () => {
    expect(planParts(100, DEFAULT_PART_SIZE)).toEqual([
      { partNumber: 1, start: 0, end: 100 },
    ]);
  });

  it('returns empty for zero size', () => {
    expect(planParts(0, DEFAULT_PART_SIZE)).toEqual([]);
  });
});

describe('parseUploadSessionHeaders', () => {
  it('reads upload id and part size case-insensitively', () => {
    expect(
      parseUploadSessionHeaders({
        'X-Amz-Upload-Id': 'up-1',
        'X-Posts-Part-Size': '1048576',
      }),
    ).toEqual({ uploadId: 'up-1', partSize: 1_048_576 });
  });
});

describe('uploadMultipart', () => {
  it('uploads all parts in parallel and reports progress', async () => {
    const file = makeFile(3_000_000);
    const putCalls: string[] = [];
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      putCalls.push(url);
      const part = new URL(url, 'http://local').searchParams.get('partNumber') ?? '0';
      return new Response(null, {
        status: 200,
        headers: { ETag: `"etag-${part}"` },
      });
    });

    const progress: number[] = [];
    const result = await uploadMultipart({
      file,
      session: {
        mediaId: 'm1',
        uploadUrl: '/mock-s3/m1/upload',
        uploadId: 'u1',
        partSize: 1_000_000,
      },
      concurrency: 2,
      fetchImpl: fetchImpl as unknown as typeof fetch,
      onProgress: (p) => progress.push(p.ratio),
    });

    expect(result.etags).toEqual(['etag-1', 'etag-2', 'etag-3']);
    expect(result.completedParts).toHaveLength(3);
    expect(putCalls).toHaveLength(3);
    expect(progress.at(-1)).toBe(1);
  });

  it('retries a failed part then succeeds', async () => {
    const file = makeFile(500_000);
    let attempts = 0;
    const fetchImpl = vi.fn(async () => {
      attempts += 1;
      if (attempts === 1) {
        return new Response(null, { status: 500 });
      }
      return new Response(null, { status: 200, headers: { ETag: '"ok"' } });
    });

    const result = await uploadMultipart({
      file,
      session: {
        mediaId: 'm1',
        uploadUrl: '/mock-s3/m1/upload',
        uploadId: 'u1',
        partSize: 1_000_000,
      },
      maxRetries: 2,
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });

    expect(result.etags).toEqual(['ok']);
    expect(attempts).toBe(2);
  });

  it('resumes from completed parts (докачка)', async () => {
    const file = makeFile(2_000_000);
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const part = new URL(String(input), 'http://local').searchParams.get('partNumber');
      return new Response(null, {
        status: 200,
        headers: { ETag: `"etag-${part}"` },
      });
    });

    const result = await uploadMultipart({
      file,
      session: {
        mediaId: 'm1',
        uploadUrl: '/mock-s3/m1/upload',
        uploadId: 'u1',
        partSize: 1_000_000,
      },
      completedParts: [{ partNumber: 1, etag: 'etag-1' }],
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });

    expect(fetchImpl).toHaveBeenCalledTimes(1);
    expect(result.etags).toEqual(['etag-1', 'etag-2']);
  });

  it('cancels in-flight upload', async () => {
    const file = makeFile(2_000_000);
    const controller = new AbortController();
    const fetchImpl = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      await new Promise<void>((resolve, reject) => {
        const timer = setTimeout(resolve, 50);
        init?.signal?.addEventListener(
          'abort',
          () => {
            clearTimeout(timer);
            reject(new DOMException('Aborted', 'AbortError'));
          },
          { once: true },
        );
      });
      return new Response(null, { status: 200, headers: { ETag: '"x"' } });
    });

    const promise = uploadMultipart({
      file,
      session: {
        mediaId: 'm1',
        uploadUrl: '/mock-s3/m1/upload',
        uploadId: 'u1',
        partSize: 1_000_000,
      },
      concurrency: 1,
      signal: controller.signal,
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });

    controller.abort();
    await expect(promise).rejects.toBeInstanceOf(UploadCancelledError);
  });
});
