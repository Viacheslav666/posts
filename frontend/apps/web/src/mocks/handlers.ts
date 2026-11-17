import type {
  CreateInvitationRequest,
  CreateProjectRequest,
  CreateWorkspaceRequest,
  EmailVerifyRequest,
  LoginRequest,
  OAuthStartResponse,
  PasswordForgotRequest,
  PasswordResetRequest,
  ProblemDetail,
  ProjectMembersRequest,
  RegisterRequest,
  TokenResponse,
  UpdateMemberRequest,
  UpdateProjectRequest,
  UpdateWorkspaceRequest,
} from '@posts/api-client';
import { http, HttpResponse } from 'msw';

import { MOCK_NETWORK_CAPABILITIES } from '@/features/editor/api/capabilities-mock';
import {
  INSUFFICIENT_PERMISSIONS_CODE,
  TWO_FACTOR_REQUIRED_CODE,
} from '@/shared/lib/errors';
import {
  can,
  isWorkspaceRole,
  type PermissionAction,
  type WorkspaceRole,
} from '@/shared/lib/permissions';

import {
  acceptInvitation,
  cancelInvitation,
  createInvitation,
  createProject,
  createWorkspace,
  deleteProject,
  deleteWorkspace,
  getDemoProfile,
  getProject,
  getWorkspace,
  isKnownWorkspace,
  isMemberOfWorkspace,
  listAudit,
  listInvitations,
  listMembers,
  listProjects,
  listWorkspaces,
  previewInvitation,
  removeMember,
  updateMember,
  updateProject,
  updateWorkspace,
  setProjectMembers,
} from './workspace-store';

function problem(status: number, code: string, detail: string, title?: string, instance = '/api/v1') {
  const body: ProblemDetail = {
    type: `https://posts.ru/problems/${code}`,
    title: title ?? code,
    status,
    detail,
    code,
    instance,
  };
  return HttpResponse.json(body, {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

function tokenResponse(overrides?: Partial<TokenResponse>): TokenResponse {
  return {
    accessToken: 'msw.access.token',
    tokenType: 'Bearer',
    expiresIn: 900,
    ...overrides,
  };
}

function refreshCookieHeader(): string {
  return 'refresh_token=msw.refresh.token; Path=/api/v1/auth; HttpOnly; SameSite=Lax';
}

function requireBearer(request: Request) {
  const auth = request.headers.get('Authorization');
  if (!auth?.startsWith('Bearer ')) {
    return problem(401, 'unauthorized', 'Missing or invalid access token', 'Unauthorized');
  }
  return null;
}

/**
 * Derive mock actor role from access token string.
 * `msw.access.token` → owner; `msw.access.editor` → editor; etc.
 */
export function roleFromAccessToken(request: Request): WorkspaceRole {
  const auth = request.headers.get('Authorization') ?? '';
  const token = auth.replace(/^Bearer\s+/i, '');
  if (token.includes('viewer')) return 'viewer';
  if (token.includes('author')) return 'author';
  if (token.includes('editor')) return 'editor';
  if (token.includes('admin')) return 'admin';
  return 'owner';
}

function requirePermission(request: Request, action: PermissionAction) {
  const unauthorized = requireBearer(request);
  if (unauthorized) {
    return unauthorized;
  }
  const role = roleFromAccessToken(request);
  if (!can(role, action)) {
    return problem(
      403,
      INSUFFICIENT_PERMISSIONS_CODE,
      `Role ${role} cannot perform ${action}`,
      'Forbidden',
    );
  }
  return null;
}

function requireWorkspaceAccess(request: Request, workspaceId: string) {
  const unauthorized = requireBearer(request);
  if (unauthorized) {
    return unauthorized;
  }
  if (!isKnownWorkspace(workspaceId) || !isMemberOfWorkspace(workspaceId)) {
    return problem(404, 'not_found', 'Workspace not found', 'Not found');
  }
  return null;
}

/**
 * MSW handlers typed against `docs/api/openapi.yaml` schemas.
 * Auth + workspace/team behaviour mocked for FE until BE-5/BE-6.
 */
export const handlers = [
  http.get('/api/v1/ping', () => HttpResponse.json({ message: 'pong' })),

  http.get('/api/v1/me', ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) {
      return unauthorized;
    }
    return HttpResponse.json(getDemoProfile());
  }),

  http.post('/api/v1/auth/register', async ({ request }) => {
    const body = (await request.json()) as RegisterRequest;
    if (!body.email || !body.password || !body.name) {
      return problem(400, 'validation_failed', 'name, email and password are required', 'Validation failed');
    }
    if (body.password.length < 10) {
      return problem(400, 'validation_failed', 'password must be at least 10 characters', 'Validation failed');
    }
    if (body.email.toLowerCase() === 'taken@posts.app') {
      return problem(409, 'conflict', 'Email already registered', 'Conflict');
    }
    return HttpResponse.json({ message: 'verification_email_sent' }, { status: 201 });
  }),

  http.post('/api/v1/auth/login', async ({ request }) => {
    const body = (await request.json()) as LoginRequest;
    if (!body.email || !body.password) {
      return problem(400, 'validation_failed', 'email and password are required', 'Validation failed');
    }
    if (body.password === 'wrong-password') {
      return problem(401, 'unauthorized', 'Invalid email or password', 'Unauthorized');
    }

    const email = body.email.toLowerCase();
    if (email === 'twofa@posts.app') {
      if (!body.totpCode) {
        return problem(
          401,
          TWO_FACTOR_REQUIRED_CODE,
          'TOTP code required',
          'Two-factor required',
        );
      }
      if (body.totpCode !== '654321') {
        return problem(401, 'unauthorized', 'Two-factor code is incorrect', 'Unauthorized');
      }
    }

    return HttpResponse.json(tokenResponse(), {
      headers: { 'Set-Cookie': refreshCookieHeader() },
    });
  }),

  http.post('/api/v1/auth/refresh', () =>
    HttpResponse.json(tokenResponse({ accessToken: 'msw.access.refreshed' }), {
      headers: { 'Set-Cookie': refreshCookieHeader() },
    }),
  ),

  http.post('/api/v1/auth/logout', () =>
    HttpResponse.json(
      { message: 'ok' },
      {
        headers: {
          'Set-Cookie': 'refresh_token=; Path=/api/v1/auth; Max-Age=0; HttpOnly; SameSite=Lax',
        },
      },
    ),
  ),

  http.post('/api/v1/auth/email/verify', async ({ request }) => {
    const body = (await request.json()) as EmailVerifyRequest;
    if (!body.token) {
      return problem(400, 'validation_failed', 'token is required', 'Validation failed');
    }
    if (body.token !== '123456' && body.token !== 'demo-verify-token') {
      return problem(400, 'validation_failed', 'Verification token is incorrect or expired', 'Invalid token');
    }
    return HttpResponse.json({ message: 'Email verified' });
  }),

  http.post('/api/v1/auth/password/forgot', async ({ request }) => {
    const body = (await request.json()) as PasswordForgotRequest;
    if (!body.email) {
      return problem(400, 'validation_failed', 'email is required', 'Validation failed');
    }
    return HttpResponse.json({
      message: 'If the account exists, a reset link was sent',
    });
  }),

  http.post('/api/v1/auth/password/reset', async ({ request }) => {
    const body = (await request.json()) as PasswordResetRequest;
    if (!body.token || !body.newPassword) {
      return problem(400, 'validation_failed', 'token and newPassword are required', 'Validation failed');
    }
    if (body.newPassword.length < 10) {
      return problem(400, 'validation_failed', 'password must be at least 10 characters', 'Validation failed');
    }
    if (body.token === 'expired') {
      return problem(410, 'not_found', 'Reset token is invalid or expired', 'Gone');
    }
    return HttpResponse.json({ message: 'Password updated' });
  }),

  http.post('/api/v1/auth/oauth/:provider/start', ({ params }) => {
    const provider = String(params.provider);
    if (provider !== 'vk' && provider !== 'yandex' && provider !== 'telegram') {
      return problem(400, 'validation_failed', `Unknown OAuth provider: ${provider}`, 'Validation failed');
    }
    const payload: OAuthStartResponse = {
      authorizationUrl: `https://oauth.example/${provider}/authorize?client_id=posts-mock`,
      state: `msw-state-${provider}`,
    };
    return HttpResponse.json(payload);
  }),

  // --- Networks (FE-4 editor) ---
  http.get('/api/v1/networks/capabilities', () => HttpResponse.json(MOCK_NETWORK_CAPABILITIES)),

  // --- Workspaces ---
  http.get('/api/v1/workspaces', ({ request }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    return HttpResponse.json({ items: listWorkspaces(), hasMore: false });
  }),

  http.post('/api/v1/workspaces', async ({ request }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const body = (await request.json()) as CreateWorkspaceRequest;
    if (!body.name?.trim()) {
      return problem(400, 'validation_failed', 'name is required', 'Validation failed');
    }
    const workspace = createWorkspace(body.name.trim());
    return HttpResponse.json(workspace, { status: 201 });
  }),

  http.get('/api/v1/workspaces/:workspaceId', ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'workspace.view');
    if (deniedView) return deniedView;
    return HttpResponse.json(getWorkspace(workspaceId));
  }),

  http.put('/api/v1/workspaces/:workspaceId', async ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedUpdate = requirePermission(request, 'workspace.update');
    if (deniedUpdate) return deniedUpdate;
    const body = (await request.json()) as UpdateWorkspaceRequest;
    if (!body.name?.trim()) {
      return problem(400, 'validation_failed', 'name is required', 'Validation failed');
    }
    const workspace = updateWorkspace(workspaceId, body.name.trim());
    return HttpResponse.json(workspace);
  }),

  http.delete('/api/v1/workspaces/:workspaceId', ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedDelete = requirePermission(request, 'workspace.delete');
    if (deniedDelete) return deniedDelete;
    if (!deleteWorkspace(workspaceId)) {
      return problem(409, 'conflict', 'Cannot delete this workspace', 'Conflict');
    }
    return new HttpResponse(null, { status: 204 });
  }),

  // --- Projects ---
  http.get('/api/v1/workspaces/:workspaceId/projects', ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'projects.view');
    if (deniedView) return deniedView;
    return HttpResponse.json({ items: listProjects(workspaceId), hasMore: false });
  }),

  http.post('/api/v1/workspaces/:workspaceId/projects', async ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedManage = requirePermission(request, 'projects.manage');
    if (deniedManage) return deniedManage;
    const body = (await request.json()) as CreateProjectRequest;
    if (!body.name?.trim()) {
      return problem(400, 'validation_failed', 'name is required', 'Validation failed');
    }
    const project = createProject(workspaceId, body.name.trim(), body.color);
    return HttpResponse.json(project, { status: 201 });
  }),

  http.get('/api/v1/projects/:projectId', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const project = getProject(String(params.projectId));
    if (!project?.workspaceId || !isMemberOfWorkspace(project.workspaceId)) {
      return problem(404, 'not_found', 'Project not found', 'Not found');
    }
    const deniedView = requirePermission(request, 'projects.view');
    if (deniedView) return deniedView;
    return HttpResponse.json(project);
  }),

  http.put('/api/v1/projects/:projectId', async ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const projectId = String(params.projectId);
    const existing = getProject(projectId);
    if (!existing?.workspaceId || !isMemberOfWorkspace(existing.workspaceId)) {
      return problem(404, 'not_found', 'Project not found', 'Not found');
    }
    const deniedManage = requirePermission(request, 'projects.manage');
    if (deniedManage) return deniedManage;
    const body = (await request.json()) as UpdateProjectRequest;
    const project = updateProject(projectId, body);
    return HttpResponse.json(project);
  }),

  http.delete('/api/v1/projects/:projectId', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const projectId = String(params.projectId);
    const existing = getProject(projectId);
    if (!existing?.workspaceId || !isMemberOfWorkspace(existing.workspaceId)) {
      return problem(404, 'not_found', 'Project not found', 'Not found');
    }
    const deniedManage = requirePermission(request, 'projects.manage');
    if (deniedManage) return deniedManage;
    deleteProject(projectId);
    return new HttpResponse(null, { status: 204 });
  }),

  http.put('/api/v1/projects/:projectId/members', async ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const projectId = String(params.projectId);
    const existing = getProject(projectId);
    if (!existing?.workspaceId || !isMemberOfWorkspace(existing.workspaceId)) {
      return problem(404, 'not_found', 'Project not found', 'Not found');
    }
    const denied = requirePermission(request, 'members.update');
    if (denied) return denied;
    const body = (await request.json()) as ProjectMembersRequest;
    const project = setProjectMembers(projectId, body.userIds ?? []);
    return HttpResponse.json(project);
  }),

  // --- Members ---
  http.get('/api/v1/workspaces/:workspaceId/members', ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'members.view');
    if (deniedView) return deniedView;
    return HttpResponse.json({ items: listMembers(workspaceId), hasMore: false });
  }),

  http.put('/api/v1/workspaces/:workspaceId/members/:userId', async ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const userId = String(params.userId);
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedUpdate = requirePermission(request, 'members.update');
    if (deniedUpdate) return deniedUpdate;
    const body = (await request.json()) as UpdateMemberRequest;
    if (!isWorkspaceRole(body.role) || body.role === 'owner') {
      return problem(400, 'validation_failed', 'Invalid role', 'Validation failed');
    }
    const member = updateMember(workspaceId, userId, {
      role: body.role,
      projectIds: body.projectIds,
    });
    if (!member) {
      return problem(404, 'not_found', 'Member not found', 'Not found');
    }
    return HttpResponse.json(member);
  }),

  http.delete('/api/v1/workspaces/:workspaceId/members/:userId', ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const userId = String(params.userId);
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedRemove = requirePermission(request, 'members.remove');
    if (deniedRemove) return deniedRemove;
    if (!removeMember(workspaceId, userId)) {
      return problem(404, 'not_found', 'Member not found', 'Not found');
    }
    return new HttpResponse(null, { status: 204 });
  }),

  // --- Invitations ---
  http.get('/api/v1/workspaces/:workspaceId/invitations', ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'members.view');
    if (deniedView) return deniedView;
    return HttpResponse.json({ items: listInvitations(workspaceId), hasMore: false });
  }),

  http.post('/api/v1/workspaces/:workspaceId/invitations', async ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedInvite = requirePermission(request, 'members.invite');
    if (deniedInvite) return deniedInvite;
    const body = (await request.json()) as CreateInvitationRequest;
    if (!body.email || !body.role) {
      return problem(400, 'validation_failed', 'email and role are required', 'Validation failed');
    }
    if (body.role === 'owner') {
      return problem(400, 'validation_failed', 'Cannot invite as owner', 'Validation failed');
    }
    const result = createInvitation(workspaceId, {
      email: body.email,
      role: body.role,
      projectIds: body.projectIds,
    });
    if ('error' in result) {
      return problem(
        409,
        'conflict',
        'Seat limit reached. Upgrade your plan to invite more members.',
        'Conflict',
      );
    }
    const { token: _token, status: _status, ...invitation } = result;
    return HttpResponse.json(invitation, { status: 201 });
  }),

  http.delete('/api/v1/invitations/:invitationId', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const denied = requirePermission(request, 'members.invite');
    if (denied) return denied;
    const invitation = cancelInvitation(String(params.invitationId));
    if (!invitation) {
      return problem(404, 'not_found', 'Invitation not found', 'Not found');
    }
    return new HttpResponse(null, { status: 204 });
  }),

  http.get('/api/v1/invitations/preview', ({ request }) => {
    const url = new URL(request.url);
    const token = url.searchParams.get('token');
    if (!token) {
      return problem(400, 'validation_failed', 'token is required', 'Validation failed');
    }
    const invitation = previewInvitation(token);
    if (!invitation) {
      return problem(404, 'not_found', 'Invitation not found or expired', 'Not found');
    }
    return HttpResponse.json(invitation);
  }),

  http.post('/api/v1/invitations/accept', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const body = (await request.json()) as { token?: string };
    if (!body.token) {
      return problem(400, 'validation_failed', 'token is required', 'Validation failed');
    }
    const result = acceptInvitation(body.token);
    if ('error' in result) {
      return problem(404, 'not_found', 'Invitation not found or expired', 'Not found');
    }
    return HttpResponse.json(result.workspace);
  }),

  // --- Audit ---
  http.get('/api/v1/workspaces/:workspaceId/audit', ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedAudit = requirePermission(request, 'audit.view');
    if (deniedAudit) return deniedAudit;
    return HttpResponse.json({ items: listAudit(workspaceId), hasMore: false });
  }),
];
