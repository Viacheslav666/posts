'use client';

import { useMemo } from 'react';

import { useAutomationFeatureFlags } from '@/features/automations-common/hooks/use-feature-flag';
import { useCan } from '@/shared/hooks/use-can';
import type { PermissionAction } from '@/shared/lib/permissions';

import type { NavAccess } from './nav-model';

export function useNavAccess(workspaceId: string): NavAccess {
  const flags = useAutomationFeatureFlags();
  const workspaceView = useCan('workspace.view', workspaceId);
  const workspaceUpdate = useCan('workspace.update', workspaceId);
  const membersView = useCan('members.view', workspaceId);
  const membersInvite = useCan('members.invite', workspaceId);
  const membersUpdate = useCan('members.update', workspaceId);
  const projectsManage = useCan('projects.manage', workspaceId);
  const projectsView = useCan('projects.view', workspaceId);
  const billingView = useCan('billing.view', workspaceId);
  const channelsView = useCan('channels.view', workspaceId);
  const channelsManage = useCan('channels.manage', workspaceId);
  const mediaView = useCan('media.view', workspaceId);
  const mediaManage = useCan('media.manage', workspaceId);
  const postsView = useCan('posts.view', workspaceId);
  const postsCreate = useCan('posts.create', workspaceId);
  const postsSchedule = useCan('posts.schedule', workspaceId);
  const calendarView = useCan('calendar.view', workspaceId);
  const analyticsView = useCan('analytics.view', workspaceId);
  const ordView = useCan('ord.view', workspaceId);
  const ordReports = useCan('ord.reports.run', workspaceId);
  const automationsView = useCan('automations.view', workspaceId);
  const automationsManage = useCan('automations.manage', workspaceId);
  const notifications = useCan('notifications.manage_self', workspaceId);

  return useMemo(() => {
    const granted: Partial<Record<PermissionAction, boolean>> = {
      'workspace.view': workspaceView,
      'workspace.update': workspaceUpdate,
      'members.view': membersView,
      'members.invite': membersInvite,
      'members.update': membersUpdate,
      'projects.manage': projectsManage,
      'projects.view': projectsView,
      'billing.view': billingView,
      'channels.view': channelsView,
      'channels.manage': channelsManage,
      'media.view': mediaView,
      'media.manage': mediaManage,
      'posts.view': postsView,
      'posts.create': postsCreate,
      'posts.schedule': postsSchedule,
      'calendar.view': calendarView,
      'analytics.view': analyticsView,
      'ord.view': ordView,
      'ord.reports.run': ordReports,
      'automations.view': automationsView,
      'automations.manage': automationsManage,
      'notifications.manage_self': notifications,
    };
    return {
      can: (action: PermissionAction) => granted[action] ?? false,
      flags: {
        suggest: flags.suggest,
        antispam: flags.antispam,
        crosspost: flags.crosspost,
      },
    };
  }, [
    analyticsView,
    automationsManage,
    automationsView,
    billingView,
    calendarView,
    channelsManage,
    channelsView,
    flags.antispam,
    flags.crosspost,
    flags.suggest,
    mediaManage,
    mediaView,
    membersInvite,
    membersUpdate,
    membersView,
    notifications,
    ordReports,
    ordView,
    postsCreate,
    postsSchedule,
    postsView,
    projectsManage,
    projectsView,
    workspaceUpdate,
    workspaceView,
  ]);
}
