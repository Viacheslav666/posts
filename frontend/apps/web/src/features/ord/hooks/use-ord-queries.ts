'use client';

import type {
  CreateOrdContractorRequest,
  OrdEridCheckRequest,
  OrdProvider,
  OrdReportRunRequest,
  OrdVatCalculateRequest,
  UpdateOrdContractorRequest,
  UpsertOrdConnectionRequest,
} from '@posts/api-client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '@/shared/api-client';

import {
  calculateOrdVat,
  checkOrdErid,
  createOrdContractor,
  deleteOrdInvoice,
  exportOrdReports,
  listOrdConnections,
  listOrdContractors,
  listOrdContracts,
  listOrdKktu,
  listOrdOksm,
  listOrdReports,
  runOrdReport,
  testOrdConnection,
  updateOrdContractor,
  upsertOrdConnection,
} from '../api/ord-api';
import type { OrdExportFormat } from '../model/report-export';
import type { OrdReportApiQuery } from '../model/report-filters';

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

export function useOrdKktu(enabled = true, q = '') {
  return useQuery({
    queryKey: queryKeys.ord.kktu(q),
    queryFn: () => listOrdKktu(q),
    enabled,
  });
}

export function useOrdOksm(enabled = true, q = '') {
  return useQuery({
    queryKey: queryKeys.ord.oksm(q),
    queryFn: () => listOrdOksm(q),
    enabled,
  });
}

export function useCalculateOrdVat() {
  return useMutation({
    mutationFn: (body: OrdVatCalculateRequest) => calculateOrdVat(body),
  });
}

export function useOrdReports(
  workspaceId: string,
  filters: OrdReportApiQuery,
  enabled = true,
) {
  return useQuery({
    queryKey: queryKeys.ord.reports(workspaceId, filters),
    queryFn: () => listOrdReports(workspaceId, filters),
    enabled: Boolean(workspaceId) && enabled,
  });
}

export function useRunOrdReport(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Omit<OrdReportRunRequest, 'workspaceId'> & { workspaceId?: string }) =>
      runOrdReport({ ...body, workspaceId: body.workspaceId ?? workspaceId }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.ord.reports(workspaceId) });
    },
  });
}

export function useCheckOrdErid() {
  return useMutation({
    mutationFn: (body: OrdEridCheckRequest) => checkOrdErid(body),
  });
}

export function useDeleteOrdInvoice(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (invoiceId: string) => deleteOrdInvoice(invoiceId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.ord.reports(workspaceId) });
    },
  });
}

export function useExportOrdReports(workspaceId: string) {
  return useMutation({
    mutationFn: (options: OrdReportApiQuery & { format: OrdExportFormat }) =>
      exportOrdReports(workspaceId, options),
  });
}