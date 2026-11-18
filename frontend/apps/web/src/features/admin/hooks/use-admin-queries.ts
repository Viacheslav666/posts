'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { queryKeys } from '@/shared/api-client';
import { useMe } from '@/shared/hooks/use-me';

import {
  createAdminAnnouncement,
  createAdminPromo,
  deleteAdminQueueMessage,
  exitImpersonation,
  getAdminUser,
  getAdminWorkspace,
  impersonateUser,
  listAdminAnnouncements,
  listAdminAudit,
  listAdminFeatureFlags,
  listAdminPlans,
  listAdminPromos,
  listAdminQueueMessages,
  listAdminQueues,
  retryAdminQueueMessage,
  searchAdminUsers,
  searchAdminWorkspaces,
  upsertAdminFeatureFlag,
  upsertAdminPlan,
  adjustWorkspaceBalance,
  adjustWorkspaceSubscription,
  updateAdminAnnouncement,
  updateAdminPromo,
} from '../api/admin-api';

export function useIsPlatformAdmin() {
  const me = useMe();
  return {
    ...me,
    isPlatformAdmin: Boolean(me.data?.platformAdmin),
    isImpersonating: Boolean(me.data?.impersonation?.active),
  };
}

export function useAdminUsers(
  filters: { q?: string; platformAdmin?: boolean; cursor?: string },
  enabled = true,
) {
  return useQuery({
    queryKey: queryKeys.admin.users(filters),
    queryFn: () => searchAdminUsers({ ...filters, limit: 20 }),
    enabled,
  });
}

export function useAdminUser(userId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.user(userId),
    queryFn: () => getAdminUser(userId),
    enabled: enabled && Boolean(userId),
  });
}

export function useAdminWorkspaces(
  filters: { q?: string; planCode?: string; billingStatus?: string; cursor?: string },
  enabled = true,
) {
  return useQuery({
    queryKey: queryKeys.admin.workspaces(filters),
    queryFn: () => searchAdminWorkspaces({ ...filters, limit: 20 }),
    enabled,
  });
}

export function useAdminWorkspace(workspaceId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.workspace(workspaceId),
    queryFn: () => getAdminWorkspace(workspaceId),
    enabled: enabled && Boolean(workspaceId),
  });
}

export function useAdminPlans(enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.plans(),
    queryFn: listAdminPlans,
    enabled,
  });
}

export function useAdminPromos(enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.promos(),
    queryFn: () => listAdminPromos({ limit: 50 }),
    enabled,
  });
}

export function useAdminQueues(enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.queues(),
    queryFn: listAdminQueues,
    enabled,
  });
}

export function useAdminQueueMessages(queue: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.queueMessages(queue),
    queryFn: () => listAdminQueueMessages(queue, { limit: 50 }),
    enabled: enabled && Boolean(queue),
  });
}

export function useAdminFeatureFlags(enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.featureFlags(),
    queryFn: listAdminFeatureFlags,
    enabled,
  });
}

export function useAdminAnnouncements(enabled = true) {
  return useQuery({
    queryKey: queryKeys.admin.announcements(),
    queryFn: () => listAdminAnnouncements({ limit: 50 }),
    enabled,
  });
}

export function useAdminAudit(
  filters: { action?: string; actorUserId?: string; targetType?: string } = {},
  enabled = true,
) {
  return useQuery({
    queryKey: queryKeys.admin.audit(filters),
    queryFn: () => listAdminAudit({ ...filters, limit: 50 }),
    enabled,
  });
}

export function useImpersonateMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, reason }: { userId: string; reason: string }) =>
      impersonateUser(userId, { reason }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.me.all() });
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.all() });
    },
  });
}

export function useExitImpersonationMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: exitImpersonation,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.root });
    },
  });
}

export function useRetryQueueMessageMutation(queue: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (messageId: string) => retryAdminQueueMessage(queue, messageId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.queues() });
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.queueMessages(queue) });
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.audit() });
    },
  });
}

export function useDeleteQueueMessageMutation(queue: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (messageId: string) => deleteAdminQueueMessage(queue, messageId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.queues() });
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.queueMessages(queue) });
    },
  });
}

export function useCreatePromoMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createAdminPromo,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.promos() });
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.audit() });
    },
  });
}

export function useUpdatePromoMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ promoId, body }: { promoId: string; body: Parameters<typeof updateAdminPromo>[1] }) =>
      updateAdminPromo(promoId, body),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.promos() });
    },
  });
}

export function useUpsertFeatureFlagMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: upsertAdminFeatureFlag,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.featureFlags() });
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.audit() });
    },
  });
}

export function useCreateAnnouncementMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createAdminAnnouncement,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.announcements() });
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.audit() });
    },
  });
}

export function useUpdateAnnouncementMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      announcementId,
      body,
    }: {
      announcementId: string;
      body: Parameters<typeof updateAdminAnnouncement>[1];
    }) => updateAdminAnnouncement(announcementId, body),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.announcements() });
    },
  });
}

export function useUpsertPlanMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: upsertAdminPlan,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.plans() });
      await queryClient.invalidateQueries({ queryKey: queryKeys.billing.plans() });
    },
  });
}

export function useAdjustSubscriptionMutation(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adjustWorkspaceSubscription.bind(null, workspaceId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.workspace(workspaceId) });
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.workspaces() });
    },
  });
}

export function useAdjustBalanceMutation(workspaceId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: adjustWorkspaceBalance.bind(null, workspaceId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.admin.workspace(workspaceId) });
    },
  });
}
