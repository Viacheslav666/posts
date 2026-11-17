import type {
  Channel,
  ChannelCheckResponse,
  ChannelConnectCallbackRequest,
  ChannelConnectConfirmRequest,
  ChannelConnectStartResponse,
  ChannelPage,
  ChannelSet,
  ChannelSetPage,
  CreateChannelSetRequest,
  UpdateChannelRequest,
  UpdateChannelSetRequest,
} from '@posts/api-client';

import { getApiClient, unwrapApi } from '@/shared/api-client';

import type { ConnectableNetwork } from '../model/types';

export type ListChannelsParams = {
  workspaceId: string;
  projectId?: string;
  network?: string;
  status?: string;
  cursor?: string;
  limit?: number;
};

export async function listChannels(params: ListChannelsParams): Promise<ChannelPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/channels', {
      params: {
        query: {
          workspaceId: params.workspaceId,
          projectId: params.projectId,
          network: params.network as ListChannelsParams['network'] &
            ('vk' | 'tg' | 'ok' | 'max' | 'rutube' | undefined),
          status: params.status as ListChannelsParams['status'] &
            ('active' | 'reauth_required' | 'no_permissions' | 'disabled' | undefined),
          cursor: params.cursor,
          limit: params.limit,
        },
      },
    }),
  );
}

export async function startChannelConnect(
  network: ConnectableNetwork,
  workspaceId: string,
  projectId?: string,
): Promise<ChannelConnectStartResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/channels/connect/{network}/start', {
      params: {
        path: { network },
        query: { workspaceId, projectId },
      },
    }),
  );
}

export async function connectChannelCallback(
  network: ConnectableNetwork,
  body: ChannelConnectCallbackRequest,
): Promise<ChannelPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/channels/connect/{network}/callback', {
      params: { path: { network } },
      body,
    }),
  );
}

export async function confirmChannelConnect(
  network: ConnectableNetwork,
  body: ChannelConnectConfirmRequest,
): Promise<ChannelPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/channels/connect/{network}/confirm', {
      params: { path: { network } },
      body,
    }),
  );
}

export async function getChannel(channelId: string): Promise<Channel> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/channels/{channelId}', {
      params: { path: { channelId } },
    }),
  );
}

export async function updateChannel(
  channelId: string,
  body: UpdateChannelRequest,
): Promise<Channel> {
  const client = getApiClient();
  return unwrapApi(
    await client.PUT('/api/v1/channels/{channelId}', {
      params: { path: { channelId } },
      body,
    }),
  );
}

export async function deleteChannel(channelId: string): Promise<void> {
  const client = getApiClient();
  const result = await client.DELETE('/api/v1/channels/{channelId}', {
    params: { path: { channelId } },
  });
  if (!result.response.ok) {
    unwrapApi(result);
  }
}

export async function checkChannel(channelId: string): Promise<ChannelCheckResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/channels/{channelId}/check', {
      params: { path: { channelId } },
    }),
  );
}

export async function listChannelSets(workspaceId: string): Promise<ChannelSetPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/channel-sets', {
      params: { query: { workspaceId } },
    }),
  );
}

export async function createChannelSet(
  workspaceId: string,
  body: CreateChannelSetRequest,
): Promise<ChannelSet> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/channel-sets', {
      params: { query: { workspaceId } },
      body,
    }),
  );
}

export async function updateChannelSet(
  setId: string,
  body: UpdateChannelSetRequest,
): Promise<ChannelSet> {
  const client = getApiClient();
  return unwrapApi(
    await client.PUT('/api/v1/channel-sets/{setId}', {
      params: { path: { setId } },
      body,
    }),
  );
}

export async function deleteChannelSet(setId: string): Promise<void> {
  const client = getApiClient();
  const result = await client.DELETE('/api/v1/channel-sets/{setId}', {
    params: { path: { setId } },
  });
  if (!result.response.ok) {
    unwrapApi(result);
  }
}
