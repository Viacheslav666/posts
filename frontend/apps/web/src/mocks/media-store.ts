import type { MediaAsset, MediaPage } from '@posts/api-client';

import type {
  MediaFolder,
  MediaKind,
  MediaLibraryItem,
  MediaQuota,
  MediaStatus,
} from '@/features/media/model/types';

const DEMO_WORKSPACE = '019a0000-0002-7000-8000-000000000002';
const DEMO_USER = '019a0000-0001-7000-8000-000000000001';

const MULTIPART_THRESHOLD_BYTES = 5 * 1024 * 1024;

type UploadSession = {
  mediaId: string;
  uploadId: string | null;
  partSize: number;
  receivedParts: Map<number, string>;
  sizeBytes: number;
  mime: string;
  filename: string;
  kind: MediaKind;
  workspaceId: string;
  sha256?: string;
};

let seq = 1;

function nextId(): string {
  const n = seq++;
  return `019a0000-00m${String(n).padStart(1, '0')}-7000-8000-${String(n).padStart(12, '0')}`;
}

const folders: MediaFolder[] = [
  { id: 'folder-root', name: 'Все файлы', workspaceId: DEMO_WORKSPACE },
  { id: 'folder-campaigns', name: 'Кампании', workspaceId: DEMO_WORKSPACE },
  { id: 'folder-stories', name: 'Истории 9:16', workspaceId: DEMO_WORKSPACE },
];

const assets = new Map<string, MediaLibraryItem>();
const sessions = new Map<string, UploadSession>();

const PLAN_LIMIT_BYTES: Record<string, number> = {
  free: 500 * 1024 * 1024,
  start: 5 * 1024 * 1024 * 1024,
  pro: 50 * 1024 * 1024 * 1024,
  agency: 200 * 1024 * 1024 * 1024,
};

const PLAN_LIMIT_COUNT: Record<string, number> = {
  free: 100,
  start: 2_000,
  pro: 20_000,
  agency: 100_000,
};

function seed() {
  if (assets.size > 0) return;
  const samples: MediaLibraryItem[] = [
    {
      id: '019a0000-00m0-7000-8000-000000000001',
      workspaceId: DEMO_WORKSPACE,
      uploaderId: DEMO_USER,
      kind: 'image',
      mime: 'image/jpeg',
      status: 'ready',
      sizeBytes: 245_760,
      width: 1200,
      height: 1500,
      createdAt: '2026-11-16T10:00:00Z',
      filename: 'cover-autumn.jpg',
      folderId: 'folder-campaigns',
      tags: ['обложка', 'осень'],
      previewUrl:
        'data:image/svg+xml,' +
        encodeURIComponent(
          `<svg xmlns="http://www.w3.org/2000/svg" width="240" height="300"><rect fill="#C4A574" width="100%" height="100%"/><text x="50%" y="50%" fill="#3B2F1E" font-size="18" text-anchor="middle" dominant-baseline="middle">cover</text></svg>`,
        ),
      usedIn: [{ postId: '019a0000-00p0-7000-8000-000000000001', title: 'Осенняя акция' }],
    },
    {
      id: '019a0000-00m0-7000-8000-000000000002',
      workspaceId: DEMO_WORKSPACE,
      uploaderId: DEMO_USER,
      kind: 'video',
      mime: 'video/mp4',
      status: 'ready',
      sizeBytes: 8_388_608,
      width: 1080,
      height: 1920,
      durationMs: 15_000,
      createdAt: '2026-11-15T12:00:00Z',
      filename: 'story-reel.mp4',
      folderId: 'folder-stories',
      tags: ['история', 'вертикаль'],
      previewUrl:
        'data:image/svg+xml,' +
        encodeURIComponent(
          `<svg xmlns="http://www.w3.org/2000/svg" width="180" height="320"><rect fill="#2F4A3A" width="100%" height="100%"/><text x="50%" y="50%" fill="#E8F5EE" font-size="16" text-anchor="middle" dominant-baseline="middle">9:16</text></svg>`,
        ),
      usedIn: [],
    },
    {
      id: '019a0000-00m0-7000-8000-000000000003',
      workspaceId: DEMO_WORKSPACE,
      uploaderId: DEMO_USER,
      kind: 'doc',
      mime: 'application/pdf',
      status: 'ready',
      sizeBytes: 512_000,
      createdAt: '2026-11-14T09:00:00Z',
      filename: 'press-kit.pdf',
      folderId: null,
      tags: ['док'],
      usedIn: [],
    },
  ];
  for (const item of samples) {
    if (item.id) assets.set(item.id, item);
  }
}

seed();

export function resetMediaStoreForTests(): void {
  assets.clear();
  sessions.clear();
  seq = 1;
  seed();
}

export function listFolders(workspaceId: string): MediaFolder[] {
  return folders.filter((folder) => folder.workspaceId === workspaceId);
}

export function listMedia(params: {
  workspaceId: string;
  kind?: MediaKind;
  status?: MediaStatus;
  q?: string;
  folderId?: string | null;
  tag?: string;
  cursor?: string;
  limit?: number;
}): MediaPage & { items: MediaLibraryItem[] } {
  seed();
  let items = [...assets.values()].filter((item) => item.workspaceId === params.workspaceId);

  if (params.kind) {
    items = items.filter((item) => item.kind === params.kind);
  }
  if (params.status) {
    items = items.filter((item) => item.status === params.status);
  }
  if (params.folderId) {
    items = items.filter((item) => item.folderId === params.folderId);
  }
  if (params.tag) {
    const tag = params.tag.toLowerCase();
    items = items.filter((item) => item.tags?.some((value) => value.toLowerCase() === tag));
  }
  if (params.q) {
    const q = params.q.toLowerCase();
    items = items.filter(
      (item) =>
        item.filename?.toLowerCase().includes(q) ||
        item.tags?.some((tag) => tag.toLowerCase().includes(q)) ||
        item.mime?.toLowerCase().includes(q),
    );
  }

  items.sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));

  const limit = params.limit ?? 20;
  let start = 0;
  if (params.cursor) {
    const idx = items.findIndex((item) => item.id === params.cursor);
    start = idx >= 0 ? idx + 1 : 0;
  }
  const page = items.slice(start, start + limit);
  const hasMore = start + limit < items.length;
  return {
    items: page,
    hasMore,
    nextCursor: hasMore ? page[page.length - 1]?.id : undefined,
  };
}

export function getMedia(mediaId: string): MediaLibraryItem | null {
  seed();
  return assets.get(mediaId) ?? null;
}

export function deleteMedia(mediaId: string): boolean {
  return assets.delete(mediaId);
}

export function createUpload(input: {
  workspaceId: string;
  kind: MediaKind;
  mime: string;
  sizeBytes: number;
  filename?: string;
  sha256?: string;
  uploaderId?: string;
}): {
  mediaId: string;
  uploadUrl: string;
  expiresAt: string;
  headers: Record<string, string>;
  uploadId: string | null;
  partSize: number;
  parts: Array<{ partNumber: number; url: string }>;
} {
  const mediaId = nextId();
  const multipart = input.sizeBytes >= MULTIPART_THRESHOLD_BYTES;
  const uploadId = multipart ? `mpu-${mediaId}` : null;
  const partSize = MULTIPART_THRESHOLD_BYTES;
  const filename = input.filename ?? `file-${mediaId}`;
  const partCount = Math.max(1, Math.ceil(input.sizeBytes / partSize));
  const parts = multipart
    ? Array.from({ length: partCount }, (_, index) => {
        const partNumber = index + 1;
        return {
          partNumber,
          url: `/mock-s3/${mediaId}/upload?partNumber=${partNumber}&uploadId=${uploadId}`,
        };
      })
    : [];

  const asset: MediaLibraryItem = {
    id: mediaId,
    workspaceId: input.workspaceId,
    uploaderId: input.uploaderId ?? DEMO_USER,
    kind: input.kind,
    mime: input.mime,
    status: 'uploading',
    sizeBytes: input.sizeBytes,
    sha256: input.sha256,
    createdAt: new Date().toISOString(),
    filename,
    folderId: null,
    tags: [],
    usedIn: [],
  };
  assets.set(mediaId, asset);

  sessions.set(mediaId, {
    mediaId,
    uploadId,
    partSize,
    receivedParts: new Map(),
    sizeBytes: input.sizeBytes,
    mime: input.mime,
    filename,
    kind: input.kind,
    workspaceId: input.workspaceId,
    sha256: input.sha256,
  });

  return {
    mediaId,
    uploadUrl: multipart
      ? parts[0]!.url
      : `/mock-s3/${mediaId}/upload?X-Amz-Signature=mock&X-Amz-Algorithm=AWS4-HMAC-SHA256`,
    expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    uploadId,
    partSize,
    parts,
    headers: multipart
      ? { 'x-posts-part-size': String(partSize) }
      : { 'Content-Type': input.mime },
  };
}

export function receivePart(
  mediaId: string,
  partNumber: number,
  bodySize: number,
): { etag: string } | null {
  const session = sessions.get(mediaId);
  if (!session) return null;
  const etag = `etag-${mediaId}-${partNumber}-${bodySize}`;
  session.receivedParts.set(partNumber, etag);
  return { etag };
}

export function completeUpload(
  mediaId: string,
  body: { uploadId?: string; etags?: string[] },
): MediaLibraryItem | null {
  const asset = assets.get(mediaId);
  const session = sessions.get(mediaId);
  if (!asset || !session) return null;
  if (body.uploadId && body.uploadId !== session.uploadId) {
    return null;
  }

  asset.status = 'processing';
  // Simulate async processing → ready shortly via poll (advance on next get if flagged).
  asset.width = asset.kind === 'image' || asset.kind === 'video' ? 1080 : undefined;
  asset.height = asset.kind === 'video' ? 1920 : asset.kind === 'image' ? 1080 : undefined;
  if (asset.kind === 'video') {
    asset.durationMs = 12_000;
  }
  asset.previewUrl =
    asset.previewUrl ??
    'data:image/svg+xml,' +
      encodeURIComponent(
        `<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect fill="#5B7C6A" width="100%" height="100%"/><text x="50%" y="50%" fill="#fff" font-size="14" text-anchor="middle" dominant-baseline="middle">${asset.filename ?? 'file'}</text></svg>`,
      );

  // Mark ready after a microtask-friendly flag: first GET after complete stays processing once,
  // second GET becomes ready — tests can force via advanceProcessing.
  (asset as MediaLibraryItem & { _processingTicks?: number })._processingTicks = 0;
  sessions.delete(mediaId);
  return asset;
}

export function advanceProcessing(mediaId: string): MediaLibraryItem | null {
  const asset = assets.get(mediaId);
  if (!asset) return null;
  const extended = asset as MediaLibraryItem & { _processingTicks?: number };
  if (asset.status === 'processing') {
    extended._processingTicks = (extended._processingTicks ?? 0) + 1;
    if (extended._processingTicks >= 1) {
      asset.status = 'ready';
    }
  }
  return asset;
}

export function importFromUrl(input: {
  workspaceId: string;
  url: string;
  filename?: string;
  uploaderId?: string;
}): MediaLibraryItem {
  const mediaId = nextId();
  const filename = input.filename ?? input.url.split('/').pop() ?? 'imported.jpg';
  const lower = filename.toLowerCase();
  const kind: MediaKind = lower.match(/\.(mp4|mov|webm)$/)
    ? 'video'
    : lower.match(/\.(pdf|doc|docx)$/)
      ? 'doc'
      : 'image';
  const asset: MediaLibraryItem = {
    id: mediaId,
    workspaceId: input.workspaceId,
    uploaderId: input.uploaderId ?? DEMO_USER,
    kind,
    mime: kind === 'video' ? 'video/mp4' : kind === 'doc' ? 'application/pdf' : 'image/jpeg',
    status: 'ready',
    sizeBytes: 1_024_000,
    width: kind === 'image' || kind === 'video' ? 1080 : undefined,
    height: kind === 'video' ? 1920 : kind === 'image' ? 1350 : undefined,
    createdAt: new Date().toISOString(),
    filename,
    folderId: null,
    tags: ['import'],
    previewUrl:
      'data:image/svg+xml,' +
      encodeURIComponent(
        `<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect fill="#6B8F71" width="100%" height="100%"/><text x="50%" y="50%" fill="#fff" font-size="12" text-anchor="middle" dominant-baseline="middle">import</text></svg>`,
      ),
    usedIn: [],
  };
  assets.set(mediaId, asset);
  return asset;
}

export function getQuota(workspaceId: string, planCode = 'pro'): MediaQuota {
  seed();
  const items = [...assets.values()].filter((item) => item.workspaceId === workspaceId);
  const usedBytes = items.reduce((sum, item) => sum + (item.sizeBytes ?? 0), 0);
  return {
    workspaceId,
    usedBytes,
    limitBytes: PLAN_LIMIT_BYTES[planCode] ?? PLAN_LIMIT_BYTES.pro,
    usedCount: items.length,
    limitCount: PLAN_LIMIT_COUNT[planCode] ?? PLAN_LIMIT_COUNT.pro,
  };
}

export function toMediaAsset(item: MediaLibraryItem): MediaAsset {
  return {
    id: item.id,
    workspaceId: item.workspaceId,
    uploaderId: item.uploaderId,
    kind: item.kind,
    mime: item.mime,
    status: item.status,
    sizeBytes: item.sizeBytes,
    sha256: item.sha256,
    width: item.width,
    height: item.height,
    durationMs: item.durationMs,
    createdAt: item.createdAt,
    previewUrl: item.previewUrl,
    derivatives: {
      filename: { value: item.filename ?? '' } as unknown as Record<string, never>,
    },
  };
}
