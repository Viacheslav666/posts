import type {
  AuditLogEntry,
  Invitation,
  MembershipSummary,
  Project,
  UserProfile,
  Workspace,
  WorkspaceMember,
} from '@posts/api-client';

export const DEMO_USER_ID = '019a0000-0002-7000-8000-000000000001';
export const PERSONAL_WORKSPACE_ID = '019a0000-0002-7000-8000-000000000002';
export const ACME_WORKSPACE_ID = '019a0000-0002-7000-8000-000000000003';
export const DEFAULT_PROJECT_ID = '019a0000-0002-7000-8000-000000000010';
export const SECONDARY_USER_ID = '019a0000-0002-7000-8000-000000000020';
export const PENDING_INVITATION_ID = '019a0000-0002-7000-8000-000000000030';
export const DEMO_INVITE_TOKEN = 'demo-invite-token';

/** Seat limit for MSW conflict simulation (members + pending invites). */
export const MOCK_SEAT_LIMIT = 3;

export type StoredInvitation = Invitation & {
  token: string;
  status: 'pending' | 'accepted' | 'cancelled';
};

type WorkspaceStoreState = {
  workspaces: Workspace[];
  projects: Project[];
  members: WorkspaceMember[];
  invitations: StoredInvitation[];
  audit: AuditLogEntry[];
  memberships: MembershipSummary[];
};

function isoDaysFromNow(days: number): string {
  return new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString();
}

function createInitialState(): WorkspaceStoreState {
  const now = new Date().toISOString();

  const workspaces: Workspace[] = [
    {
      id: PERSONAL_WORKSPACE_ID,
      name: 'Личное пространство',
      ownerUserId: DEMO_USER_ID,
      billingStatus: 'trial',
      planCode: 'pro',
      createdAt: now,
      trialEndsAt: isoDaysFromNow(7),
    },
    {
      id: ACME_WORKSPACE_ID,
      name: 'Acme Brand',
      ownerUserId: DEMO_USER_ID,
      billingStatus: 'active',
      planCode: 'agency',
      createdAt: now,
    },
  ];

  const projects: Project[] = [
    {
      id: DEFAULT_PROJECT_ID,
      workspaceId: PERSONAL_WORKSPACE_ID,
      name: 'Основной проект',
      color: '#3B82F6',
      createdAt: now,
    },
  ];

  const members: WorkspaceMember[] = [
    {
      userId: DEMO_USER_ID,
      email: 'demo@posts.app',
      name: 'Demo User',
      role: 'owner',
      projectIds: [DEFAULT_PROJECT_ID],
      createdAt: now,
    },
    {
      userId: SECONDARY_USER_ID,
      email: 'editor@posts.app',
      name: 'Мария Редактор',
      role: 'editor',
      projectIds: [DEFAULT_PROJECT_ID],
      createdAt: now,
    },
  ];

  // Tag members with workspace via parallel map keyed in helpers
  const invitations: StoredInvitation[] = [
    {
      id: PENDING_INVITATION_ID,
      workspaceId: PERSONAL_WORKSPACE_ID,
      email: 'invitee@posts.app',
      role: 'author',
      projectIds: [DEFAULT_PROJECT_ID],
      createdAt: now,
      expiresAt: isoDaysFromNow(7),
      token: DEMO_INVITE_TOKEN,
      status: 'pending',
    },
  ];

  const audit: AuditLogEntry[] = [
    {
      id: '019a0000-0002-7000-8000-000000000040',
      workspaceId: PERSONAL_WORKSPACE_ID,
      actorId: DEMO_USER_ID,
      action: 'workspace.created',
      entity: 'workspace',
      entityId: PERSONAL_WORKSPACE_ID,
      createdAt: now,
      ip: '203.0.113.10',
    },
    {
      id: '019a0000-0002-7000-8000-000000000041',
      workspaceId: PERSONAL_WORKSPACE_ID,
      actorId: DEMO_USER_ID,
      action: 'member.invited',
      entity: 'invitation',
      entityId: PENDING_INVITATION_ID,
      createdAt: now,
      ip: '203.0.113.10',
    },
    {
      id: '019a0000-0002-7000-8000-000000000042',
      workspaceId: PERSONAL_WORKSPACE_ID,
      actorId: DEMO_USER_ID,
      action: 'member.role_changed',
      entity: 'workspace_member',
      entityId: SECONDARY_USER_ID,
      createdAt: now,
      ip: '203.0.113.10',
      diff: { role: {} },
    },
  ];

  const memberships: MembershipSummary[] = [
    {
      workspaceId: PERSONAL_WORKSPACE_ID,
      workspaceName: 'Личное пространство',
      role: 'owner',
      billingStatus: 'trial',
    },
    {
      workspaceId: ACME_WORKSPACE_ID,
      workspaceName: 'Acme Brand',
      role: 'owner',
      billingStatus: 'active',
    },
  ];

  return { workspaces, projects, members, invitations, audit, memberships };
}

/** Members are stored with workspaceId attached for filtering. */
type MemberRecord = WorkspaceMember & { workspaceId: string };

let state = createInitialState();
let memberRecords: MemberRecord[] = [
  { ...state.members[0], workspaceId: PERSONAL_WORKSPACE_ID },
  { ...state.members[1], workspaceId: PERSONAL_WORKSPACE_ID },
  {
    userId: DEMO_USER_ID,
    email: 'demo@posts.app',
    name: 'Demo User',
    role: 'owner',
    projectIds: [],
    createdAt: state.workspaces[1]?.createdAt,
    workspaceId: ACME_WORKSPACE_ID,
  },
];

function nextId(suffix: string): string {
  return `019a0000-0002-7000-8000-${suffix.padStart(12, '0')}`;
}

let idCounter = 100;

export function resetWorkspaceStore(): void {
  state = createInitialState();
  memberRecords = [
    { ...state.members[0], workspaceId: PERSONAL_WORKSPACE_ID },
    { ...state.members[1], workspaceId: PERSONAL_WORKSPACE_ID },
    {
      userId: DEMO_USER_ID,
      email: 'demo@posts.app',
      name: 'Demo User',
      role: 'owner',
      projectIds: [],
      createdAt: state.workspaces[1]?.createdAt,
      workspaceId: ACME_WORKSPACE_ID,
    },
  ];
  idCounter = 100;
}

export function getDemoProfile(): UserProfile {
  return {
    id: DEMO_USER_ID,
    email: 'demo@posts.app',
    emailVerified: true,
    name: 'Demo User',
    timezone: 'Europe/Moscow',
    locale: 'ru',
    totpEnabled: false,
    memberships: [...state.memberships],
  };
}

export function listWorkspaces(): Workspace[] {
  return [...state.workspaces];
}

export function getWorkspace(id: string): Workspace | undefined {
  return state.workspaces.find((ws) => ws.id === id);
}

export function isKnownWorkspace(id: string): boolean {
  return state.workspaces.some((ws) => ws.id === id);
}

export function isMemberOfWorkspace(workspaceId: string, userId = DEMO_USER_ID): boolean {
  return memberRecords.some((m) => m.workspaceId === workspaceId && m.userId === userId);
}

export function createWorkspace(name: string): Workspace {
  const id = nextId(String(++idCounter));
  const now = new Date().toISOString();
  const workspace: Workspace = {
    id,
    name,
    ownerUserId: DEMO_USER_ID,
    billingStatus: 'trial',
    planCode: 'pro',
    createdAt: now,
    trialEndsAt: isoDaysFromNow(7),
  };
  state.workspaces.push(workspace);
  state.memberships.push({
    workspaceId: id,
    workspaceName: name,
    role: 'owner',
    billingStatus: 'trial',
  });
  memberRecords.push({
    userId: DEMO_USER_ID,
    email: 'demo@posts.app',
    name: 'Demo User',
    role: 'owner',
    projectIds: [],
    createdAt: now,
    workspaceId: id,
  });
  appendAudit(id, 'workspace.created', 'workspace', id);
  return workspace;
}

export function updateWorkspace(id: string, name: string): Workspace | undefined {
  const workspace = getWorkspace(id);
  if (!workspace) {
    return undefined;
  }
  workspace.name = name;
  const membership = state.memberships.find((m) => m.workspaceId === id);
  if (membership) {
    membership.workspaceName = name;
  }
  appendAudit(id, 'workspace.updated', 'workspace', id);
  return workspace;
}

export function deleteWorkspace(id: string): boolean {
  if (id === PERSONAL_WORKSPACE_ID) {
    return false;
  }
  const before = state.workspaces.length;
  state.workspaces = state.workspaces.filter((ws) => ws.id !== id);
  state.projects = state.projects.filter((p) => p.workspaceId !== id);
  state.invitations = state.invitations.filter((i) => i.workspaceId !== id);
  state.audit = state.audit.filter((a) => a.workspaceId !== id);
  state.memberships = state.memberships.filter((m) => m.workspaceId !== id);
  memberRecords = memberRecords.filter((m) => m.workspaceId !== id);
  return state.workspaces.length < before;
}

export function listProjects(workspaceId: string): Project[] {
  return state.projects.filter((p) => p.workspaceId === workspaceId);
}

export function getProject(id: string): Project | undefined {
  return state.projects.find((p) => p.id === id);
}

export function createProject(workspaceId: string, name: string, color?: string): Project {
  const project: Project = {
    id: nextId(String(++idCounter)),
    workspaceId,
    name,
    color: color ?? '#3B82F6',
    createdAt: new Date().toISOString(),
  };
  state.projects.push(project);
  appendAudit(workspaceId, 'project.created', 'project', project.id!);
  return project;
}

export function updateProject(id: string, patch: { name?: string; color?: string }): Project | undefined {
  const project = getProject(id);
  if (!project) {
    return undefined;
  }
  if (patch.name !== undefined) {
    project.name = patch.name;
  }
  if (patch.color !== undefined) {
    project.color = patch.color;
  }
  if (project.workspaceId) {
    appendAudit(project.workspaceId, 'project.updated', 'project', id);
  }
  return project;
}

export function deleteProject(id: string): boolean {
  const project = getProject(id);
  if (!project) {
    return false;
  }
  state.projects = state.projects.filter((p) => p.id !== id);
  if (project.workspaceId) {
    appendAudit(project.workspaceId, 'project.deleted', 'project', id);
  }
  return true;
}

export function setProjectMembers(projectId: string, userIds: string[]): Project | undefined {
  const project = getProject(projectId);
  if (!project?.workspaceId) {
    return undefined;
  }
  for (const member of memberRecords.filter((m) => m.workspaceId === project.workspaceId)) {
    const ids = new Set(member.projectIds ?? []);
    if (userIds.includes(member.userId!)) {
      ids.add(projectId);
    } else {
      ids.delete(projectId);
    }
    member.projectIds = [...ids];
  }
  appendAudit(project.workspaceId, 'project.members_updated', 'project', projectId);
  return project;
}

export function listMembers(workspaceId: string): WorkspaceMember[] {
  return memberRecords
    .filter((m) => m.workspaceId === workspaceId)
    .map(({ workspaceId: _ws, ...member }) => member);
}

export function updateMember(
  workspaceId: string,
  userId: string,
  patch: { role: WorkspaceMember['role']; projectIds?: string[] },
): WorkspaceMember | undefined {
  const member = memberRecords.find((m) => m.workspaceId === workspaceId && m.userId === userId);
  if (!member) {
    return undefined;
  }
  member.role = patch.role;
  if (patch.projectIds) {
    member.projectIds = patch.projectIds;
  }
  appendAudit(workspaceId, 'member.role_changed', 'workspace_member', userId);
  const { workspaceId: _ws, ...rest } = member;
  return rest;
}

export function removeMember(workspaceId: string, userId: string): boolean {
  if (userId === DEMO_USER_ID) {
    return false;
  }
  const before = memberRecords.length;
  memberRecords = memberRecords.filter(
    (m) => !(m.workspaceId === workspaceId && m.userId === userId),
  );
  if (memberRecords.length < before) {
    appendAudit(workspaceId, 'member.removed', 'workspace_member', userId);
    return true;
  }
  return false;
}

export function countSeats(workspaceId: string): number {
  const members = memberRecords.filter((m) => m.workspaceId === workspaceId).length;
  const pending = state.invitations.filter(
    (i) => i.workspaceId === workspaceId && i.status === 'pending',
  ).length;
  return members + pending;
}

export function listInvitations(workspaceId: string): Invitation[] {
  return state.invitations
    .filter((i) => i.workspaceId === workspaceId && i.status === 'pending')
    .map(({ token: _t, status: _s, ...invitation }) => invitation);
}

export function createInvitation(
  workspaceId: string,
  input: { email: string; role: Invitation['role']; projectIds?: string[] },
): StoredInvitation | { error: 'seat_limit' } {
  if (countSeats(workspaceId) >= MOCK_SEAT_LIMIT) {
    return { error: 'seat_limit' };
  }
  const invitation: StoredInvitation = {
    id: nextId(String(++idCounter)),
    workspaceId,
    email: input.email,
    role: input.role,
    projectIds: input.projectIds ?? [],
    createdAt: new Date().toISOString(),
    expiresAt: isoDaysFromNow(7),
    token: `invite-${++idCounter}`,
    status: 'pending',
  };
  state.invitations.push(invitation);
  appendAudit(workspaceId, 'member.invited', 'invitation', invitation.id!);
  return invitation;
}

export function cancelInvitation(invitationId: string): StoredInvitation | undefined {
  const invitation = state.invitations.find((i) => i.id === invitationId);
  if (!invitation || invitation.status !== 'pending') {
    return undefined;
  }
  invitation.status = 'cancelled';
  if (invitation.workspaceId) {
    appendAudit(invitation.workspaceId, 'invitation.cancelled', 'invitation', invitationId);
  }
  return invitation;
}

export function previewInvitation(token: string): Invitation | undefined {
  const invitation = state.invitations.find((i) => i.token === token && i.status === 'pending');
  if (!invitation) {
    return undefined;
  }
  const { token: _t, status: _s, ...rest } = invitation;
  return rest;
}

export function acceptInvitation(token: string): { workspace: Workspace; invitation: Invitation } | { error: 'not_found' | 'seat_limit' } {
  const invitation = state.invitations.find((i) => i.token === token && i.status === 'pending');
  if (!invitation?.workspaceId) {
    return { error: 'not_found' };
  }
  if (countSeats(invitation.workspaceId) >= MOCK_SEAT_LIMIT + 1) {
    // already counted as pending; accepting converts pending → member
  }
  invitation.status = 'accepted';
  invitation.acceptedAt = new Date().toISOString();

  const newUserId = nextId(String(++idCounter));
  memberRecords.push({
    userId: newUserId,
    email: invitation.email,
    name: invitation.email?.split('@')[0] ?? 'Invitee',
    role: invitation.role,
    projectIds: invitation.projectIds ?? [],
    createdAt: new Date().toISOString(),
    workspaceId: invitation.workspaceId,
  });

  const workspace = getWorkspace(invitation.workspaceId)!;
  appendAudit(invitation.workspaceId, 'invitation.accepted', 'invitation', invitation.id!);
  const { token: _t, status: _s, ...rest } = invitation;
  return { workspace, invitation: rest };
}

export function listAudit(workspaceId: string): AuditLogEntry[] {
  return state.audit.filter((a) => a.workspaceId === workspaceId);
}

function appendAudit(
  workspaceId: string,
  action: string,
  entity: string,
  entityId: string,
): void {
  state.audit.unshift({
    id: nextId(String(++idCounter)),
    workspaceId,
    actorId: DEMO_USER_ID,
    action,
    entity,
    entityId,
    createdAt: new Date().toISOString(),
    ip: '203.0.113.10',
  });
}
