/**
 * Direct-to-object-storage uploader.
 *
 * Small files (CreateUploadResponse.uploadId null): one PUT to `uploadUrl` exactly as
 * signed. Do not append partNumber or uploadId. Send the Content-Type that was signed.
 *
 * Large files: PUT each `parts[].url` unchanged, read ETag, then complete with the real
 * uploadId. Safari only exposes ETag cross-origin when the store (or the /s3/ proxy)
 * sends `Access-Control-Expose-Headers: ETag`.
 */

export const DEFAULT_PART_SIZE = 5 * 1024 * 1024; // 5 MiB (S3 minimum for non-last parts)
export const DEFAULT_CONCURRENCY = 3;
export const DEFAULT_MAX_RETRIES = 3;

export type CompletedPart = {
  partNumber: number;
  etag: string;
};

export type UploadPartUrl = {
  partNumber: number;
  url: string;
};

export type MultipartUploadSession = {
  mediaId: string;
  /** Exact presigned URL for a single PUT. Not rewritten. */
  uploadUrl: string;
  /** Null means a single PUT. Never invent `upload-${mediaId}`. */
  uploadId: string | null;
  partSize: number;
  headers?: Record<string, string>;
  /** Presigned URL per part. Used as-is when uploadId is set. */
  parts?: UploadPartUrl[];
  /** Content-Type that was signed for a single PUT. */
  contentType?: string;
};

export type PutRequest = {
  url: string;
  body: Blob;
  contentType?: string;
  timeoutMs: number;
  signal?: AbortSignal;
  onProgress?: (loaded: number, total: number) => void;
};

export type PutResult = {
  status: number;
  etag: string | null;
};

export type PutImpl = (request: PutRequest) => Promise<PutResult>;

export type MultipartUploaderOptions = {
  file: Blob;
  session: MultipartUploadSession;
  /** Already uploaded parts (resume). */
  completedParts?: CompletedPart[];
  concurrency?: number;
  maxRetries?: number;
  signal?: AbortSignal;
  onProgress?: (progress: MultipartProgress) => void;
  /** Fresh presigned URL when a part URL is missing or rejected. */
  refreshPartUrl?: (partNumber: number) => Promise<string>;
  /** Injected for tests. Defaults to XHR so upload progress is observable. */
  putImpl?: PutImpl;
  /** @deprecated Tests may still inject fetch. Production uses XHR. */
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
  uploadId: string | null;
  etags: string[];
  completedParts: CompletedPart[];
};

export type CreateUploadLike = {
  mediaId?: string;
  uploadUrl?: string;
  uploadId?: string | null;
  headers?: Record<string, string>;
  parts?: Array<{ partNumber?: number; url?: string }>;
};

export class UploadCancelledError extends Error {
  constructor(message = 'Upload cancelled') {
    super(message);
    this.name = 'UploadCancelledError';
  }
}

export class UploadTimeoutError extends Error {
  constructor(message = 'upload_timeout') {
    super(message);
    this.name = 'UploadTimeoutError';
  }
}

export class UploadHttpError extends Error {
  readonly status: number;

  constructor(status: number) {
    super(`Upload failed with HTTP ${status}`);
    this.name = 'UploadHttpError';
    this.status = status;
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

/** At least 60s, about 50 KB/s, capped at 15 minutes per request. */
export function uploadTimeoutMs(bytes: number): number {
  const scaled = Math.ceil(Math.max(0, bytes) / (50 * 1024)) * 1000;
  return Math.min(15 * 60 * 1000, Math.max(60_000, scaled));
}

export function signedContentType(
  headers: Record<string, string> | undefined,
  fallback?: string,
): string | undefined {
  if (headers) {
    for (const [key, value] of Object.entries(headers)) {
      if (key.toLowerCase() === 'content-type' && value.trim()) {
        return value.trim();
      }
    }
  }
  return fallback?.trim() || undefined;
}

/**
 * Top-level `uploadId: null` is a single PUT. Header ids are only a fallback when the
 * field is omitted. Never fabricate an id.
 */
export function resolveUploadId(response: {
  uploadId?: string | null;
  headers?: Record<string, string>;
}): string | null {
  if (response.uploadId === null || response.uploadId === '') {
    return null;
  }
  if (typeof response.uploadId === 'string') {
    return response.uploadId;
  }
  const fromHeaders = parseUploadSessionHeaders(response.headers).uploadId;
  return fromHeaders || null;
}

export function resolveUploadSession(response: CreateUploadLike, contentType: string): MultipartUploadSession {
  const uploadId = resolveUploadId(response);
  const { partSize } = parseUploadSessionHeaders(response.headers);
  const parts = (response.parts ?? [])
    .filter((part): part is { partNumber: number; url: string } =>
      typeof part.partNumber === 'number' && typeof part.url === 'string' && part.url.length > 0,
    )
    .map((part) => ({ partNumber: part.partNumber, url: part.url }));
  return {
    mediaId: response.mediaId ?? '',
    uploadUrl: response.uploadUrl ?? '',
    uploadId,
    partSize,
    headers: response.headers,
    parts,
    contentType: signedContentType(response.headers, contentType),
  };
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

export function planParts(
  fileSize: number,
  partSize: number,
): Array<{ partNumber: number; start: number; end: number }> {
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

function normalizeEtag(raw: string | null): string | null {
  if (!raw) return null;
  const value = raw.trim();
  if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
    return value.slice(1, -1);
  }
  return value;
}

function xhrPut(request: PutRequest): Promise<PutResult> {
  return new Promise((resolve, reject) => {
    if (request.signal?.aborted) {
      reject(new UploadCancelledError());
      return;
    }
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', request.url);
    xhr.timeout = request.timeoutMs;
    if (request.contentType) {
      xhr.setRequestHeader('Content-Type', request.contentType);
    }
    const onAbort = () => xhr.abort();
    request.signal?.addEventListener('abort', onAbort);
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        request.onProgress?.(event.loaded, event.total);
      }
    };
    const cleanup = () => request.signal?.removeEventListener('abort', onAbort);
    xhr.onload = () => {
      cleanup();
      const etag = normalizeEtag(xhr.getResponseHeader('ETag') ?? xhr.getResponseHeader('etag'));
      resolve({ status: xhr.status, etag });
    };
    xhr.onerror = () => {
      cleanup();
      reject(new Error('upload_network'));
    };
    xhr.ontimeout = () => {
      cleanup();
      reject(new UploadTimeoutError());
    };
    xhr.onabort = () => {
      cleanup();
      reject(new UploadCancelledError());
    };
    xhr.send(request.body);
  });
}

function fetchPut(fetchImpl: typeof fetch): PutImpl {
  return async (request) => {
    const headers: Record<string, string> = {};
    if (request.contentType) {
      headers['Content-Type'] = request.contentType;
    }
    const response = await fetchImpl(request.url, {
      method: 'PUT',
      body: request.body,
      headers,
      signal: request.signal,
    });
    request.onProgress?.(request.body.size, request.body.size);
    return {
      status: response.status,
      etag: normalizeEtag(response.headers.get('ETag') ?? response.headers.get('etag')),
    };
  };
}

function partUrl(session: MultipartUploadSession, partNumber: number): string | undefined {
  return session.parts?.find((part) => part.partNumber === partNumber)?.url;
}

async function putWithRetry(args: {
  url: string;
  body: Blob;
  contentType?: string;
  partNumber: number;
  putImpl: PutImpl;
  signal?: AbortSignal;
  maxRetries: number;
  refreshPartUrl?: (partNumber: number) => Promise<string>;
  onProgress?: (loaded: number, total: number) => void;
  requireEtag: boolean;
}): Promise<CompletedPart | null> {
  const { body, partNumber, putImpl, signal, maxRetries, onProgress, requireEtag } = args;
  let url = args.url;
  let refreshed = false;
  let lastError: unknown;

  for (let attempt = 0; attempt <= maxRetries; attempt += 1) {
    if (signal?.aborted) {
      throw new UploadCancelledError();
    }
    try {
      const result = await putImpl({
        url,
        body,
        contentType: args.contentType,
        timeoutMs: uploadTimeoutMs(body.size),
        signal,
        onProgress,
      });
      if (result.status === 403 && !refreshed && args.refreshPartUrl) {
        url = await args.refreshPartUrl(partNumber);
        refreshed = true;
        continue;
      }
      if (result.status < 200 || result.status >= 300) {
        throw new UploadHttpError(result.status);
      }
      if (!requireEtag) {
        return null;
      }
      const etag = result.etag?.trim();
      if (!etag) {
        throw new PartUploadError(
          partNumber,
          'etag_unavailable',
        );
      }
      return { partNumber, etag };
    } catch (error) {
      if (signal?.aborted || error instanceof UploadCancelledError) {
        throw error instanceof UploadCancelledError ? error : new UploadCancelledError();
      }
      lastError = error;
      if (attempt === maxRetries) {
        break;
      }
      await sleep(Math.min(1000 * 2 ** attempt, 8000), signal);
    }
  }

  if (lastError instanceof UploadTimeoutError || lastError instanceof UploadHttpError) {
    throw lastError;
  }
  if (lastError instanceof PartUploadError) {
    throw lastError;
  }
  throw new PartUploadError(
    partNumber,
    lastError instanceof Error ? lastError.message : `Part ${partNumber} failed`,
    lastError,
  );
}

async function uploadSingle(options: MultipartUploaderOptions, putImpl: PutImpl): Promise<MultipartUploadResult> {
  const { file, session, signal, onProgress, maxRetries = DEFAULT_MAX_RETRIES } = options;
  if (signal?.aborted) {
    throw new UploadCancelledError();
  }
  const contentType = session.contentType ?? signedContentType(session.headers);
  await putWithRetry({
    url: session.uploadUrl,
    body: file,
    contentType,
    partNumber: 1,
    putImpl,
    signal,
    maxRetries,
    requireEtag: false,
    onProgress: (loaded, total) => {
      const bytesTotal = file.size || total;
      onProgress?.({
        bytesUploaded: loaded,
        bytesTotal,
        ratio: bytesTotal === 0 ? 1 : loaded / bytesTotal,
        completedParts: [],
      });
    },
  });
  onProgress?.({
    bytesUploaded: file.size,
    bytesTotal: file.size,
    ratio: 1,
    completedParts: [],
  });
  return {
    mediaId: session.mediaId,
    uploadId: null,
    etags: [],
    completedParts: [],
  };
}

/**
 * Single PUT when uploadId is null. Otherwise upload each presigned part.
 * Presigned URLs are not modified.
 */
export async function uploadMultipart(options: MultipartUploaderOptions): Promise<MultipartUploadResult> {
  const putImpl = options.putImpl ?? (options.fetchImpl ? fetchPut(options.fetchImpl) : xhrPut);
  if (!options.session.uploadId) {
    return uploadSingle(options, putImpl);
  }

  const {
    file,
    session,
    completedParts: initialCompleted = [],
    concurrency = DEFAULT_CONCURRENCY,
    maxRetries = DEFAULT_MAX_RETRIES,
    signal,
    onProgress,
    refreshPartUrl,
  } = options;

  if (signal?.aborted) {
    throw new UploadCancelledError();
  }

  const parts = planParts(file.size, session.partSize);
  const completed = new Map<number, CompletedPart>(
    initialCompleted.map((part) => [part.partNumber, part]),
  );
  const inflight = new Map<number, number>();

  const emit = () => {
    const list = [...completed.values()].sort((a, b) => a.partNumber - b.partNumber);
    const completedBytes = list.reduce((sum, part) => {
      const plan = parts.find((item) => item.partNumber === part.partNumber);
      return sum + (plan ? plan.end - plan.start : 0);
    }, 0);
    let inflightBytes = 0;
    for (const [partNumber, loaded] of inflight) {
      if (!completed.has(partNumber)) {
        inflightBytes += loaded;
      }
    }
    const bytesUploaded = Math.min(file.size, completedBytes + inflightBytes);
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
      let url = partUrl(session, part.partNumber);
      if (!url) {
        if (!refreshPartUrl) {
          throw new PartUploadError(part.partNumber, 'missing_part_url');
        }
        url = await refreshPartUrl(part.partNumber);
      }
      const blob = file.slice(part.start, part.end);
      const result = await putWithRetry({
        url,
        body: blob,
        partNumber: part.partNumber,
        putImpl,
        signal,
        maxRetries,
        refreshPartUrl,
        requireEtag: true,
        onProgress: (loaded) => {
          inflight.set(part.partNumber, loaded);
          emit();
        },
      });
      if (result) {
        completed.set(result.partNumber, result);
      }
      inflight.delete(part.partNumber);
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
