/**
 * Direct-to-S3 multipart uploader (Uppy/tus-like).
 *
 * Contract (OpenAPI CreateUploadResponse + CompleteUploadRequest):
 * 1. POST /media/uploads → mediaId, uploadUrl, headers (part size + uploadId)
 * 2. PUT each part in parallel to `${uploadUrl}&partNumber=N`
 * 3. POST /media/{id}/complete with { uploadId, etags }
 *
 * Supports cancel, per-part retry, and resume from completedParts.
 */

export const DEFAULT_PART_SIZE = 5 * 1024 * 1024; // 5 MiB (S3 minimum for non-last parts)
export const DEFAULT_CONCURRENCY = 3;
export const DEFAULT_MAX_RETRIES = 3;

export type CompletedPart = {
  partNumber: number;
  etag: string;
};

export type MultipartUploadSession = {
  mediaId: string;
  uploadUrl: string;
  uploadId: string;
  partSize: number;
  headers?: Record<string, string>;
};

export type MultipartUploaderOptions = {
  file: Blob;
  session: MultipartUploadSession;
  /** Already uploaded parts (resume). */
  completedParts?: CompletedPart[];
  concurrency?: number;
  maxRetries?: number;
  signal?: AbortSignal;
  onProgress?: (progress: MultipartProgress) => void;
  /** Injected for tests — defaults to global fetch. */
  fetchImpl?: typeof fetch;
};

export type MultipartProgress = {
  bytesUploaded: number;
  bytesTotal: number;
  ratio: number;
  completedParts: CompletedPart[];
};

export type MultipartUploadResult = {
  mediaId: string;
  uploadId: string;
  etags: string[];
  completedParts: CompletedPart[];
};

export class UploadCancelledError extends Error {
  constructor(message = 'Upload cancelled') {
    super(message);
    this.name = 'UploadCancelledError';
  }
}

export class PartUploadError extends Error {
  readonly partNumber: number;
  readonly causeError?: unknown;

  constructor(partNumber: number, message: string, causeError?: unknown) {
    super(message);
    this.name = 'PartUploadError';
    this.partNumber = partNumber;
    this.causeError = causeError;
  }
}

export function parseUploadSessionHeaders(
  headers: Record<string, string> | undefined,
  fallbackPartSize = DEFAULT_PART_SIZE,
): { uploadId: string; partSize: number } {
  const normalized = Object.fromEntries(
    Object.entries(headers ?? {}).map(([key, value]) => [key.toLowerCase(), value]),
  );
  const uploadId =
    normalized['x-amz-upload-id'] ??
    normalized['x-posts-upload-id'] ??
    normalized['upload-id'] ??
    '';
  const partSizeRaw = normalized['x-posts-part-size'] ?? normalized['x-amz-part-size'];
  const partSize = partSizeRaw ? Number(partSizeRaw) : fallbackPartSize;
  return {
    uploadId,
    partSize: Number.isFinite(partSize) && partSize > 0 ? partSize : fallbackPartSize,
  };
}

export function planParts(fileSize: number, partSize: number): Array<{ partNumber: number; start: number; end: number }> {
  if (fileSize <= 0) {
    return [];
  }
  const parts: Array<{ partNumber: number; start: number; end: number }> = [];
  let start = 0;
  let partNumber = 1;
  while (start < fileSize) {
    const end = Math.min(start + partSize, fileSize);
    parts.push({ partNumber, start, end });
    start = end;
    partNumber += 1;
  }
  return parts;
}

function partUrl(uploadUrl: string, partNumber: number, uploadId: string): string {
  const url = new URL(uploadUrl, 'http://local.invalid');
  url.searchParams.set('partNumber', String(partNumber));
  if (uploadId) {
    url.searchParams.set('uploadId', uploadId);
  }
  // Preserve relative URLs (MSW / same-origin mock-s3).
  if (uploadUrl.startsWith('/') || uploadUrl.startsWith('./')) {
    return `${url.pathname}${url.search}`;
  }
  if (uploadUrl.startsWith('http://local.invalid')) {
    return `${url.pathname}${url.search}`;
  }
  return url.toString();
}

async function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  if (!ms) return;
  await new Promise<void>((resolve, reject) => {
    if (signal?.aborted) {
      reject(new UploadCancelledError());
      return;
    }
    const timer = setTimeout(resolve, ms);
    const onAbort = () => {
      clearTimeout(timer);
      reject(new UploadCancelledError());
    };
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

async function uploadOnePart(args: {
  file: Blob;
  start: number;
  end: number;
  partNumber: number;
  session: MultipartUploadSession;
  fetchImpl: typeof fetch;
  signal?: AbortSignal;
  maxRetries: number;
}): Promise<CompletedPart> {
  const { file, start, end, partNumber, session, fetchImpl, signal, maxRetries } = args;
  const blob = file.slice(start, end);
  const url = partUrl(session.uploadUrl, partNumber, session.uploadId);
  let lastError: unknown;

  for (let attempt = 0; attempt <= maxRetries; attempt += 1) {
    if (signal?.aborted) {
      throw new UploadCancelledError();
    }
    try {
      const response = await fetchImpl(url, {
        method: 'PUT',
        body: blob,
        headers: {
          'Content-Type': 'application/octet-stream',
          ...(session.headers ?? {}),
        },
        signal,
      });
      if (!response.ok) {
        throw new PartUploadError(partNumber, `Part ${partNumber} failed with HTTP ${response.status}`);
      }
      const etag =
        response.headers.get('ETag')?.replaceAll('"', '') ??
        response.headers.get('etag')?.replaceAll('"', '') ??
        `part-${partNumber}`;
      return { partNumber, etag };
    } catch (error) {
      if (signal?.aborted || error instanceof UploadCancelledError) {
        throw error instanceof UploadCancelledError ? error : new UploadCancelledError();
      }
      lastError = error;
      const backoff = Math.min(1000 * 2 ** attempt, 8000);
      await sleep(backoff, signal);
    }
  }

  throw new PartUploadError(
    partNumber,
    `Part ${partNumber} failed after ${maxRetries + 1} attempts`,
    lastError,
  );
}

/**
 * Upload file parts in parallel with progress, cancel, retry, and resume.
 */
export async function uploadMultipart(options: MultipartUploaderOptions): Promise<MultipartUploadResult> {
  const {
    file,
    session,
    completedParts: initialCompleted = [],
    concurrency = DEFAULT_CONCURRENCY,
    maxRetries = DEFAULT_MAX_RETRIES,
    signal,
    onProgress,
    fetchImpl = fetch,
  } = options;

  if (signal?.aborted) {
    throw new UploadCancelledError();
  }

  const parts = planParts(file.size, session.partSize);
  const completed = new Map<number, CompletedPart>(
    initialCompleted.map((part) => [part.partNumber, part]),
  );

  const emit = () => {
    const list = [...completed.values()].sort((a, b) => a.partNumber - b.partNumber);
    const bytesUploaded = list.reduce((sum, part) => {
      const plan = parts.find((item) => item.partNumber === part.partNumber);
      return sum + (plan ? plan.end - plan.start : 0);
    }, 0);
    onProgress?.({
      bytesUploaded,
      bytesTotal: file.size,
      ratio: file.size === 0 ? 1 : bytesUploaded / file.size,
      completedParts: list,
    });
  };

  emit();

  const pending = parts.filter((part) => !completed.has(part.partNumber));
  let cursor = 0;

  async function worker(): Promise<void> {
    while (cursor < pending.length) {
      if (signal?.aborted) {
        throw new UploadCancelledError();
      }
      const index = cursor;
      cursor += 1;
      const part = pending[index];
      if (!part) {
        return;
      }
      const result = await uploadOnePart({
        file,
        start: part.start,
        end: part.end,
        partNumber: part.partNumber,
        session,
        fetchImpl,
        signal,
        maxRetries,
      });
      completed.set(result.partNumber, result);
      emit();
    }
  }

  const workers = Array.from({ length: Math.min(concurrency, Math.max(pending.length, 1)) }, () =>
    worker(),
  );

  try {
    await Promise.all(workers);
  } catch (error) {
    if (signal?.aborted) {
      throw new UploadCancelledError();
    }
    throw error;
  }

  const ordered = [...completed.values()].sort((a, b) => a.partNumber - b.partNumber);
  return {
    mediaId: session.mediaId,
    uploadId: session.uploadId,
    etags: ordered.map((part) => part.etag),
    completedParts: ordered,
  };
}

/** Hex SHA-256 of a Blob (for CreateUploadRequest.sha256). */
export async function sha256Hex(blob: Blob): Promise<string> {
  const buffer = await blob.arrayBuffer();
  const digest = await crypto.subtle.digest('SHA-256', buffer);
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, '0')).join('');
}
