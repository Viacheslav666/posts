import { describe, expect, it, vi } from 'vitest';

import {
  DEFAULT_PART_SIZE,
  parseUploadSessionHeaders,
  planParts,
  resolveUploadId,
  resolveUploadSession,
  UploadCancelledError,
  UploadTimeoutError,
  uploadMultipart,
  type PutImpl,
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

const SIGNED = 'https://socialposts.ru/s3/posts-media/key?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=abc';

function partUrls(count: number): Array<{ partNumber: number; url: string }> {
  return Array.from({ length: count }, (_, index) => {
    const partNumber = index + 1;
    return {
      partNumber,
      url: `https://socialposts.ru/s3/posts-media/key?partNumber=${partNumber}&uploadId=mpu-real&X-Amz-Signature=sig${partNumber}`,
    };
  });
}

describe('resolveUploadId', () => {
  it('treats a null upload id as a single PUT even when headers carry an id', () => {
    expect(
      resolveUploadId({
        uploadId: null,
        headers: { 'x-amz-upload-id': 'upload-should-be-ignored' },
      }),
    ).toBeNull();
  });

  it('keeps a real multipart upload id', () => {
    expect(resolveUploadId({ uploadId: 'mpu-real' })).toBe('mpu-real');
  });
});

describe('uploadMultipart', () => {
  it('single PUT uses the presigned URL unchanged and reports XHR progress', async () => {
    const file = makeFile(1200, 'IMG_9203.jpeg');
    const calls: Array<{ url: string; contentType?: string }> = [];
    const putImpl: PutImpl = async (request) => {
      calls.push({ url: request.url, contentType: request.contentType });
      request.onProgress?.(400, 1200);
      request.onProgress?.(1200, 1200);
      return { status: 200, etag: '"ignored"' };
    };
    const progress: number[] = [];
    const session = resolveUploadSession(
      {
        mediaId: '019a0000-0000-7000-8000-000000000099',
        uploadUrl: SIGNED,
        uploadId: null,
        headers: { 'Content-Type': 'image/jpeg', 'x-amz-upload-id': 'upload-nope' },
      },
      'image/jpeg',
    );

    const result = await uploadMultipart({
      file,
      session,
      putImpl,
      onProgress: (p) => progress.push(p.ratio),
    });

    expect(session.uploadId).toBeNull();
    expect(calls).toEqual([{ url: SIGNED, contentType: 'image/jpeg' }]);
    expect(calls[0]?.url).not.toContain('partNumber');
    expect(calls[0]?.url).not.toContain('upload-');
    expect(result.uploadId).toBeNull();
    expect(result.etags).toEqual([]);
    expect(progress).toContain(400 / 1200);
    expect(progress.at(-1)).toBe(1);
  });

  it('uses XMLHttpRequest by default and leaves the presigned URL untouched', async () => {
    const file = makeFile(80, 'IMG_9203.jpeg');
    const opened: string[] = [];
    const headers: Record<string, string> = {};
    class FakeXHR {
      upload = {
        onprogress: null as ((event: ProgressEvent) => void) | null,
      };
      status = 200;
      timeout = 0;
      onload: (() => void) | null = null;
      onerror: (() => void) | null = null;
      ontimeout: (() => void) | null = null;
      onabort: (() => void) | null = null;
      open(_method: string, url: string) {
        opened.push(url);
      }
      setRequestHeader(name: string, value: string) {
        headers[name] = value;
      }
      getResponseHeader() {
        return null;
      }
      abort() {
        this.onabort?.();
      }
      send() {
        this.upload.onprogress?.({ lengthComputable: true, loaded: 80, total: 80 } as ProgressEvent);
        this.onload?.();
      }
    }
    vi.stubGlobal('XMLHttpRequest', FakeXHR);
    const ratios: number[] = [];
    try {
      const result = await uploadMultipart({
        file,
        session: {
          mediaId: 'm1',
          uploadUrl: SIGNED,
          uploadId: null,
          partSize: DEFAULT_PART_SIZE,
          contentType: 'image/jpeg',
        },
        onProgress: (progress) => ratios.push(progress.ratio),
      });
      expect(opened).toEqual([SIGNED]);
      expect(headers['Content-Type']).toBe('image/jpeg');
      expect(result.uploadId).toBeNull();
      expect(ratios).toContain(1);
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it('surfaces HTTP errors and timeouts instead of hanging', async () => {
    const file = makeFile(100);
    const putImpl: PutImpl = async () => ({ status: 400, etag: null });
    await expect(
      uploadMultipart({
        file,
        session: {
          mediaId: 'm1',
          uploadUrl: SIGNED,
          uploadId: null,
          partSize: DEFAULT_PART_SIZE,
          contentType: 'image/jpeg',
        },
        maxRetries: 0,
        putImpl,
      }),
    ).rejects.toThrow(/HTTP 400/);

    const timeoutPut: PutImpl = async () => {
      throw new UploadTimeoutError();
    };
    await expect(
      uploadMultipart({
        file,
        session: {
          mediaId: 'm1',
          uploadUrl: SIGNED,
          uploadId: null,
          partSize: DEFAULT_PART_SIZE,
          contentType: 'image/jpeg',
        },
        maxRetries: 0,
        putImpl: timeoutPut,
      }),
    ).rejects.toBeInstanceOf(UploadTimeoutError);
  });

  it('uploads each presigned part URL unchanged and requires ETag', async () => {
    const file = makeFile(3_000_000);
    const parts = partUrls(3);
    const putCalls: string[] = [];
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      putCalls.push(url);
      const part = new URL(url).searchParams.get('partNumber') ?? '0';
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
        uploadUrl: parts[0]!.url,
        uploadId: 'mpu-real',
        partSize: 1_000_000,
        parts,
      },
      concurrency: 2,
      fetchImpl: fetchImpl as unknown as typeof fetch,
      onProgress: (p) => progress.push(p.ratio),
    });

    expect(result.etags).toEqual(['etag-1', 'etag-2', 'etag-3']);
    expect(result.uploadId).toBe('mpu-real');
    expect(putCalls).toEqual(parts.map((part) => part.url));
    expect(progress.at(-1)).toBe(1);
  });

  it('fails the part when ETag is missing', async () => {
    const file = makeFile(100);
    const fetchImpl = vi.fn(async () => new Response(null, { status: 200 }));
    await expect(
      uploadMultipart({
        file,
        session: {
          mediaId: 'm1',
          uploadUrl: partUrls(1)[0]!.url,
          uploadId: 'mpu-real',
          partSize: 1_000_000,
          parts: partUrls(1),
        },
        maxRetries: 0,
        fetchImpl: fetchImpl as unknown as typeof fetch,
      }),
    ).rejects.toThrow(/etag_unavailable/);
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
        uploadUrl: partUrls(1)[0]!.url,
        uploadId: 'u1',
        partSize: 1_000_000,
        parts: partUrls(1),
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

    const parts = partUrls(2);
    const result = await uploadMultipart({
      file,
      session: {
        mediaId: 'm1',
        uploadUrl: parts[0]!.url,
        uploadId: 'u1',
        partSize: 1_000_000,
        parts,
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

    const parts = partUrls(2);
    const promise = uploadMultipart({
      file,
      session: {
        mediaId: 'm1',
        uploadUrl: parts[0]!.url,
        uploadId: 'u1',
        partSize: 1_000_000,
        parts,
      },
      concurrency: 1,
      signal: controller.signal,
      fetchImpl: fetchImpl as unknown as typeof fetch,
    });

    controller.abort();
    await expect(promise).rejects.toBeInstanceOf(UploadCancelledError);
  });
});
