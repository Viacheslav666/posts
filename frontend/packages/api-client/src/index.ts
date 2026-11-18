import createClient, { type ClientOptions, type Middleware } from 'openapi-fetch';

import type { components, paths } from './schema';

export type { components, paths };
export type { ClientOptions, Middleware };

export type ProblemDetail = components['schemas']['ProblemDetail'];
export type TokenResponse = components['schemas']['TokenResponse'];
export type MessageResponse = components['schemas']['MessageResponse'];
export type UserProfile = components['schemas']['UserProfile'];
export type LoginRequest = components['schemas']['LoginRequest'];
export type RegisterRequest = components['schemas']['RegisterRequest'];
export type EmailVerifyRequest = components['schemas']['EmailVerifyRequest'];
export type PasswordForgotRequest = components['schemas']['PasswordForgotRequest'];
export type PasswordResetRequest = components['schemas']['PasswordResetRequest'];
export type RefreshRequest = components['schemas']['RefreshRequest'];
export type OAuthStartResponse = components['schemas']['OAuthStartResponse'];
export type LogoutRequest = components['schemas']['LogoutRequest'];

export type Workspace = components['schemas']['Workspace'];
export type WorkspacePage = components['schemas']['WorkspacePage'];
export type WorkspaceMember = components['schemas']['WorkspaceMember'];
export type WorkspaceMemberPage = components['schemas']['WorkspaceMemberPage'];
export type CreateWorkspaceRequest = components['schemas']['CreateWorkspaceRequest'];
export type UpdateWorkspaceRequest = components['schemas']['UpdateWorkspaceRequest'];
export type Project = components['schemas']['Project'];
export type ProjectPage = components['schemas']['ProjectPage'];
export type CreateProjectRequest = components['schemas']['CreateProjectRequest'];
export type UpdateProjectRequest = components['schemas']['UpdateProjectRequest'];
export type ProjectMembersRequest = components['schemas']['ProjectMembersRequest'];
export type Invitation = components['schemas']['Invitation'];
export type InvitationPage = components['schemas']['InvitationPage'];
export type CreateInvitationRequest = components['schemas']['CreateInvitationRequest'];
export type AcceptInvitationRequest = components['schemas']['AcceptInvitationRequest'];
export type AuditLogEntry = components['schemas']['AuditLogEntry'];
export type AuditLogPage = components['schemas']['AuditLogPage'];
export type UpdateMemberRequest = components['schemas']['UpdateMemberRequest'];
export type MembershipSummary = components['schemas']['MembershipSummary'];

export type Channel = components['schemas']['Channel'];
export type ChannelPage = components['schemas']['ChannelPage'];
export type ChannelSet = components['schemas']['ChannelSet'];
export type ChannelSetPage = components['schemas']['ChannelSetPage'];
export type ChannelCheckResponse = components['schemas']['ChannelCheckResponse'];
export type ChannelConnectStartResponse = components['schemas']['ChannelConnectStartResponse'];
export type ChannelConnectCallbackRequest = components['schemas']['ChannelConnectCallbackRequest'];
export type ChannelConnectConfirmRequest = components['schemas']['ChannelConnectConfirmRequest'];
export type CreateChannelSetRequest = components['schemas']['CreateChannelSetRequest'];
export type UpdateChannelSetRequest = components['schemas']['UpdateChannelSetRequest'];
export type UpdateChannelRequest = components['schemas']['UpdateChannelRequest'];

export type MediaAsset = components['schemas']['MediaAsset'];
export type MediaPage = components['schemas']['MediaPage'];
export type MediaLimits = components['schemas']['MediaLimits'];
export type CreateUploadRequest = components['schemas']['CreateUploadRequest'];
export type CreateUploadResponse = components['schemas']['CreateUploadResponse'];
export type CompleteUploadRequest = components['schemas']['CompleteUploadRequest'];

export type Post = components['schemas']['Post'];
export type PostPage = components['schemas']['PostPage'];
export type PostActionRequest = components['schemas']['PostActionRequest'];
export type CalendarItem = components['schemas']['CalendarItem'];
export type MoveCalendarItemRequest = components['schemas']['MoveCalendarItemRequest'];
export type BulkPostsRequest = components['schemas']['BulkPostsRequest'];
export type BulkPostsResponse = components['schemas']['BulkPostsResponse'];
export type Publication = components['schemas']['Publication'];
export type PublicationAttempt = components['schemas']['PublicationAttempt'];
export type PublicationPage = components['schemas']['PublicationPage'];
export type AdMarkingSummary = components['schemas']['AdMarkingSummary'];
export type CreatePostRequest = components['schemas']['CreatePostRequest'];

export type PostsApiClient = ReturnType<typeof createPostsApiClient>;

export type CreatePostsApiClientOptions = ClientOptions & {
  /** Absolute or relative API origin; empty string uses same-origin `/api/v1/...`. */
  baseUrl?: string;
};

/**
 * Typed OpenAPI client (`openapi-fetch` + generated `paths`).
 * Auth headers, cookie credentials and ProblemDetail mapping live in the web app wrapper.
 */
export function createPostsApiClient(options: CreatePostsApiClientOptions = {}) {
  const { baseUrl = '', ...rest } = options;
  return createClient<paths>({
    baseUrl,
    ...rest,
  });
}
