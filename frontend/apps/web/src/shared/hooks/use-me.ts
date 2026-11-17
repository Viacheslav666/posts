'use client';

import { useQuery } from '@tanstack/react-query';

import { fetchMe } from '@/features/auth/api/auth-api';
import { queryKeys } from '@/shared/api-client';

export function useMe() {
  return useQuery({
    queryKey: queryKeys.me.profile(),
    queryFn: fetchMe,
  });
}
