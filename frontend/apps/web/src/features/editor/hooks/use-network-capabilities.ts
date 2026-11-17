'use client';

import type { NetworkTextCapability } from '@posts/domain';
import { useQuery } from '@tanstack/react-query';

import { getApiClient, queryKeys, unwrapApi } from '@/shared/api-client';

export function useNetworkCapabilities() {
  return useQuery({
    queryKey: queryKeys.networks.capabilities(),
    queryFn: async () => {
      const result = await getApiClient().GET('/api/v1/networks/capabilities');
      return unwrapApi(result);
    },
    staleTime: 60 * 60 * 1000,
  });
}

export function toTextCapabilities(
  response: Awaited<ReturnType<typeof useNetworkCapabilities>['data']>,
): NetworkTextCapability[] {
  return (response?.networks ?? []).flatMap((network) => {
    if (!network.id) {
      return [];
    }
    return [
      {
        id: network.id,
        maxChars: network.text?.maxChars,
        captionMaxChars: network.text?.captionMaxChars,
        formattingMode: network.formatting?.mode,
      },
    ];
  });
}
