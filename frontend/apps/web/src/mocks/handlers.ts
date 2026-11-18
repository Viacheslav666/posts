import type {
  BulkPostsRequest,
  BatchPostsRequest,
  BatchStoriesRequest,
  ChannelConnectCallbackRequest,
  ChannelConnectConfirmRequest,
  CompleteUploadRequest,
  CreateChannelSetRequest,
  StartStoryConnectionRequest,
  CreateInvitationRequest,
  CreateOrdContractRequest,
  CreateOrdContractorRequest,
  CreatePostRequest,
  CreateProjectRequest,
  CreateUploadRequest,
  CreateWorkspaceRequest,
  EmailVerifyRequest,
  GrabberBulkModerationRequest,
  GrabberItemEditApproveRequest,
  GrabberItemRejectRequest,
  GrabberRuleCreate,
  GrabberRulePatch,
  IngestPreviewRequest,
  LoginRequest,
  MoveCalendarItemRequest,
  OAuthStartResponse,
  OrdProvider,
  OrdReportRunRequest,
  OrdVatCalculateRequest,
  PasswordForgotRequest,
  PasswordResetRequest,
  PostActionRequest,
  PreviewRequest,
  ProblemDetail,
  ProjectMembersRequest,
  RegisterRequest,
  TokenResponse,
  MarkNotificationsReadRequest,
  UpdateChannelRequest,
  UpdateChannelSetRequest,
  UpdateMemberRequest,
  UpdateMeRequest,
  UpdateNotificationSettingsRequest,
  UpdateOrdContractorRequest,
  UpdatePasswordRequest,
  UpdatePostRequest,
  UpdateProjectRequest,
  UpdateWorkspaceRequest,
  UpsertOrdConnectionRequest,
  WebPushSubscribeRequest,
} from '@posts/api-client';
import { http, HttpResponse } from 'msw';

import { MOCK_NETWORK_CAPABILITIES } from '@/features/editor/api/capabilities-mock';
import type { MediaKind, MediaStatus } from '@/features/media/model/types';
import type { ItemStatus } from '@/features/grabber/model/types';
import {
  INSUFFICIENT_PERMISSIONS_CODE,
  PLAN_LIMIT_EXCEEDED_CODE,
  TWO_FACTOR_REQUIRED_CODE,
} from '@/shared/lib/errors';
import {
  adjustBalance,
  adjustSubscription,
  createAnnouncement,
  createPromo,
  deleteQueueMessage,
  endImpersonationOnRefresh,
  getDemoAdminProfile,
  getUserCard,
  getWorkspaceCard,
  impersonate,
  listAdminPlansFromStore,
  listAnnouncements,
  listAudit as listAdminAuditLog,
  listFeatureFlags,
  listPromos,
  listQueueMessages,
  listQueues,
  requirePlatformAdmin,
  retryQueueMessage,
  searchUsers,
  searchWorkspaces,
  setWorkspaceFlag,
  updateAnnouncement,
  updatePromo,
  upsertAdminPlanInStore,
  upsertFeatureFlag,
} from './admin-store';
import {
  changePlan,
  checkoutTopup,
  completeMockPayment,
  deletePaymentMethod,
  getAutopay,
  getInvoice,
  getPayment,
  getPaymentMethod,
  getWallet,
  listInvoices,
  listPayments,
  listPlans,
  listTransactions,
  mockPdfBytes,
  requestInvoice,
  updateAutopay,
} from './billing-store';
import {
  can,
  isWorkspaceRole,
  type PermissionAction,
  type WorkspaceRole,
} from '@/shared/lib/permissions';

import {
  channelLimitForWorkspace,
  checkChannel,
  confirmBotConnect,
  connectCallback,
  countActiveChannels,
  createChannelSet,
  deleteChannel,
  deleteChannelSet,
  getChannel,
  getChannelSet,
  getStoryConnection,
  listChannelSets,
  listChannels,
  startConnect,
  startStoryConnection,
  updateChannel,
  updateChannelSet,
  type ConnectNetwork,
} from './channel-store';
import {
  activateRule,
  approveItem,
  archiveRule,
  bulkModerate,
  cloneRuleConfig,
  createRule,
  editAndApproveItem,
  exportItemsCsv,
  getItem,
  getRule,
  listItems,
  listRules,
  listRuns,
  pauseRule,
  patchRule,
  previewSource,
  rejectItem,
  resumeRule,
  startRun,
  type GrabberMutationError,
} from './grabber-store';
import {
  advanceProcessing,
  completeUpload,
  createUpload,
  deleteMedia,
  getMedia,
  getQuota,
  importFromUrl,
  listFolders,
  listMedia,
  receivePart,
} from './media-store';
import {
  createOrdContract,
  createOrdContractor,
  getCreativeForPost,
  getOrdContractor,
  hasActiveOrdConnection,
  KKTU_DICTIONARY,
  listOrdConnections,
  listOrdContractors,
  listOrdContracts,
  OKSM_DICTIONARY,
  registerCreative,
  testOrdConnection,
  toPublicConnection,
  updateOrdContractor,
  upsertOrdConnection,
  validateAdMarking,
} from './ord-store';
import {
  getNotificationSettings,
  getVapidPublicKey,
  listNotifications,
  markNotificationsRead,
  startTelegramLink,
  subscribeWebPush,
  unlinkTelegram,
  unsubscribeWebPush,
  updateNotificationSettings,
} from './notification-store';
import {
  attachEridToPost,
  batchCreatePosts,
  batchCreateStories,
  bulkPosts,
  createPost,
  deletePost,
  duplicatePost,
  getPost,
  getPublication,
  listCalendar,
  listPosts,
  listPublicationAttempts,
  listPublicationsForPost,
  moveCalendarItem,
  pausePost,
  resumePost,
  retryPublication,
  toApiPost,
  updatePost,
  type ListPostsFilters,
} from './post-store';
import { renderEditorVariables } from '@/features/editor/lib/variables';
import { invalidVkStringAttachments } from '@/features/editor/lib/vk-string-attachments';
import {
  acceptInvitation,
  cancelInvitation,
  changeDemoPassword,
  createInvitation,
  createProject,
  createWorkspace,
  deleteProject,
  deleteWorkspace,
  getProject,
  getWorkspace,
  isKnownWorkspace,
  isMemberOfWorkspace,
  listAudit,
  listDemoSessions,
  listInvitations,
  listMembers,
  listProjects,
  listWorkspaces,
  previewInvitation,
  removeMember,
  revokeDemoSession,
  setDemoTotpEnabled,
  updateDemoProfile,
  updateMember,
  updateProject,
  updateWorkspace,
  setProjectMembers,
} from './workspace-store';

function grabberError(error: GrabberMutationError) {
  return problem(error.status, error.code, error.detail, error.code);
}

function isGrabberError(value: unknown): value is GrabberMutationError {
  return Boolean(value && typeof value === 'object' && 'code' in value && 'status' in value);
}

const CONNECT_NETWORKS = new Set<ConnectNetwork>(['vk', 'tg', 'ok', 'max', 'rutube']);

function parseConnectNetwork(value: string): ConnectNetwork | null {
  return CONNECT_NETWORKS.has(value as ConnectNetwork) ? (value as ConnectNetwork) : null;
}

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

const VK_STORY_LINK = /^https:\/\/(vk\.com|vk\.ru|m\.vk\.com|m\.vk\.ru)(\/|$)/i;

function validateStoryCreate(
  body: CreatePostRequest,
): { code: string; detail: string; title: string } | null {
  const mediaIds = body.baseContent?.mediaIds ?? [];
  if (mediaIds.length !== 1) {
    return {
      code: 'story_media_invalid',
      detail: 'Story requires exactly one media asset',
      title: 'Invalid story media',
    };
  }

  const channelIds =
    body.variants
      ?.map((variant) => variant.channelId)
      .filter((id): id is string => Boolean(id)) ?? [];
  if (channelIds.length === 0) {
    return {
      code: 'validation_failed',
      detail: 'At least one channel is required',
      title: 'Validation failed',
    };
  }

  let hasVk = false;
  for (const channelId of channelIds) {
    const channel = getChannel(channelId);
    if (!channel?.network) {
      return {
        code: 'not_found',
        detail: `Channel ${channelId} not found`,
        title: 'Not found',
      };
    }
    if (channel.network !== 'vk' && channel.network !== 'ok') {
      return {
        code: 'story_unsupported_network',
        detail: `Stories are not supported for ${channel.network}`,
        title: 'Unsupported network',
      };
    }
    if (channel.network === 'vk') {
      hasVk = true;
      if ((channel.storyConnectionStatus ?? 'missing') !== 'ready') {
        return {
          code: 'story_connection_required',
          detail: 'VK channel requires a separate stories connection',
          title: 'Story connection required',
        };
      }
    }
  }

  const linkText = body.story?.linkText?.trim();
  const linkUrl = body.story?.linkUrl?.trim();
  if ((linkText && !linkUrl) || (!linkText && linkUrl)) {
    return {
      code: 'story_link_invalid',
      detail: 'linkText and linkUrl must be set together',
      title: 'Invalid story link',
    };
  }
  if (hasVk && linkUrl && !VK_STORY_LINK.test(linkUrl)) {
    return {
      code: 'story_link_invalid',
      detail: 'VK story CTA URL must be vk.com / vk.ru',
      title: 'Invalid story link',
    };
  }

  const ttl = body.story?.ttl;
  if (ttl && !['ttl_1h', 'ttl_12h', 'ttl_24h', 'ttl_48h'].includes(ttl)) {
    return {
      code: 'story_ttl_unsupported',
      detail: 'Unsupported story TTL',
      title: 'Unsupported TTL',
    };
  }

  return null;
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
    return HttpResponse.json(getDemoAdminProfile(request));
  }),

  http.patch('/api/v1/me', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) {
      return unauthorized;
    }
    const body = (await request.json()) as UpdateMeRequest;
    return HttpResponse.json(updateDemoProfile(body));
  }),

  http.post('/api/v1/me/password', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) {
      return unauthorized;
    }
    const body = (await request.json()) as UpdatePasswordRequest;
    const result = changeDemoPassword(body);
    if ('error' in result) {
      return problem(400, 'validation_failed', result.error, 'Validation failed');
    }
    return HttpResponse.json(result);
  }),

  http.get('/api/v1/me/sessions', ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) {
      return unauthorized;
    }
    return HttpResponse.json({ items: listDemoSessions(), hasMore: false });
  }),

  http.delete('/api/v1/me/sessions/:sessionId', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) {
      return unauthorized;
    }
    const ok = revokeDemoSession(String(params.sessionId));
    if (!ok) {
      return problem(404, 'not_found', 'Session not found', 'Not found');
    }
    return HttpResponse.json({ message: 'ok' });
  }),

  http.post('/api/v1/auth/2fa/setup', ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) {
      return unauthorized;
    }
    return HttpResponse.json({
      secret: 'JBSWY3DPEHPK3PXP',
      otpauthUrl: 'otpauth://totp/Posts:demo@posts.app?secret=JBSWY3DPEHPK3PXP&issuer=Posts',
      backupCodes: ['11111111', '22222222', '33333333'],
    });
  }),

  http.post('/api/v1/auth/2fa/confirm', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) {
      return unauthorized;
    }
    const body = (await request.json()) as { code?: string };
    if (body.code !== '123456') {
      return problem(400, 'validation_failed', 'Invalid TOTP code', 'Validation failed');
    }
    setDemoTotpEnabled(true);
    return HttpResponse.json({ message: 'ok' });
  }),

  http.post('/api/v1/auth/2fa/disable', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) {
      return unauthorized;
    }
    const body = (await request.json()) as { password?: string; totpCode?: string };
    if (body.password !== 'Password1!' || body.totpCode !== '123456') {
      return problem(400, 'validation_failed', 'Invalid credentials', 'Validation failed');
    }
    setDemoTotpEnabled(false);
    return HttpResponse.json({ message: 'ok' });
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

  http.post('/api/v1/auth/refresh', () => {
    endImpersonationOnRefresh();
    return HttpResponse.json(tokenResponse({ accessToken: 'msw.access.refreshed' }), {
      headers: { 'Set-Cookie': refreshCookieHeader() },
    });
  }),

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

  // --- Channels (FE-6) ---
  http.get('/api/v1/channels', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required', 'Validation failed');
    }
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'channels.view');
    if (deniedView) return deniedView;
    const items = listChannels(workspaceId, {
      projectId: url.searchParams.get('projectId') ?? undefined,
      network: url.searchParams.get('network') ?? undefined,
      status: url.searchParams.get('status') ?? undefined,
    });
    return HttpResponse.json({
      items,
      hasMore: false,
      meta: {
        used: countActiveChannels(workspaceId),
        limit: channelLimitForWorkspace(workspaceId),
      },
    });
  }),

  http.post('/api/v1/channels/connect/:network/start', ({ request, params }) => {
    const network = parseConnectNetwork(String(params.network));
    if (!network) {
      return problem(400, 'validation_failed', 'Unknown network', 'Validation failed');
    }
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required', 'Validation failed');
    }
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedManage = requirePermission(request, 'channels.manage');
    if (deniedManage) return deniedManage;
    if (countActiveChannels(workspaceId) >= channelLimitForWorkspace(workspaceId)) {
      return problem(
        402,
        PLAN_LIMIT_EXCEEDED_CODE,
        'Channel limit for the current plan is reached. Upgrade to connect more channels.',
        'Payment required',
      );
    }
    const projectId = url.searchParams.get('projectId') ?? undefined;
    return HttpResponse.json(startConnect(network, workspaceId, projectId));
  }),

  http.post('/api/v1/channels/connect/:network/callback', async ({ request, params }) => {
    const network = parseConnectNetwork(String(params.network));
    if (!network) {
      return problem(400, 'validation_failed', 'Unknown network', 'Validation failed');
    }
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedManage = requirePermission(request, 'channels.manage');
    if (deniedManage) return deniedManage;
    const body = (await request.json()) as ChannelConnectCallbackRequest;
    if (!body.workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required', 'Validation failed');
    }
    const denied = requireWorkspaceAccess(request, body.workspaceId);
    if (denied) return denied;
    const result = connectCallback(network, body);
    if ('error' in result) {
      if (result.error === 'plan_limit_exceeded') {
        return problem(
          402,
          PLAN_LIMIT_EXCEEDED_CODE,
          'Channel limit for the current plan is reached. Upgrade to connect more channels.',
          'Payment required',
        );
      }
      if (result.error === 'insufficient_permissions') {
        return problem(
          403,
          INSUFFICIENT_PERMISSIONS_CODE,
          'Bot lacks required admin permissions. Grant can_post_messages, can_edit_messages, can_delete_messages, can_pin_messages.',
          'Forbidden',
        );
      }
      return problem(400, 'validation_failed', 'Invalid connect callback payload', 'Validation failed');
    }
    return HttpResponse.json({ items: result, hasMore: false });
  }),

  http.post('/api/v1/channels/connect/:network/confirm', async ({ request, params }) => {
    const network = parseConnectNetwork(String(params.network));
    if (!network) {
      return problem(400, 'validation_failed', 'Unknown network', 'Validation failed');
    }
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedManage = requirePermission(request, 'channels.manage');
    if (deniedManage) return deniedManage;
    const body = (await request.json()) as ChannelConnectConfirmRequest;
    if (!body.workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required', 'Validation failed');
    }
    const denied = requireWorkspaceAccess(request, body.workspaceId);
    if (denied) return denied;
    const result = confirmBotConnect(network, body);
    if ('error' in result) {
      if (result.error === 'plan_limit_exceeded') {
        return problem(
          402,
          PLAN_LIMIT_EXCEEDED_CODE,
          'Channel limit for the current plan is reached. Upgrade to connect more channels.',
          'Payment required',
        );
      }
      if (result.error === 'insufficient_permissions') {
        return problem(
          403,
          INSUFFICIENT_PERMISSIONS_CODE,
          'Bot lacks required admin permissions. Add @PostsBot as admin with post/edit/delete/pin rights.',
          'Forbidden',
        );
      }
      return problem(400, 'validation_failed', 'username or forwarded message is required', 'Validation failed');
    }
    return HttpResponse.json({ items: result, hasMore: false });
  }),

  http.get('/api/v1/channels/:channelId', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedView = requirePermission(request, 'channels.view');
    if (deniedView) return deniedView;
    const channel = getChannel(String(params.channelId));
    if (!channel) {
      return problem(404, 'not_found', 'Channel not found', 'Not found');
    }
    return HttpResponse.json(channel);
  }),

  http.put('/api/v1/channels/:channelId', async ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedManage = requirePermission(request, 'channels.manage');
    if (deniedManage) return deniedManage;
    const body = (await request.json()) as UpdateChannelRequest;
    const channel = updateChannel(String(params.channelId), body);
    if (!channel) {
      return problem(404, 'not_found', 'Channel not found', 'Not found');
    }
    return HttpResponse.json(channel);
  }),

  http.delete('/api/v1/channels/:channelId', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedManage = requirePermission(request, 'channels.manage');
    if (deniedManage) return deniedManage;
    if (!deleteChannel(String(params.channelId))) {
      return problem(404, 'not_found', 'Channel not found', 'Not found');
    }
    return HttpResponse.json({ message: 'disconnected' });
  }),

  http.post('/api/v1/channels/:channelId/check', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedManage = requirePermission(request, 'channels.manage');
    if (deniedManage) return deniedManage;
    const result = checkChannel(String(params.channelId));
    if (!result) {
      return problem(404, 'not_found', 'Channel not found', 'Not found');
    }
    return HttpResponse.json(result);
  }),

  http.get('/api/v1/channels/:channelId/story-connection', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedView = requirePermission(request, 'channels.view');
    if (deniedView) return deniedView;
    const connection = getStoryConnection(String(params.channelId));
    if (!connection) {
      return problem(404, 'not_found', 'Channel not found', 'Not found');
    }
    return HttpResponse.json(connection);
  }),

  http.post('/api/v1/channels/:channelId/story-connection', async ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedManage = requirePermission(request, 'channels.manage');
    if (deniedManage) return deniedManage;
    const body = ((await request.json().catch(() => ({}))) ?? {}) as StartStoryConnectionRequest;
    const result = startStoryConnection(String(params.channelId), body);
    if ('error' in result) {
      if (result.error === 'unsupported') {
        return problem(
          422,
          'story_unsupported_network',
          'Story connect is only for VK (OK does not require it)',
          'Unsupported network',
        );
      }
      return problem(404, 'not_found', 'Channel not found', 'Not found');
    }
    return HttpResponse.json(result);
  }),

  // --- Channel sets ---
  http.get('/api/v1/channel-sets', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required', 'Validation failed');
    }
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'channel_sets.view');
    if (deniedView) return deniedView;
    return HttpResponse.json({ items: listChannelSets(workspaceId), hasMore: false });
  }),

  http.post('/api/v1/channel-sets', async ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required', 'Validation failed');
    }
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedManage = requirePermission(request, 'channel_sets.manage');
    if (deniedManage) return deniedManage;
    const body = (await request.json()) as CreateChannelSetRequest;
    const result = createChannelSet(workspaceId, body);
    if ('error' in result) {
      return problem(
        400,
        'validation_failed',
        'Channel set name is required and max 50 channels',
        'Validation failed',
      );
    }
    return HttpResponse.json(result, { status: 201 });
  }),

  http.get('/api/v1/channel-sets/:setId', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedView = requirePermission(request, 'channel_sets.view');
    if (deniedView) return deniedView;
    const set = getChannelSet(String(params.setId));
    if (!set) {
      return problem(404, 'not_found', 'Channel set not found', 'Not found');
    }
    return HttpResponse.json(set);
  }),

  http.put('/api/v1/channel-sets/:setId', async ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedManage = requirePermission(request, 'channel_sets.manage');
    if (deniedManage) return deniedManage;
    const body = (await request.json()) as UpdateChannelSetRequest;
    const result = updateChannelSet(String(params.setId), body);
    if (!result) {
      return problem(404, 'not_found', 'Channel set not found', 'Not found');
    }
    if ('error' in result) {
      return problem(400, 'validation_failed', 'Max 50 channels per set', 'Validation failed');
    }
    return HttpResponse.json(result);
  }),

  http.delete('/api/v1/channel-sets/:setId', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedManage = requirePermission(request, 'channel_sets.manage');
    if (deniedManage) return deniedManage;
    if (!deleteChannelSet(String(params.setId))) {
      return problem(404, 'not_found', 'Channel set not found', 'Not found');
    }
    return HttpResponse.json({ message: 'deleted' });
  }),

  // --- Media (OpenAPI + MSW extensions for DELETE / import-url / quota / folders until BE-8) ---
  http.get('/api/v1/media', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required', 'Validation failed');
    }
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'media.view');
    if (deniedView) return deniedView;
    const kind = url.searchParams.get('kind') as MediaKind | null;
    const status = url.searchParams.get('status') as MediaStatus | null;
    const page = listMedia({
      workspaceId,
      kind: kind ?? undefined,
      status: status ?? undefined,
      q: url.searchParams.get('q') ?? undefined,
      folderId: url.searchParams.get('folderId'),
      tag: url.searchParams.get('tag') ?? undefined,
      cursor: url.searchParams.get('cursor') ?? undefined,
      limit: url.searchParams.get('limit') ? Number(url.searchParams.get('limit')) : 20,
    });
    return HttpResponse.json(page);
  }),

  http.post('/api/v1/media/uploads', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedManage = requirePermission(request, 'media.manage');
    if (deniedManage) return deniedManage;
    const body = (await request.json()) as CreateUploadRequest;
    if (!body.workspaceId || !body.kind || !body.mime || !body.sizeBytes) {
      return problem(
        400,
        'validation_failed',
        'workspaceId, kind, mime, sizeBytes are required',
        'Validation failed',
      );
    }
    const denied = requireWorkspaceAccess(request, body.workspaceId);
    if (denied) return denied;
    const created = createUpload({
      workspaceId: body.workspaceId,
      kind: body.kind,
      mime: body.mime,
      sizeBytes: body.sizeBytes,
      filename: body.filename,
      sha256: body.sha256,
    });
    return HttpResponse.json(
      {
        mediaId: created.mediaId,
        uploadUrl: created.uploadUrl,
        expiresAt: created.expiresAt,
        headers: created.headers,
      },
      { status: 201 },
    );
  }),

  http.get('/api/v1/media/quota', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required', 'Validation failed');
    }
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'media.view');
    if (deniedView) return deniedView;
    const workspace = getWorkspace(workspaceId);
    return HttpResponse.json(getQuota(workspaceId, workspace?.planCode));
  }),

  http.get('/api/v1/media/folders', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required', 'Validation failed');
    }
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'media.view');
    if (deniedView) return deniedView;
    return HttpResponse.json({ items: listFolders(workspaceId) });
  }),

  http.post('/api/v1/media/import-url', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedManage = requirePermission(request, 'media.manage');
    if (deniedManage) return deniedManage;
    const body = (await request.json()) as {
      workspaceId?: string;
      url?: string;
      filename?: string;
    };
    if (!body.workspaceId || !body.url) {
      return problem(400, 'validation_failed', 'workspaceId and url are required', 'Validation failed');
    }
    const denied = requireWorkspaceAccess(request, body.workspaceId);
    if (denied) return denied;
    const asset = importFromUrl({
      workspaceId: body.workspaceId,
      url: body.url,
      filename: body.filename,
    });
    return HttpResponse.json(asset, { status: 201 });
  }),

  http.get('/api/v1/media/:mediaId', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedView = requirePermission(request, 'media.view');
    if (deniedView) return deniedView;
    const mediaId = String(params.mediaId);
    advanceProcessing(mediaId);
    const asset = getMedia(mediaId);
    if (!asset) {
      return problem(404, 'not_found', 'Media not found', 'Not found');
    }
    return HttpResponse.json(asset);
  }),

  http.post('/api/v1/media/:mediaId/complete', async ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedManage = requirePermission(request, 'media.manage');
    if (deniedManage) return deniedManage;
    const body = ((await request.json()) as CompleteUploadRequest) ?? {};
    const asset = completeUpload(String(params.mediaId), body);
    if (!asset) {
      return problem(404, 'not_found', 'Upload session not found', 'Not found');
    }
    return HttpResponse.json(asset);
  }),

  http.delete('/api/v1/media/:mediaId', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedManage = requirePermission(request, 'media.manage');
    if (deniedManage) return deniedManage;
    if (!deleteMedia(String(params.mediaId))) {
      return problem(404, 'not_found', 'Media not found', 'Not found');
    }
    return HttpResponse.json({ message: 'deleted' });
  }),

  // Mock S3 multipart PUT (same-origin; never hits external network)
  http.put('/mock-s3/:mediaId/upload', async ({ request, params }) => {
    const url = new URL(request.url);
    const partNumber = Number(url.searchParams.get('partNumber') ?? '1');
    const buffer = await request.arrayBuffer();
    const result = receivePart(String(params.mediaId), partNumber, buffer.byteLength);
    if (!result) {
      return problem(404, 'not_found', 'Upload session not found', 'Not found');
    }
    return new HttpResponse(null, {
      status: 200,
      headers: { ETag: `"${result.etag}"` },
    });
  }),

  // --- FE-8: calendar + posts + publications ---

  http.get('/api/v1/calendar', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    const from = url.searchParams.get('from');
    const to = url.searchParams.get('to');
    if (!workspaceId || !from || !to) {
      return problem(400, 'validation_failed', 'workspaceId, from and to are required', 'Validation failed');
    }
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'calendar.view');
    if (deniedView) return deniedView;
    const contentKind = url.searchParams.get('contentKind');
    const items = listCalendar({
      workspaceId,
      from,
      to,
      projectId: url.searchParams.get('projectId') ?? undefined,
      channelId: url.searchParams.get('channelId') ?? undefined,
      authorId: url.searchParams.get('authorId') ?? undefined,
      network: url.searchParams.get('network') ?? undefined,
      status: url.searchParams.get('status') ?? undefined,
      contentKind:
        contentKind === 'post' || contentKind === 'story' ? contentKind : undefined,
    });
    return HttpResponse.json(items);
  }),

  http.post('/api/v1/calendar/:postId/move', async ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedMove = requirePermission(request, 'calendar.move');
    if (deniedMove) return deniedMove;
    const body = (await request.json()) as MoveCalendarItemRequest;
    const result = moveCalendarItem(String(params.postId), body);
    if (!result) {
      return problem(404, 'not_found', 'Post not found', 'Not found');
    }
    if ('conflict' in result) {
      return problem(409, 'optimistic_lock_conflict', 'Post version conflict', 'Conflict');
    }
    return HttpResponse.json(toApiPost(result));
  }),

  http.post('/api/v1/posts', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedCreate = requirePermission(request, 'posts.create');
    if (deniedCreate) return deniedCreate;
    const body = (await request.json()) as CreatePostRequest;
    if (!body.workspaceId || !body.baseContent) {
      return problem(400, 'validation_failed', 'workspaceId and baseContent are required', 'Validation failed');
    }
    const deniedWs = requireWorkspaceAccess(request, body.workspaceId);
    if (deniedWs) return deniedWs;

    if (body.scheduledAt) {
      const deniedSchedule = requirePermission(request, 'posts.schedule');
      if (deniedSchedule) return deniedSchedule;
    }

    if (body.contentKind === 'story') {
      const storyError = validateStoryCreate(body);
      if (storyError) {
        return problem(422, storyError.code, storyError.detail, storyError.title);
      }
    }

    if (body.ad) {
      const deniedAd = requirePermission(request, 'posts.ad.schedule');
      if (deniedAd) return deniedAd;
      if (body.contentKind === 'story') {
        const channelIds =
          body.variants
            ?.map((variant) => variant.channelId)
            .filter((id): id is string => Boolean(id)) ?? [];
        for (const channelId of channelIds) {
          const channel = getChannel(channelId);
          if (channel && channel.network !== 'vk' && channel.network !== 'ok') {
            return problem(
              422,
              'ads_not_allowed_for_network',
              'Маркировка историй доступна только для VK и OK',
              'Ads not allowed',
            );
          }
        }
      }
      if (!hasActiveOrdConnection(body.workspaceId)) {
        return problem(
          422,
          'ord_not_configured',
          'Подключите ОРД в настройках workspace перед планированием рекламы',
          'ORD not configured',
        );
      }
      const invalid = validateAdMarking(body.adMarking);
      if (invalid) {
        return problem(400, invalid.code, invalid.detail, 'Validation failed');
      }
      if (body.scheduledAt && !body.adMarking?.erid) {
        return problem(
          422,
          'ord_erid_missing',
          'Нельзя запланировать рекламный пост без erid. Зарегистрируйте креатив в ОРД.',
          'Erid missing',
        );
      }
    }

    const post = createPost(body);
    return HttpResponse.json(toApiPost(post), { status: 201 });
  }),

  http.put('/api/v1/posts/:postId', async ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedUpdate = requirePermission(request, 'posts.update');
    if (deniedUpdate) return deniedUpdate;
    const body = (await request.json()) as UpdatePostRequest;
    if (body.ad) {
      const deniedAd = requirePermission(request, 'posts.ad.schedule');
      if (deniedAd) return deniedAd;
    }
    const updated = updatePost(String(params.postId), body);
    if (!updated) return problem(404, 'not_found', 'Post not found', 'Not found');
    return HttpResponse.json(toApiPost(updated));
  }),

  http.post('/api/v1/ord/vat/calculate', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const body = (await request.json()) as OrdVatCalculateRequest;
    const amount = Number(body.amountWithVat ?? 0);
    const rate = body.vatRate ?? 'without_vat';
    const gross = Math.round(Math.max(0, amount) * 100) / 100;
    if (rate === 'without_vat' || rate === '0') {
      return HttpResponse.json({
        amountWithVat: gross,
        amountWithoutVat: gross,
        vatAmount: 0,
        vatRate: rate,
      });
    }
    const percent = Number(rate);
    const net = Math.round(((gross * 100) / (100 + percent)) * 100) / 100;
    const vat = Math.round((gross - net) * 100) / 100;
    return HttpResponse.json({
      amountWithVat: gross,
      amountWithoutVat: net,
      vatAmount: vat,
      vatRate: rate,
    });
  }),

  http.get('/api/v1/ord/dictionaries/kktu', ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const denied = requirePermission(request, 'ord.view');
    if (denied) return denied;
    const url = new URL(request.url);
    const q = (url.searchParams.get('q') ?? '').trim().toLowerCase();
    const items = KKTU_DICTIONARY.map((item) => ({ code: item.code, name: item.name })).filter(
      (item) => !q || item.code.includes(q) || item.name.toLowerCase().includes(q),
    );
    return HttpResponse.json(items);
  }),

  http.get('/api/v1/ord/dictionaries/oksm', ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const denied = requirePermission(request, 'ord.view');
    if (denied) return denied;
    const url = new URL(request.url);
    const q = (url.searchParams.get('q') ?? '').trim().toLowerCase();
    const items = OKSM_DICTIONARY.map((item) => ({ code: item.code, name: item.name })).filter(
      (item) => !q || item.code.includes(q) || item.name.toLowerCase().includes(q),
    );
    return HttpResponse.json(items);
  }),

  http.post('/api/v1/ord/reports/run', async ({ request }) => {
    const denied = requirePermission(request, 'ord.reports.run');
    if (denied) return denied;
    const body = (await request.json()) as OrdReportRunRequest;
    const deniedWs = requireWorkspaceAccess(request, body.workspaceId);
    if (deniedWs) return deniedWs;
    return HttpResponse.json({
      ok: true,
      invoicesCreated: 1,
      statisticsCreated: 1,
      period: `${body.year}-${String(body.month).padStart(2, '0')}`,
    });
  }),

  http.get('/api/v1/workspaces/:workspaceId/ord/connections', ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const deniedWs = requireWorkspaceAccess(request, workspaceId);
    if (deniedWs) return deniedWs;
    const denied = requirePermission(request, 'ord.view');
    if (denied) return denied;
    return HttpResponse.json({
      items: listOrdConnections(workspaceId),
      hasMore: false,
    });
  }),

  http.put('/api/v1/workspaces/:workspaceId/ord/connections/:provider', async ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const provider = String(params.provider) as OrdProvider;
    const deniedWs = requireWorkspaceAccess(request, workspaceId);
    if (deniedWs) return deniedWs;
    const denied = requirePermission(request, 'ord.connection.manage');
    if (denied) return denied;
    if (provider !== 'vk_ord' && provider !== 'yandex_ord') {
      return problem(400, 'validation_failed', 'Unsupported ORD provider', 'Validation failed');
    }
    const body = (await request.json()) as UpsertOrdConnectionRequest;
    if (!body.responsibilityAccepted) {
      return problem(
        400,
        'validation_failed',
        'responsibilityAccepted must be true',
        'Validation failed',
      );
    }
    if (!body.address?.trim()) {
      return HttpResponse.json(
        {
          type: 'https://posts.ru/problems/validation_failed',
          title: 'Validation failed',
          status: 400,
          detail: 'address is required when activating connection',
          code: 'validation_failed',
          instance: '/api/v1',
          errors: [{ field: 'address', message: 'address is required' }],
        } satisfies ProblemDetail,
        { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
      );
    }
    const conn = upsertOrdConnection(workspaceId, provider, body);
    return HttpResponse.json(toPublicConnection(conn));
  }),

  http.post('/api/v1/workspaces/:workspaceId/ord/connections/:provider/test', ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const provider = String(params.provider) as OrdProvider;
    const deniedWs = requireWorkspaceAccess(request, workspaceId);
    if (deniedWs) return deniedWs;
    const denied = requirePermission(request, 'ord.connection.manage');
    if (denied) return denied;
    return HttpResponse.json(testOrdConnection(workspaceId, provider));
  }),

  http.get('/api/v1/workspaces/:workspaceId/ord/contractors', ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const deniedWs = requireWorkspaceAccess(request, workspaceId);
    if (deniedWs) return deniedWs;
    const denied = requirePermission(request, 'ord.view');
    if (denied) return denied;
    return HttpResponse.json({
      items: listOrdContractors(workspaceId),
      hasMore: false,
    });
  }),

  http.post('/api/v1/workspaces/:workspaceId/ord/contractors', async ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const deniedWs = requireWorkspaceAccess(request, workspaceId);
    if (deniedWs) return deniedWs;
    const denied = requirePermission(request, 'ord.contractors.manage');
    if (denied) return denied;
    const body = (await request.json()) as CreateOrdContractorRequest;
    const result = createOrdContractor(workspaceId, body);
    if ('code' in result) {
      return problem(400, result.code, result.detail, 'Validation failed');
    }
    return HttpResponse.json(result, { status: 201 });
  }),

  http.get('/api/v1/workspaces/:workspaceId/ord/contractors/:contractorId', ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const deniedWs = requireWorkspaceAccess(request, workspaceId);
    if (deniedWs) return deniedWs;
    const denied = requirePermission(request, 'ord.view');
    if (denied) return denied;
    const item = getOrdContractor(workspaceId, String(params.contractorId));
    if (!item) return problem(404, 'not_found', 'Contractor not found', 'Not found');
    return HttpResponse.json(item);
  }),

  http.put(
    '/api/v1/workspaces/:workspaceId/ord/contractors/:contractorId',
    async ({ request, params }) => {
      const workspaceId = String(params.workspaceId);
      const deniedWs = requireWorkspaceAccess(request, workspaceId);
      if (deniedWs) return deniedWs;
      const denied = requirePermission(request, 'ord.contractors.manage');
      if (denied) return denied;
      const body = (await request.json()) as UpdateOrdContractorRequest;
      const result = updateOrdContractor(workspaceId, String(params.contractorId), body);
      if (!result) return problem(404, 'not_found', 'Contractor not found', 'Not found');
      if ('code' in result) {
        return problem(400, result.code, result.detail, 'Validation failed');
      }
      return HttpResponse.json(result);
    },
  ),

  http.get('/api/v1/workspaces/:workspaceId/ord/contracts', ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const deniedWs = requireWorkspaceAccess(request, workspaceId);
    if (deniedWs) return deniedWs;
    const denied = requirePermission(request, 'ord.view');
    if (denied) return denied;
    return HttpResponse.json({
      items: listOrdContracts(workspaceId),
      hasMore: false,
    });
  }),

  http.post('/api/v1/workspaces/:workspaceId/ord/contracts', async ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const deniedWs = requireWorkspaceAccess(request, workspaceId);
    if (deniedWs) return deniedWs;
    const denied = requirePermission(request, 'ord.contractors.manage');
    if (denied) return denied;
    const body = (await request.json()) as CreateOrdContractRequest;
    const created = createOrdContract(workspaceId, body);
    if (!created) {
      return problem(404, 'not_found', 'Contractor not found', 'Not found');
    }
    return HttpResponse.json(created, { status: 201 });
  }),

  http.post('/api/v1/posts/:postId/ord/register', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const denied = requirePermission(request, 'posts.ad.schedule');
    if (denied) return denied;
    const post = getPost(String(params.postId));
    if (!post) return problem(404, 'not_found', 'Post not found', 'Not found');
    const provider: OrdProvider = 'vk_ord';
    const ad = post.adPayload ?? {
      advertiserName: post.adMarking?.advertiserName ?? 'ООО Посты',
      inn: '7707083893',
      kktuCodes: ['1.1.1'],
      orgType: 'juridical' as const,
    };
    const result = registerCreative({
      workspaceId: post.workspaceId ?? '',
      postId: post.id ?? String(params.postId),
      provider,
      ad,
    });
    if ('code' in result) {
      const status = result.code === 'ord_not_configured' ? 422 : 400;
      return problem(status, result.code, result.detail, 'ORD error');
    }
    attachEridToPost(post.id ?? String(params.postId), result.erid ?? '', ad.advertiserName);
    return HttpResponse.json(result);
  }),

  http.get('/api/v1/posts/:postId/ord/status', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const denied = requirePermission(request, 'ord.view');
    if (denied) return denied;
    const creative = getCreativeForPost(String(params.postId));
    if (!creative) {
      return problem(404, 'not_found', 'Creative not found', 'Not found');
    }
    return HttpResponse.json(creative);
  }),

  http.get('/api/v1/posts', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required', 'Validation failed');
    }
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'posts.view');
    if (deniedView) return deniedView;

    const sort = url.searchParams.get('sort') as ListPostsFilters['sort'] | null;
    const contentKind = url.searchParams.get('contentKind');
    const page = listPosts({
      workspaceId,
      from: url.searchParams.get('from') ?? undefined,
      to: url.searchParams.get('to') ?? undefined,
      status: url.searchParams.get('status') ?? undefined,
      channelId: url.searchParams.get('channelId') ?? undefined,
      projectId: url.searchParams.get('projectId') ?? undefined,
      network: url.searchParams.get('network') ?? undefined,
      authorId: url.searchParams.get('authorId') ?? undefined,
      contentKind:
        contentKind === 'post' || contentKind === 'story' ? contentKind : undefined,
      q: url.searchParams.get('q') ?? undefined,
      cursor: url.searchParams.get('cursor') ?? undefined,
      limit: url.searchParams.get('limit')
        ? Number(url.searchParams.get('limit'))
        : undefined,
      sort: sort ?? undefined,
    });

    return HttpResponse.json({
      items: page.items.map(toApiPost),
      hasMore: page.hasMore,
      nextCursor: page.nextCursor,
    });
  }),

  http.post('/api/v1/posts/preview', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const body = (await request.json()) as PreviewRequest;
    if (!body.content || !body.network) {
      return problem(400, 'validation_failed', 'content and network are required', 'Validation failed');
    }
    const text = body.content.text ?? '';
    const invalid = invalidVkStringAttachments(body.content.stringAttachments);
    if (invalid.length) {
      return problem(
        400,
        'validation_failed',
        `Invalid VK string attachments: ${invalid.join(', ')}`,
        'Validation failed',
      );
    }
    const expandedText = renderEditorVariables(text.replace(/<[^>]+>/g, ''), {
      at: body.at,
      timezone: body.timezone,
      groupId: body.groupId,
      groupName: body.groupName,
      birthdays: body.birthdays,
      json: body.json as Record<string, unknown> | undefined,
      htmlNetwork: body.network === 'tg' || body.network === 'max',
      spintaxSeed: body.spintaxSeed ?? 'msw-preview',
    });
    return HttpResponse.json({
      network: body.network,
      expandedText,
      plainText: expandedText,
      html: `<p>${expandedText}</p>`,
      valid: true,
      issues: [],
      warnings: [],
    });
  }),

  http.post('/api/v1/posts/batch', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedBulk = requirePermission(request, 'posts.bulk');
    if (deniedBulk) return deniedBulk;
    const deniedSchedule = requirePermission(request, 'posts.schedule');
    if (deniedSchedule) return deniedSchedule;
    const body = (await request.json()) as BatchPostsRequest;
    if (!body.workspaceId || !body.channelIds?.length || !body.mediaIds?.length || !body.scheduledAt) {
      return problem(
        400,
        'validation_failed',
        'workspaceId, channelIds, mediaIds and scheduledAt are required',
        'Validation failed',
      );
    }
    const deniedWs = requireWorkspaceAccess(request, body.workspaceId);
    if (deniedWs) return deniedWs;
    if (body.mediaIds.length > 50) {
      return problem(422, 'post_batch_limit', 'Batch exceeds 50 posts', 'Batch limit');
    }
    const result = batchCreatePosts(body);
    return HttpResponse.json(result, { status: 201 });
  }),

  http.post('/api/v1/stories/batch', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedBulk = requirePermission(request, 'posts.bulk');
    if (deniedBulk) return deniedBulk;
    const deniedSchedule = requirePermission(request, 'posts.schedule');
    if (deniedSchedule) return deniedSchedule;
    const body = (await request.json()) as BatchStoriesRequest;
    if (!body.workspaceId || !body.channelIds?.length || !body.mediaIds?.length || !body.scheduledAt) {
      return problem(
        400,
        'validation_failed',
        'workspaceId, channelIds, mediaIds and scheduledAt are required',
        'Validation failed',
      );
    }
    const deniedWs = requireWorkspaceAccess(request, body.workspaceId);
    if (deniedWs) return deniedWs;
    if (body.mediaIds.length > 50) {
      return problem(422, 'story_batch_limit', 'Batch exceeds 50 stories', 'Batch limit');
    }
    for (const channelId of body.channelIds) {
      const channel = getChannel(channelId);
      if (!channel) {
        return problem(404, 'not_found', `Channel ${channelId} not found`, 'Not found');
      }
      if (channel.network !== 'vk' && channel.network !== 'ok') {
        return problem(
          422,
          'story_unsupported_network',
          `Stories are not supported for ${channel.network}`,
          'Unsupported network',
        );
      }
      if (channel.network === 'vk' && (channel.storyConnectionStatus ?? 'missing') !== 'ready') {
        return problem(
          422,
          'story_connection_required',
          'VK channel requires a separate stories connection',
          'Story connection required',
        );
      }
    }
    const result = batchCreateStories(body);
    return HttpResponse.json(result, { status: 201 });
  }),

  http.post('/api/v1/posts/bulk', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const denied = requirePermission(request, 'posts.bulk');
    if (denied) return denied;
    const body = (await request.json()) as BulkPostsRequest;
    if (!body.action || !body.postIds?.length) {
      return problem(400, 'validation_failed', 'action and postIds are required', 'Validation failed');
    }
    return HttpResponse.json(bulkPosts(body));
  }),

  http.get('/api/v1/posts/:postId', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedView = requirePermission(request, 'posts.view');
    if (deniedView) return deniedView;
    const post = getPost(String(params.postId));
    if (!post || post.status === 'archived') {
      return problem(404, 'not_found', 'Post not found', 'Not found');
    }
    return HttpResponse.json(toApiPost(post));
  }),

  http.delete('/api/v1/posts/:postId', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedDelete = requirePermission(request, 'posts.delete');
    if (deniedDelete) return deniedDelete;
    if (!deletePost(String(params.postId))) {
      return problem(404, 'not_found', 'Post not found', 'Not found');
    }
    return HttpResponse.json({ message: 'deleted' });
  }),

  http.post('/api/v1/posts/:postId/pause', async ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const denied = requirePermission(request, 'posts.schedule');
    if (denied) return denied;
    const body = (await request.json()) as PostActionRequest;
    const result = pausePost(String(params.postId), body.version);
    if (!result) return problem(404, 'not_found', 'Post not found', 'Not found');
    if ('conflict' in result) {
      return problem(409, 'optimistic_lock_conflict', 'Post version conflict', 'Conflict');
    }
    return HttpResponse.json(toApiPost(result));
  }),

  http.post('/api/v1/posts/:postId/resume', async ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const denied = requirePermission(request, 'posts.schedule');
    if (denied) return denied;
    const body = (await request.json()) as PostActionRequest;
    const result = resumePost(String(params.postId), body.version);
    if (!result) return problem(404, 'not_found', 'Post not found', 'Not found');
    if ('conflict' in result) {
      return problem(409, 'optimistic_lock_conflict', 'Post version conflict', 'Conflict');
    }
    return HttpResponse.json(toApiPost(result));
  }),

  http.post('/api/v1/posts/:postId/duplicate', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const denied = requirePermission(request, 'posts.create');
    if (denied) return denied;
    const copy = duplicatePost(String(params.postId));
    if (!copy) return problem(404, 'not_found', 'Post not found', 'Not found');
    return HttpResponse.json(toApiPost(copy), { status: 201 });
  }),

  http.get('/api/v1/posts/:postId/publications', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedView = requirePermission(request, 'posts.view');
    if (deniedView) return deniedView;
    const url = new URL(request.url);
    const page = listPublicationsForPost(
      String(params.postId),
      url.searchParams.get('cursor') ?? undefined,
      url.searchParams.get('limit') ? Number(url.searchParams.get('limit')) : 50,
    );
    return HttpResponse.json(page);
  }),

  http.get('/api/v1/publications/:publicationId', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedView = requirePermission(request, 'posts.view');
    if (deniedView) return deniedView;
    const pub = getPublication(String(params.publicationId));
    if (!pub) return problem(404, 'not_found', 'Publication not found', 'Not found');
    return HttpResponse.json(pub);
  }),

  http.get('/api/v1/publications/:publicationId/attempts', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const deniedView = requirePermission(request, 'posts.view');
    if (deniedView) return deniedView;
    const pub = getPublication(String(params.publicationId));
    if (!pub) return problem(404, 'not_found', 'Publication not found', 'Not found');
    return HttpResponse.json(listPublicationAttempts(String(params.publicationId)));
  }),

  http.post('/api/v1/publications/:publicationId/retry', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const denied = requirePermission(request, 'posts.retry_publish');
    if (denied) return denied;
    const result = retryPublication(String(params.publicationId));
    if (!result) {
      return problem(404, 'not_found', 'Publication not found or not retryable', 'Not found');
    }
    return HttpResponse.json(result);
  }),

  // --- Ingest / Grabber (FE-14) ---
  http.get('/api/v1/workspaces/:workspaceId/projects/:projectId/ingest/rules', ({ request, params }) => {
    const workspaceId = String(params.workspaceId);
    const denied = requireWorkspaceAccess(request, workspaceId);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'automations.view');
    if (deniedView) return deniedView;
    const url = new URL(request.url);
    const items = listRules({
      workspaceId,
      projectId: String(params.projectId),
      type: url.searchParams.get('type') ?? undefined,
    });
    return HttpResponse.json({ items, hasMore: false });
  }),

  http.post(
    '/api/v1/workspaces/:workspaceId/projects/:projectId/ingest/rules',
    async ({ request, params }) => {
      const workspaceId = String(params.workspaceId);
      const denied = requireWorkspaceAccess(request, workspaceId);
      if (denied) return denied;
      const deniedManage = requirePermission(request, 'automations.manage');
      if (deniedManage) return deniedManage;
      const body = (await request.json()) as GrabberRuleCreate;
      if (!body.name || !body.targetChannelId || !body.type) {
        return problem(400, 'validation_failed', 'name, type and targetChannelId are required');
      }
      const result = createRule(workspaceId, String(params.projectId), body);
      if (isGrabberError(result)) return grabberError(result);
      return HttpResponse.json(result, { status: 201 });
    },
  ),

  http.get('/api/v1/ingest/rules/:ruleId', ({ request, params }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'automations.view');
    if (deniedView) return deniedView;
    const rule = getRule(String(params.ruleId));
    if (!rule) return problem(404, 'not_found', 'Rule not found');
    return HttpResponse.json(rule);
  }),

  http.patch('/api/v1/ingest/rules/:ruleId', async ({ request, params }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedManage = requirePermission(request, 'automations.manage');
    if (deniedManage) return deniedManage;
    const body = (await request.json()) as GrabberRulePatch;
    const result = patchRule(String(params.ruleId), body);
    if (isGrabberError(result)) return grabberError(result);
    return HttpResponse.json(result);
  }),

  http.post('/api/v1/ingest/rules/:ruleId/activate', ({ request, params }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedManage = requirePermission(request, 'automations.manage');
    if (deniedManage) return deniedManage;
    const result = activateRule(String(params.ruleId));
    if (isGrabberError(result)) return grabberError(result);
    return HttpResponse.json(result);
  }),

  http.post('/api/v1/ingest/rules/:ruleId/pause', ({ request, params }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedManage = requirePermission(request, 'automations.manage');
    if (deniedManage) return deniedManage;
    const result = pauseRule(String(params.ruleId));
    if (isGrabberError(result)) return grabberError(result);
    return HttpResponse.json(result);
  }),

  http.post('/api/v1/ingest/rules/:ruleId/resume', ({ request, params }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedManage = requirePermission(request, 'automations.manage');
    if (deniedManage) return deniedManage;
    const result = resumeRule(String(params.ruleId));
    if (isGrabberError(result)) return grabberError(result);
    return HttpResponse.json(result);
  }),

  http.post('/api/v1/ingest/rules/:ruleId/archive', ({ request, params }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedManage = requirePermission(request, 'automations.manage');
    if (deniedManage) return deniedManage;
    const result = archiveRule(String(params.ruleId));
    if (isGrabberError(result)) return grabberError(result);
    return HttpResponse.json(result);
  }),

  http.get('/api/v1/ingest/rules/:ruleId/runs', ({ request, params }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'automations.view');
    if (deniedView) return deniedView;
    if (!getRule(String(params.ruleId))) return problem(404, 'not_found', 'Rule not found');
    return HttpResponse.json({ items: listRuns(String(params.ruleId)), hasMore: false });
  }),

  http.post('/api/v1/ingest/rules/:ruleId/runs', ({ request, params }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedManage = requirePermission(request, 'automations.manage');
    if (deniedManage) return deniedManage;
    const result = startRun(String(params.ruleId));
    if (isGrabberError(result)) return grabberError(result);
    return HttpResponse.json(result, { status: 202 });
  }),

  http.get('/api/v1/ingest/rules/:ruleId/items', ({ request, params }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'automations.view');
    if (deniedView) return deniedView;
    if (!getRule(String(params.ruleId))) return problem(404, 'not_found', 'Rule not found');
    const url = new URL(request.url);
    const status = (url.searchParams.get('status') as ItemStatus | null) ?? undefined;
    return HttpResponse.json({
      items: listItems(String(params.ruleId), status),
      hasMore: false,
    });
  }),

  http.get('/api/v1/ingest/items/:itemId', ({ request, params }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'automations.view');
    if (deniedView) return deniedView;
    const item = getItem(String(params.itemId));
    if (!item) return problem(404, 'not_found', 'Item not found');
    return HttpResponse.json(item);
  }),

  http.post('/api/v1/ingest/items/:itemId/approve', ({ request, params }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedModerate = requirePermission(request, 'automations.moderate');
    if (deniedModerate) return deniedModerate;
    const result = approveItem(String(params.itemId));
    if (isGrabberError(result)) return grabberError(result);
    return HttpResponse.json(result);
  }),

  http.post('/api/v1/ingest/items/:itemId/reject', async ({ request, params }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedModerate = requirePermission(request, 'automations.moderate');
    if (deniedModerate) return deniedModerate;
    const body = ((await request.json().catch(() => ({}))) ?? {}) as GrabberItemRejectRequest;
    const result = rejectItem(String(params.itemId), body.reason);
    if (isGrabberError(result)) return grabberError(result);
    return HttpResponse.json(result);
  }),

  http.post('/api/v1/ingest/items/:itemId/edit-and-approve', async ({ request, params }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedModerate = requirePermission(request, 'automations.moderate');
    if (deniedModerate) return deniedModerate;
    const body = (await request.json()) as GrabberItemEditApproveRequest;
    if (!body.contentText && body.contentText !== '') {
      return problem(400, 'validation_failed', 'contentText is required');
    }
    const result = editAndApproveItem(String(params.itemId), body);
    if (isGrabberError(result)) return grabberError(result);
    return HttpResponse.json(result);
  }),

  http.post('/api/v1/ingest/items/bulk', async ({ request }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedModerate = requirePermission(request, 'automations.moderate');
    if (deniedModerate) return deniedModerate;
    const body = (await request.json()) as GrabberBulkModerationRequest;
    if (!body.action || !body.itemIds?.length) {
      return problem(400, 'validation_failed', 'action and itemIds are required');
    }
    return HttpResponse.json(bulkModerate(body));
  }),

  http.post('/api/v1/ingest/preview', async ({ request }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'automations.view');
    if (deniedView) return deniedView;
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const body = (await request.json()) as IngestPreviewRequest;
    const result = previewSource(workspaceId, body);
    if (isGrabberError(result)) return grabberError(result);
    return HttpResponse.json(result);
  }),

  http.post('/api/v1/ingest/rules/:ruleId/clone-config', async ({ request, params }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedManage = requirePermission(request, 'automations.manage');
    if (deniedManage) return deniedManage;
    const body = (await request.json()) as { sourceRuleId?: string };
    if (!body.sourceRuleId) {
      return problem(400, 'validation_failed', 'sourceRuleId is required');
    }
    const result = cloneRuleConfig(String(params.ruleId), body.sourceRuleId);
    if (isGrabberError(result)) return grabberError(result);
    return HttpResponse.json(result);
  }),

  http.get('/api/v1/ingest/rules/:ruleId/items/export', ({ request, params }) => {
    const denied = requireBearer(request);
    if (denied) return denied;
    const deniedView = requirePermission(request, 'automations.view');
    if (deniedView) return deniedView;
    if (!getRule(String(params.ruleId))) return problem(404, 'not_found', 'Rule not found');
    const url = new URL(request.url);
    const status = (url.searchParams.get('status') as ItemStatus | null) ?? undefined;
    const format = url.searchParams.get('format') ?? 'csv';
    const csv = exportItemsCsv(String(params.ruleId), status);
    if (format === 'xlsx') {
      // MSW: serve CSV bytes with xlsx content-type (enough for FE download path).
      return new HttpResponse(csv, {
        status: 200,
        headers: {
          'Content-Type':
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          'Content-Disposition': `attachment; filename="grabber-${params.ruleId}.xlsx"`,
        },
      });
    }
    return new HttpResponse(csv, {
      status: 200,
      headers: {
        'Content-Type': 'text/csv; charset=utf-8',
        'Content-Disposition': `attachment; filename="grabber-${params.ruleId}.csv"`,
      },
    });
  }),

  // --- Notifications (FE-9 / BE-11) ---
  http.get('/api/v1/notifications', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const unreadOnly = url.searchParams.get('unreadOnly') === 'true';
    const cursor = url.searchParams.get('cursor') ?? undefined;
    const limitRaw = url.searchParams.get('limit');
    const limit = limitRaw ? Number(limitRaw) : 20;
    return HttpResponse.json(
      listNotifications({ workspaceId, unreadOnly, cursor, limit }),
    );
  }),

  http.put('/api/v1/notifications/read', async ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const body = ((await request.json().catch(() => ({}))) ?? {}) as MarkNotificationsReadRequest;
    return HttpResponse.json(markNotificationsRead(workspaceId, body));
  }),

  http.get('/api/v1/notifications/vapid-public-key', ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    return HttpResponse.json(getVapidPublicKey());
  }),

  http.get('/api/v1/notification-settings', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'notifications.manage_self');
    if (denied) return denied;
    return HttpResponse.json(getNotificationSettings(workspaceId));
  }),

  http.put('/api/v1/notification-settings', async ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'notifications.manage_self');
    if (denied) return denied;
    const body = (await request.json()) as UpdateNotificationSettingsRequest;
    return HttpResponse.json(updateNotificationSettings(workspaceId, body));
  }),

  http.post('/api/v1/notification-settings/telegram/link', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'notifications.manage_self');
    if (denied) return denied;
    return HttpResponse.json(startTelegramLink(workspaceId));
  }),

  http.delete('/api/v1/notification-settings/telegram', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'notifications.manage_self');
    if (denied) return denied;
    return HttpResponse.json(unlinkTelegram(workspaceId));
  }),

  http.post('/api/v1/notification-settings/web-push', async ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId is required');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'notifications.manage_self');
    if (denied) return denied;
    const body = (await request.json()) as WebPushSubscribeRequest;
    if (!body.endpoint || !body.p256dh || !body.auth) {
      return problem(400, 'validation_failed', 'endpoint, p256dh and auth are required');
    }
    return HttpResponse.json(subscribeWebPush(workspaceId, body));
  }),

  http.delete('/api/v1/notification-settings/web-push', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    const endpoint = url.searchParams.get('endpoint');
    if (!workspaceId || !endpoint) {
      return problem(400, 'validation_failed', 'workspaceId and endpoint are required');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'notifications.manage_self');
    if (denied) return denied;
    return HttpResponse.json(unsubscribeWebPush(workspaceId, endpoint));
  }),

  // --- Billing (FE-10b / BE-12b / BE-31: wallet + daily charge) ---
  http.get('/api/v1/billing/plans', ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    return HttpResponse.json(listPlans());
  }),

  http.get('/api/v1/billing/wallet', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId required', 'Validation failed');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'billing.view');
    if (denied) return denied;
    return HttpResponse.json(getWallet(workspaceId));
  }),

  http.get('/api/v1/billing/subscription', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId required', 'Validation failed');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'billing.view');
    if (denied) return denied;
    const wallet = getWallet(workspaceId);
    return HttpResponse.json({
      id: workspaceId,
      workspaceId,
      planCode: wallet.planCode,
      status: wallet.billingStatus,
      billingModel: 'balance',
      cancelAtPeriodEnd: false,
    });
  }),

  http.post('/api/v1/billing/checkout', async ({ request }) => {
    const body = (await request.json()) as {
      workspaceId?: string;
      amountKopecks?: number;
      planCode?: string;
    };
    if (!body.workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId required', 'Validation failed');
    }
    const access = requireWorkspaceAccess(request, body.workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'billing.manage');
    if (denied) return denied;
    if (body.amountKopecks == null || body.amountKopecks <= 0) {
      return problem(
        400,
        'validation_failed',
        'Provide amountKopecks for top-up',
        'Validation failed',
      );
    }
    try {
      return HttpResponse.json(checkoutTopup(body.workspaceId, body.amountKopecks));
    } catch (error) {
      return problem(
        400,
        (error as { code?: string }).code ?? 'validation_failed',
        error instanceof Error ? error.message : 'Checkout failed',
        'Validation failed',
      );
    }
  }),

  http.get('/api/v1/billing/transactions', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId required', 'Validation failed');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'billing.view');
    if (denied) return denied;
    const kind = url.searchParams.get('kind');
    const cursor = url.searchParams.get('cursor');
    const limit = Number.parseInt(url.searchParams.get('limit') ?? '20', 10) || 20;
    return HttpResponse.json(listTransactions(workspaceId, { kind, cursor, limit }));
  }),

  http.get('/api/v1/billing/payments', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId required', 'Validation failed');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'billing.view');
    if (denied) return denied;
    const cursor = url.searchParams.get('cursor');
    const limit = Number.parseInt(url.searchParams.get('limit') ?? '20', 10) || 20;
    return HttpResponse.json(listPayments(workspaceId, { cursor, limit }));
  }),

  http.get('/api/v1/billing/invoices', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId required', 'Validation failed');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'billing.view');
    if (denied) return denied;
    const cursor = url.searchParams.get('cursor');
    const limit = Number.parseInt(url.searchParams.get('limit') ?? '20', 10) || 20;
    return HttpResponse.json(listInvoices(workspaceId, { cursor, limit }));
  }),

  http.post('/api/v1/billing/invoices/request', async ({ request }) => {
    const body = (await request.json()) as {
      workspaceId?: string;
      amountKopecks?: number;
      buyer?: Record<string, string>;
    };
    if (!body.workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId required', 'Validation failed');
    }
    const access = requireWorkspaceAccess(request, body.workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'billing.manage');
    if (denied) return denied;
    if (!body.amountKopecks || body.amountKopecks < 100 || !body.buyer) {
      return problem(400, 'validation_failed', 'Invalid invoice request', 'Validation failed');
    }
    return HttpResponse.json(requestInvoice(body.workspaceId, body.amountKopecks, body.buyer));
  }),

  http.get('/api/v1/billing/invoices/:id/pdf', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const invoice = getInvoice(String(params.id));
    if (!invoice?.workspaceId) {
      return problem(404, 'not_found', 'Invoice not found', 'Not found');
    }
    const access = requireWorkspaceAccess(request, invoice.workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'billing.view');
    if (denied) return denied;
    const bytes = mockPdfBytes(`invoice-${invoice.id}`);
    return new HttpResponse(bytes.buffer as ArrayBuffer, {
      status: 200,
      headers: {
        'Content-Type': 'application/pdf',
        'Content-Disposition': `attachment; filename="invoice-${invoice.id}.pdf"`,
      },
    });
  }),

  http.get('/api/v1/billing/invoices/:id/act', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const invoice = getInvoice(String(params.id));
    if (!invoice?.workspaceId) {
      return problem(404, 'not_found', 'Invoice not found', 'Not found');
    }
    const access = requireWorkspaceAccess(request, invoice.workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'billing.view');
    if (denied) return denied;
    const bytes = mockPdfBytes(`act-${invoice.id}`);
    return new HttpResponse(bytes.buffer as ArrayBuffer, {
      status: 200,
      headers: {
        'Content-Type': 'application/pdf',
        'Content-Disposition': `attachment; filename="act-${invoice.id}.pdf"`,
      },
    });
  }),

  http.get('/api/v1/billing/autopay', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId required', 'Validation failed');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'billing.view');
    if (denied) return denied;
    return HttpResponse.json(getAutopay(workspaceId));
  }),

  http.put('/api/v1/billing/autopay', async ({ request }) => {
    const body = (await request.json()) as {
      workspaceId?: string;
      enabled?: boolean;
      thresholdKopecks?: number;
      topupAmountKopecks?: number;
    };
    if (!body.workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId required', 'Validation failed');
    }
    const access = requireWorkspaceAccess(request, body.workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'billing.manage');
    if (denied) return denied;
    try {
      return HttpResponse.json(
        updateAutopay(body.workspaceId, {
          enabled: Boolean(body.enabled),
          thresholdKopecks: body.thresholdKopecks,
          topupAmountKopecks: body.topupAmountKopecks,
        }),
      );
    } catch (error) {
      return problem(
        400,
        (error as { code?: string }).code ?? 'validation_failed',
        error instanceof Error ? error.message : 'Autopay update failed',
        'Validation failed',
      );
    }
  }),

  http.get('/api/v1/billing/payment-method', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId required', 'Validation failed');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'billing.view');
    if (denied) return denied;
    const method = getPaymentMethod(workspaceId);
    if (!method) {
      return new HttpResponse(null, { status: 204 });
    }
    return HttpResponse.json(method);
  }),

  http.delete('/api/v1/billing/payment-method', ({ request }) => {
    const url = new URL(request.url);
    const workspaceId = url.searchParams.get('workspaceId');
    if (!workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId required', 'Validation failed');
    }
    const access = requireWorkspaceAccess(request, workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'billing.manage');
    if (denied) return denied;
    deletePaymentMethod(workspaceId);
    return new HttpResponse(null, { status: 204 });
  }),

  http.post('/api/v1/billing/plan/change', async ({ request }) => {
    const body = (await request.json()) as { workspaceId?: string; planCode?: string };
    if (!body.workspaceId) {
      return problem(400, 'validation_failed', 'workspaceId required', 'Validation failed');
    }
    const access = requireWorkspaceAccess(request, body.workspaceId);
    if (access) return access;
    const denied = requirePermission(request, 'billing.manage');
    if (denied) return denied;
    if (!body.planCode) {
      return problem(400, 'validation_failed', 'planCode required', 'Validation failed');
    }
    try {
      return HttpResponse.json(changePlan(body.workspaceId, body.planCode));
    } catch (error) {
      return problem(
        404,
        (error as { code?: string }).code ?? 'not_found',
        error instanceof Error ? error.message : 'Plan not found',
        'Not found',
      );
    }
  }),

  /** Mock-only: complete YooKassa payment after redirect (e2e / local). */
  http.post('/api/v1/billing/mock/complete-payment', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    const body = (await request.json()) as {
      paymentId?: string;
      outcome?: 'succeeded' | 'canceled' | 'pending';
    };
    if (!body.paymentId) {
      return problem(400, 'validation_failed', 'paymentId required', 'Validation failed');
    }
    const payment = getPayment(body.paymentId);
    if (!payment) {
      return problem(404, 'not_found', 'Payment not found', 'Not found');
    }
    const updated = completeMockPayment(body.paymentId, body.outcome ?? 'succeeded');
    return HttpResponse.json(updated);
  }),

  // --- Platform admin (BE-13 / A13) ---
  http.get('/api/v1/admin/users', ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const url = new URL(request.url);
    return HttpResponse.json(
      searchUsers({
        q: url.searchParams.get('q'),
        platformAdmin: url.searchParams.get('platformAdmin'),
        cursor: url.searchParams.get('cursor'),
        limit: url.searchParams.get('limit'),
      }),
    );
  }),

  http.get('/api/v1/admin/users/:userId', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const card = getUserCard(String(params.userId));
    if (!card) return problem(404, 'not_found', 'User not found', 'Not found');
    return HttpResponse.json(card);
  }),

  http.post('/api/v1/admin/users/:userId/impersonate', async ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const body = (await request.json()) as { reason?: string; workspaceId?: string };
    if (!body.reason || body.reason.trim().length < 3) {
      return problem(400, 'validation_failed', 'reason is required (min 3)', 'Validation failed');
    }
    const result = impersonate(String(params.userId), {
      reason: body.reason.trim(),
      workspaceId: body.workspaceId,
    });
    if ('error' in result) {
      return problem(result.status, result.code, result.error, result.error);
    }
    return HttpResponse.json(result);
  }),

  http.get('/api/v1/admin/workspaces', ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const url = new URL(request.url);
    return HttpResponse.json(
      searchWorkspaces({
        q: url.searchParams.get('q'),
        planCode: url.searchParams.get('planCode'),
        billingStatus: url.searchParams.get('billingStatus'),
        cursor: url.searchParams.get('cursor'),
        limit: url.searchParams.get('limit'),
      }),
    );
  }),

  http.get('/api/v1/admin/workspaces/:workspaceId', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const card = getWorkspaceCard(String(params.workspaceId));
    if (!card) return problem(404, 'not_found', 'Workspace not found', 'Not found');
    return HttpResponse.json(card);
  }),

  http.post('/api/v1/admin/workspaces/:workspaceId/subscription', async ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const body = (await request.json()) as {
      planCode?: string;
      reason?: string;
      billingStatus?: 'trial' | 'active' | 'grace' | 'frozen';
      periodEnd?: string;
      trialEndsAt?: string;
    };
    if (!body.planCode || !body.reason) {
      return problem(400, 'validation_failed', 'planCode and reason required', 'Validation failed');
    }
    const card = adjustSubscription(String(params.workspaceId), {
      planCode: body.planCode,
      reason: body.reason,
      billingStatus: body.billingStatus,
      periodEnd: body.periodEnd,
      trialEndsAt: body.trialEndsAt,
    });
    if (!card) return problem(404, 'not_found', 'Workspace not found', 'Not found');
    return HttpResponse.json(card);
  }),

  http.post('/api/v1/admin/workspaces/:workspaceId/balance', async ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const body = (await request.json()) as { amount?: number; reason?: string; kind?: string };
    if (typeof body.amount !== 'number' || !body.reason) {
      return problem(400, 'validation_failed', 'amount and reason required', 'Validation failed');
    }
    const card = adjustBalance(String(params.workspaceId), {
      amount: body.amount,
      reason: body.reason,
      kind: body.kind,
    });
    if (!card) return problem(404, 'not_found', 'Workspace not found', 'Not found');
    return HttpResponse.json(card);
  }),

  http.put(
    '/api/v1/admin/workspaces/:workspaceId/feature-flags/:flagKey',
    async ({ request, params }) => {
      const unauthorized = requireBearer(request);
      if (unauthorized) return unauthorized;
      if (!requirePlatformAdmin(request)) {
        return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
      }
      const body = (await request.json()) as { enabled?: boolean };
      if (typeof body.enabled !== 'boolean') {
        return problem(400, 'validation_failed', 'enabled is required', 'Validation failed');
      }
      const flag = setWorkspaceFlag(String(params.workspaceId), String(params.flagKey), body.enabled);
      if (!flag) return problem(404, 'not_found', 'Workspace not found', 'Not found');
      return HttpResponse.json(flag);
    },
  ),

  http.get('/api/v1/admin/plans', ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    return HttpResponse.json(listAdminPlansFromStore());
  }),

  http.put('/api/v1/admin/plans', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const body = (await request.json()) as {
      code?: string;
      name?: string;
      priceMonth?: number;
      priceYear?: number;
      limits?: Record<string, number | null>;
    };
    if (!body.code || !body.name || body.priceMonth == null || body.priceYear == null || !body.limits) {
      return problem(400, 'validation_failed', 'plan fields required', 'Validation failed');
    }
    return HttpResponse.json(
      upsertAdminPlanInStore({
        code: body.code,
        name: body.name,
        priceMonth: body.priceMonth,
        priceYear: body.priceYear,
        limits: body.limits,
      }),
    );
  }),

  http.get('/api/v1/admin/promos', ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const url = new URL(request.url);
    return HttpResponse.json(
      listPromos({
        cursor: url.searchParams.get('cursor'),
        limit: url.searchParams.get('limit'),
      }),
    );
  }),

  http.post('/api/v1/admin/promos', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const body = (await request.json()) as {
      code?: string;
      kind?: 'percent' | 'fixed' | 'trial_days';
      value?: number;
      validFrom?: string;
      validUntil?: string;
      active?: boolean;
      maxRedemptions?: number;
      oncePerWorkspace?: boolean;
      stackable?: boolean;
    };
    if (!body.code || !body.kind || body.value == null || !body.validFrom || !body.validUntil) {
      return problem(400, 'validation_failed', 'promo fields required', 'Validation failed');
    }
    return HttpResponse.json(
      createPromo({
        code: body.code,
        kind: body.kind,
        value: body.value,
        validFrom: body.validFrom,
        validUntil: body.validUntil,
        active: body.active,
        maxRedemptions: body.maxRedemptions,
        oncePerWorkspace: body.oncePerWorkspace,
        stackable: body.stackable,
      }),
    );
  }),

  http.put('/api/v1/admin/promos/:promoId', async ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const body = (await request.json()) as {
      active?: boolean;
      maxRedemptions?: number;
      oncePerWorkspace?: boolean;
      stackable?: boolean;
      validFrom?: string;
      validUntil?: string;
    };
    const promo = updatePromo(String(params.promoId), body);
    if (!promo) return problem(404, 'not_found', 'Promo not found', 'Not found');
    return HttpResponse.json(promo);
  }),

  http.get('/api/v1/admin/queues', ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    return HttpResponse.json(listQueues());
  }),

  http.get('/api/v1/admin/queues/:queue/messages', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const url = new URL(request.url);
    return HttpResponse.json(
      listQueueMessages(String(params.queue), {
        cursor: url.searchParams.get('cursor'),
        limit: url.searchParams.get('limit'),
      }),
    );
  }),

  http.post('/api/v1/admin/queues/:queue/messages/:messageId/retry', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    if (!request.headers.get('Idempotency-Key')) {
      return problem(400, 'validation_failed', 'Idempotency-Key required', 'Validation failed');
    }
    const result = retryQueueMessage(String(params.queue), String(params.messageId));
    if (!result) return problem(404, 'not_found', 'Message not found', 'Not found');
    return HttpResponse.json(result);
  }),

  http.post('/api/v1/admin/queues/:queue/messages/:messageId/delete', ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    if (!request.headers.get('Idempotency-Key')) {
      return problem(400, 'validation_failed', 'Idempotency-Key required', 'Validation failed');
    }
    const result = deleteQueueMessage(String(params.queue), String(params.messageId));
    if (!result) return problem(404, 'not_found', 'Message not found', 'Not found');
    return HttpResponse.json(result);
  }),

  http.get('/api/v1/admin/feature-flags', ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    return HttpResponse.json(listFeatureFlags());
  }),

  http.put('/api/v1/admin/feature-flags', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const body = (await request.json()) as {
      key?: string;
      description?: string;
      enabledGlobal?: boolean;
    };
    if (!body.key) {
      return problem(400, 'validation_failed', 'key is required', 'Validation failed');
    }
    return HttpResponse.json(
      upsertFeatureFlag({
        key: body.key,
        description: body.description,
        enabledGlobal: body.enabledGlobal,
      }),
    );
  }),

  http.get('/api/v1/admin/announcements', ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const url = new URL(request.url);
    return HttpResponse.json(
      listAnnouncements({
        cursor: url.searchParams.get('cursor'),
        limit: url.searchParams.get('limit'),
      }),
    );
  }),

  http.post('/api/v1/admin/announcements', async ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const body = (await request.json()) as {
      title?: string;
      body?: string;
      severity?: 'info' | 'warning' | 'critical';
      active?: boolean;
      startsAt?: string;
      endsAt?: string;
    };
    if (!body.title || !body.body || !body.severity) {
      return problem(400, 'validation_failed', 'title, body, severity required', 'Validation failed');
    }
    return HttpResponse.json(
      createAnnouncement({
        title: body.title,
        body: body.body,
        severity: body.severity,
        active: body.active,
        startsAt: body.startsAt,
        endsAt: body.endsAt,
      }),
    );
  }),

  http.put('/api/v1/admin/announcements/:announcementId', async ({ request, params }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const body = (await request.json()) as {
      title?: string;
      body?: string;
      severity?: 'info' | 'warning' | 'critical';
      active?: boolean;
      startsAt?: string;
      endsAt?: string;
    };
    const item = updateAnnouncement(String(params.announcementId), body);
    if (!item) return problem(404, 'not_found', 'Announcement not found', 'Not found');
    return HttpResponse.json(item);
  }),

  http.get('/api/v1/admin/audit', ({ request }) => {
    const unauthorized = requireBearer(request);
    if (unauthorized) return unauthorized;
    if (!requirePlatformAdmin(request)) {
      return problem(403, 'forbidden', 'Platform admin required', 'Forbidden');
    }
    const url = new URL(request.url);
    return HttpResponse.json(
      listAdminAuditLog({
        actorUserId: url.searchParams.get('actorUserId'),
        action: url.searchParams.get('action'),
        targetType: url.searchParams.get('targetType'),
        targetId: url.searchParams.get('targetId'),
        cursor: url.searchParams.get('cursor'),
        limit: url.searchParams.get('limit'),
      }),
    );
  }),
];
