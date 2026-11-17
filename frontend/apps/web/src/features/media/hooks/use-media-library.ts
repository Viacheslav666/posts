'use client';

import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';

import { queryKeys } from '@/shared/api-client';

import { getMediaQuota, listMedia, listMediaFolders } from '../api/media-api';
import type { MediaKind, MediaStatus } from '../model/types';

export type MediaLibraryFiltersState = {
  kind?: MediaKind;
  status?: MediaStatus;
  q: string;
  folderId?: string | null;
  tag?: string;
};

export function useMediaLibrary(workspaceId: string, enabled = true) {
  const [filters, setFilters] = useState<MediaLibraryFiltersState>({ q: '' });

  const listQuery = useQuery({
    queryKey: queryKeys.media.list(workspaceId, filters),
    queryFn: () =>
      listMedia({
        workspaceId,
        kind: filters.kind,
        status: filters.status,
        q: filters.q || undefined,
        folderId: filters.folderId,
        tag: filters.tag,
        limit: 100,
      }),
    enabled,
    refetchInterval: (query) => {
      const items = query.state.data?.items ?? [];
      const pending = items.some(
        (item) => item.status === 'uploading' || item.status === 'processing',
      );
      return pending ? 2000 : false;
    },
  });

  const quotaQuery = useQuery({
    queryKey: queryKeys.media.quota(workspaceId),
    queryFn: () => getMediaQuota(workspaceId),
    enabled,
  });

  const foldersQuery = useQuery({
    queryKey: queryKeys.media.folders(workspaceId),
    queryFn: () => listMediaFolders(workspaceId),
    enabled,
  });

  const items = useMemo(() => listQuery.data?.items ?? [], [listQuery.data?.items]);

  return {
    filters,
    setFilters,
    items,
    isLoading: listQuery.isLoading,
    isFetching: listQuery.isFetching,
    refetch: listQuery.refetch,
    quota: quotaQuery.data,
    folders: foldersQuery.data ?? [],
    error: listQuery.error,
  };
}
