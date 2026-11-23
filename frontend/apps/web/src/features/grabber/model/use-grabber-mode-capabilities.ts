'use client';

import { useQuery } from '@tanstack/react-query';

import { getGrabberModeMatrix } from '../api/grabber-api';
import { capabilitiesFor, type GrabberModeCaps, type GrabberModeMatrix } from './mode-capabilities';

/** Loads `GET /api/v1/ingest/mode-matrix` and resolves the effective capabilities. */
export function useGrabberModeCapabilities(mode: string, intervalMinutes: number) {
  const query = useQuery({
    queryKey: ['ingest', 'mode-matrix'],
    queryFn: getGrabberModeMatrix,
    staleTime: 60 * 60 * 1000,
  });
  const caps: GrabberModeCaps = capabilitiesFor(mode, intervalMinutes || 0, query.data);
  return {
    matrix: query.data as GrabberModeMatrix | undefined,
    caps,
    isLoading: query.isLoading,
    isError: query.isError,
  };
}
