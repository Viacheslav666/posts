'use client';

import type { WorkspaceMember } from '@posts/api-client';
import {
  Button,
  EmptyState,
  RoleBadge,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Skeleton,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  toast,
  type WorkspaceRole,
} from '@posts/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ShieldOff, Users } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';
import { useCan } from '@/shared/hooks/use-can';
import { getErrorMessage } from '@/shared/lib/errors';
import { isWorkspaceRole } from '@/shared/lib/permissions';

import {
  cancelInvitation,
  listAudit,
  listInvitations,
  listMembers,
  removeMember,
  updateMember,
} from '../api/team-api';
import { inviteRoles } from '../model/schemas';
import { InviteMemberForm } from './invite-member-form';

type TeamPageProps = {
  workspaceId: string;
  initialTab?: 'members' | 'invitations';
};

function MemberRow({
  member,
  workspaceId,
  canUpdate,
  canRemove,
}: {
  member: WorkspaceMember;
  workspaceId: string;
  canUpdate: boolean;
  canRemove: boolean;
}) {
  const t = useTranslations('team');
  const tRoles = useTranslations('roles');
  const queryClient = useQueryClient();
  const role = isWorkspaceRole(member.role) ? member.role : 'viewer';

  const updateMutation = useMutation({
    mutationFn: (nextRole: WorkspaceRole) =>
      updateMember(workspaceId, member.userId!, {
        role: nextRole,
        projectIds: member.projectIds,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.workspaces.members(workspaceId),
      });
      toast({ title: t('members.updatedTitle') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('members.errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  const removeMutation = useMutation({
    mutationFn: () => removeMember(workspaceId, member.userId!),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.workspaces.members(workspaceId),
      });
      toast({ title: t('members.removedTitle') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('members.errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  return (
    <li
      className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
      data-testid={`member-row-${member.userId}`}
    >
      <div className="min-w-0 space-y-1">
        <p className="truncate font-medium text-foreground">{member.name ?? member.email}</p>
        <p className="truncate text-sm text-muted-foreground">{member.email}</p>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        {canUpdate && role !== 'owner' ? (
          <Select
            value={role}
            onValueChange={(value) => {
              if (isWorkspaceRole(value) && value !== 'owner') {
                updateMutation.mutate(value);
              }
            }}
          >
            <SelectTrigger className="w-[140px]" aria-label={t('members.roleLabel')}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {inviteRoles.map((r) => (
                <SelectItem key={r} value={r}>
                  {tRoles(r)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : (
          <RoleBadge role={role} label={tRoles(role)} />
        )}
        {canRemove && role !== 'owner' ? (
          <Button
            type="button"
            size="sm"
            variant="destructive"
            onClick={() => removeMutation.mutate()}
            data-testid={`remove-member-${member.userId}`}
          >
            {t('members.remove')}
          </Button>
        ) : null}
      </div>
    </li>
  );
}

export function TeamPage({ workspaceId, initialTab = 'members' }: TeamPageProps) {
  const t = useTranslations('team');
  const tRoles = useTranslations('roles');
  const queryClient = useQueryClient();

  const canViewMembers = useCan('members.view', workspaceId);
  const canInvite = useCan('members.invite', workspaceId);
  const canUpdate = useCan('members.update', workspaceId);
  const canRemove = useCan('members.remove', workspaceId);
  const canAudit = useCan('audit.view', workspaceId);

  const membersQuery = useQuery({
    queryKey: queryKeys.workspaces.members(workspaceId),
    queryFn: () => listMembers(workspaceId),
    enabled: canViewMembers,
  });

  const invitationsQuery = useQuery({
    queryKey: queryKeys.workspaces.invitations(workspaceId),
    queryFn: () => listInvitations(workspaceId),
    enabled: canViewMembers,
  });

  const auditQuery = useQuery({
    queryKey: queryKeys.workspaces.audit(workspaceId),
    queryFn: () => listAudit(workspaceId),
    enabled: canAudit,
  });

  const cancelMutation = useMutation({
    mutationFn: (invitationId: string) => cancelInvitation(invitationId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: queryKeys.workspaces.invitations(workspaceId),
      });
      toast({ title: t('invitations.cancelledTitle') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('invitations.errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  if (!canViewMembers) {
    return (
      <EmptyState
        icon={<ShieldOff aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
      />
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6" data-testid="team-page">
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{t('title')}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t('description')}</p>
      </div>

      <Tabs defaultValue={initialTab === 'invitations' ? 'invitations' : 'members'}>
        <TabsList>
          <TabsTrigger value="members">{t('tabs.members')}</TabsTrigger>
          <TabsTrigger value="invitations">{t('tabs.invitations')}</TabsTrigger>
          {canAudit ? <TabsTrigger value="audit">{t('tabs.audit')}</TabsTrigger> : null}
        </TabsList>

        <TabsContent value="members" className="space-y-6 pt-4">
          {canInvite ? (
            <section className="space-y-3" aria-labelledby="invite-heading">
              <h2 id="invite-heading" className="text-lg font-medium">
                {t('invite.title')}
              </h2>
              <InviteMemberForm workspaceId={workspaceId} />
            </section>
          ) : null}

          {membersQuery.isLoading ? (
            <Skeleton className="h-24 w-full" />
          ) : (
            <ul
              className="divide-y divide-border rounded-md border border-border"
              role="list"
              data-testid="members-list"
            >
              {(membersQuery.data?.items ?? []).map((member) => (
                <MemberRow
                  key={member.userId}
                  member={member}
                  workspaceId={workspaceId}
                  canUpdate={canUpdate}
                  canRemove={canRemove}
                />
              ))}
              {(membersQuery.data?.items ?? []).length === 0 ? (
                <li className="px-4 py-8">
                  <EmptyState
                    icon={<Users aria-hidden />}
                    title={t('members.emptyTitle')}
                    description={t('members.emptyBody')}
                  />
                </li>
              ) : null}
            </ul>
          )}
        </TabsContent>

        <TabsContent value="invitations" className="space-y-4 pt-4">
          {invitationsQuery.isLoading ? (
            <Skeleton className="h-20 w-full" />
          ) : (
            <ul
              className="divide-y divide-border rounded-md border border-border"
              role="list"
              data-testid="invitations-list"
            >
              {(invitationsQuery.data?.items ?? []).map((invitation) => {
                const role = isWorkspaceRole(invitation.role) ? invitation.role : null;
                return (
                  <li
                    key={invitation.id}
                    className="flex flex-wrap items-center justify-between gap-3 px-4 py-3"
                  >
                    <div>
                      <p className="font-medium text-foreground">{invitation.email}</p>
                      {role ? <RoleBadge role={role} label={tRoles(role)} className="mt-1" /> : null}
                    </div>
                    {canInvite ? (
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => cancelMutation.mutate(invitation.id!)}
                        data-testid={`cancel-invite-${invitation.id}`}
                      >
                        {t('invitations.cancel')}
                      </Button>
                    ) : null}
                  </li>
                );
              })}
              {(invitationsQuery.data?.items ?? []).length === 0 ? (
                <li className="px-4 py-8 text-center text-sm text-muted-foreground">
                  {t('invitations.empty')}
                </li>
              ) : null}
            </ul>
          )}
        </TabsContent>

        {canAudit ? (
          <TabsContent value="audit" className="pt-4">
            {auditQuery.isLoading ? (
              <Skeleton className="h-32 w-full" />
            ) : (
              <div className="overflow-x-auto rounded-md border border-border">
                <table className="w-full min-w-[560px] text-left text-sm" data-testid="audit-table">
                  <thead className="border-b border-border bg-muted/50">
                    <tr>
                      <th className="px-3 py-2 font-medium">{t('audit.action')}</th>
                      <th className="px-3 py-2 font-medium">{t('audit.entity')}</th>
                      <th className="px-3 py-2 font-medium">{t('audit.ip')}</th>
                      <th className="px-3 py-2 font-medium">{t('audit.time')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(auditQuery.data?.items ?? []).map((entry) => (
                      <tr key={entry.id} className="border-b border-border last:border-0">
                        <td className="px-3 py-2">{entry.action}</td>
                        <td className="px-3 py-2 text-muted-foreground">{entry.entity}</td>
                        <td className="px-3 py-2 font-mono text-xs text-muted-foreground">
                          {entry.ip ?? '—'}
                        </td>
                        <td className="px-3 py-2 text-muted-foreground">
                          {entry.createdAt
                            ? new Date(entry.createdAt).toLocaleString('ru-RU')
                            : '—'}
                        </td>
                      </tr>
                    ))}
                    {(auditQuery.data?.items ?? []).length === 0 ? (
                      <tr>
                        <td colSpan={4} className="px-3 py-8 text-center text-muted-foreground">
                          {t('audit.empty')}
                        </td>
                      </tr>
                    ) : null}
                  </tbody>
                </table>
              </div>
            )}
          </TabsContent>
        ) : (
          <TabsContent value="audit" className="pt-4">
            <p className="text-sm text-muted-foreground">{t('audit.forbidden')}</p>
          </TabsContent>
        )}
      </Tabs>
    </div>
  );
}
