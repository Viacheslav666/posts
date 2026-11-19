/**
 * Local mirrors for Suggest DTOs that historically lacked OpenAPI response schemas.
 *
 * As of the FE-22 OpenAPI patch these also exist in `@posts/api-client` schemas
 * (`SuggestBulkJob`, `SuggestSyncResult`, `SuggestSettingsCopyResult`). Prefer the
 * package exports for typed client calls; keep these mirrors for documentation /
 * defensive casting when response typing is incomplete.
 *
 * Matching BE `SuggestDtos` (BE-34).
 */

export type SuggestBulkJobMirror = {
  id: string;
  workspaceId: string;
  settingsId: string;
  channelId: string;
  action: string;
  status: string;
  total: number;
  processed: number;
  succeeded: number;
  failed: number;
  progressPercent: number;
  createdAt: string;
  updatedAt: string;
  finishedAt?: string | null;
};

export type SuggestSyncResultMirror = {
  imported: number;
  removedExternally: number;
  markedPublished: number;
  status: string;
};

export type SuggestSettingsCopyItemMirror = {
  channelId: string;
  ok: boolean;
  error?: string | null;
  settingsId?: string | null;
};

export type SuggestSettingsCopyResultMirror = {
  results: SuggestSettingsCopyItemMirror[];
};
