'use client';

import type {
  CreateOrdContractorRequest,
  OrdProvider,
  UpdateOrdContractorRequest,
  UpsertOrdConnectionRequest,
} from '@posts/api-client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '@/shared/api-client';

import {
  createOrdContractor,
  listOrdConnections,
  listOrdContractors,
  listOrdContracts,
  listOrdKktu,
  listOrdOksm,
  testOrdConnection,
  updateOrdContractor,
  upsertOrdConnection,
} from '../api/ord-api';

export function useOrdConnections(workspaceId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.ord.connections(workspaceId),
    queryFn: () => listOrdConnections(workspaceId),
    enabled,
  });
}

export function useUpsertOrdConnection(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      provider,
      body,
    }: {
      provider: OrdProvider;
      body: UpsertOrdConnectionRequest;
    }) => upsertOrdConnection(workspaceId, provider, body),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.ord.connections(workspaceId),
      });
    },
  });
}

export function useTestOrdConnection(workspaceId: string) {
  return useMutation({
    mutationFn: (provider: OrdProvider) => testOrdConnection(workspaceId, provider),
  });
}

export function useOrdContractors(workspaceId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.ord.contractors(workspaceId),
    queryFn: () => listOrdContractors(workspaceId),
    enabled,
  });
}

export function useCreateOrdContractor(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateOrdContractorRequest) => createOrdContractor(workspaceId, body),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.ord.contractors(workspaceId),
      });
    },
  });
}

export function useUpdateOrdContractor(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      contractorId,
      body,
    }: {
      contractorId: string;
      body: UpdateOrdContractorRequest;
    }) => updateOrdContractor(workspaceId, contractorId, body),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.ord.contractors(workspaceId),
      });
    },
  });
}

export function useOrdContracts(workspaceId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.ord.contracts(workspaceId),
    queryFn: () => listOrdContracts(workspaceId),
    enabled,
  });
}

export function useOrdKktu(enabled = true) {
  return useQuery({
    queryKey: queryKeys.ord.kktu(),
    queryFn: () => listOrdKktu(),
    enabled,
  });
}

export function useOrdOksm(enabled = true) {
  return useQuery({
    queryKey: queryKeys.ord.oksm(),
    queryFn: () => listOrdOksm(),
    enabled,
  });
}