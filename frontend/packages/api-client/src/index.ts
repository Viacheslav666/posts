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
export type EventSubscriptionResponse = components['schemas']['EventSubscriptionResponse'];

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
export type AdMarking = components['schemas']['AdMarking'];
export type AdMarkingSummary = components['schemas']['AdMarkingSummary'];
export type CreatePostRequest = components['schemas']['CreatePostRequest'];
export type UpdatePostRequest = components['schemas']['UpdatePostRequest'];
export type PostVariantOptions = components['schemas']['PostVariantOptions'];
export type PollOptions = components['schemas']['PollOptions'];
export type Recurrence = components['schemas']['Recurrence'];
export type RepostTarget = components['schemas']['RepostTarget'];
export type GeoPoint = components['schemas']['GeoPoint'];
export type InlineButton = components['schemas']['InlineButton'];

export type StoryOptions = components['schemas']['StoryOptions'];
export type StoryConnection = components['schemas']['StoryConnection'];
export type StartStoryConnectionRequest = components['schemas']['StartStoryConnectionRequest'];
export type BatchStoriesRequest = components['schemas']['BatchStoriesRequest'];
export type BatchStoriesResponse = components['schemas']['BatchStoriesResponse'];
export type BatchStoryFailure = components['schemas']['BatchStoryFailure'];
export type BatchPostsRequest = components['schemas']['BatchPostsRequest'];
export type BatchPostsResponse = components['schemas']['BatchPostsResponse'];
export type BatchPostFailure = components['schemas']['BatchPostFailure'];
export type PreviewRequest = components['schemas']['PreviewRequest'];
export type PreviewResponse = components['schemas']['PreviewResponse'];
export type StoryTtl = NonNullable<StoryOptions['ttl']>;
export type StoryConnectionStatus = NonNullable<StoryConnection['status']>;

export type OrdConnection = components['schemas']['OrdConnection'];
export type OrdConnectionPage = components['schemas']['OrdConnectionPage'];
export type OrdConnectionTestResponse = components['schemas']['OrdConnectionTestResponse'];
export type UpsertOrdConnectionRequest = components['schemas']['UpsertOrdConnectionRequest'];
export type OrdContractor = components['schemas']['OrdContractor'];
export type OrdContractorPage = components['schemas']['OrdContractorPage'];
export type CreateOrdContractorRequest = components['schemas']['CreateOrdContractorRequest'];
export type UpdateOrdContractorRequest = components['schemas']['UpdateOrdContractorRequest'];
export type OrdContract = components['schemas']['OrdContract'];
export type OrdContractPage = components['schemas']['OrdContractPage'];
export type CreateOrdContractRequest = components['schemas']['CreateOrdContractRequest'];
export type OrdCreativeStatus = components['schemas']['OrdCreativeStatus'];
export type OrdRegisterCreativeResponse = components['schemas']['OrdRegisterCreativeResponse'];
export type OrdReportRunRequest = components['schemas']['OrdReportRunRequest'];
export type OrdReportRunResponse = components['schemas']['OrdReportRunResponse'];
export type OrdReportRow = components['schemas']['OrdReportRow'];
export type OrdReportRowPage = components['schemas']['OrdReportRowPage'];
export type OrdEridCheckRequest = components['schemas']['OrdEridCheckRequest'];
export type OrdEridCheckResponse = components['schemas']['OrdEridCheckResponse'];
export type OrdProvider = NonNullable<OrdConnection['provider']>;
export type OrdVatCalculateRequest = components['schemas']['OrdVatCalculateRequest'];
export type VatBreakdown = components['schemas']['VatBreakdown'];
export type AdMarkingPlacement = NonNullable<AdMarking['placement']>;
export type AdMarkingVatRate = NonNullable<AdMarking['vatRate']>;
export type AdMarkingCustomerRole = NonNullable<AdMarking['customerRole']>;

export type GrabberConfig = components['schemas']['GrabberConfig'];
export type GrabberCloneConfigRequest = components['schemas']['GrabberCloneConfigRequest'];
export type GrabberRule = components['schemas']['GrabberRule'];
export type GrabberRuleCreate = components['schemas']['GrabberRuleCreate'];
export type GrabberRulePatch = components['schemas']['GrabberRulePatch'];
export type GrabberRulePage = components['schemas']['GrabberRulePage'];
export type GrabberRun = components['schemas']['GrabberRun'];
export type GrabberRunPage = components['schemas']['GrabberRunPage'];
export type GrabberItem = components['schemas']['GrabberItem'];
export type GrabberItemPage = components['schemas']['GrabberItemPage'];
export type GrabberItemApproveRequest = components['schemas']['GrabberItemApproveRequest'];
export type GrabberItemRejectRequest = components['schemas']['GrabberItemRejectRequest'];
export type GrabberItemEditApproveRequest = components['schemas']['GrabberItemEditApproveRequest'];
export type GrabberBulkModerationRequest = components['schemas']['GrabberBulkModerationRequest'];
export type GrabberBulkModerationResponse = components['schemas']['GrabberBulkModerationResponse'];
export type IngestPreviewRequest = components['schemas']['IngestPreviewRequest'];
export type IngestPreviewResponse = components['schemas']['IngestPreviewResponse'];

/** VK Suggest automation (BE-34 / FE-22) — BulkJob/Sync/CopyResult after springdoc @ApiResponse */
export type SuggestSettings = components['schemas']['SuggestSettings'];
export type SuggestSettingsPage = components['schemas']['SuggestSettingsPage'];
export type SuggestSettingsCreate = components['schemas']['SuggestSettingsCreate'];
export type SuggestSettingsPatch = components['schemas']['SuggestSettingsPatch'];
export type SuggestSettingsCopyRequest = components['schemas']['SuggestSettingsCopyRequest'];
export type SuggestSettingsCopyResult = components['schemas']['SuggestSettingsCopyResult'];
export type SuggestSettingsCopyItem = components['schemas']['SuggestSettingsCopyItem'];
export type SuggestItem = components['schemas']['SuggestItem'];
export type SuggestItemPage = components['schemas']['SuggestItemPage'];
export type SuggestItemApproveRequest = components['schemas']['SuggestItemApproveRequest'];
export type SuggestItemRejectRequest = components['schemas']['SuggestItemRejectRequest'];
export type SuggestBulkRequest = components['schemas']['SuggestBulkRequest'];
export type SuggestBulkJob = components['schemas']['SuggestBulkJob'];
export type SuggestSyncResult = components['schemas']['SuggestSyncResult'];
export type SuggestMode = NonNullable<SuggestSettings['mode']>;
export type SuggestSettingsStatus = NonNullable<SuggestSettings['status']>;

export type CrosspostRule = components['schemas']['CrosspostRule'];
export type CrosspostRuleCreate = components['schemas']['CrosspostRuleCreate'];
export type CrosspostRulePatch = components['schemas']['CrosspostRulePatch'];
export type CrosspostRuleList = components['schemas']['CrosspostRuleList'];
export type CrosspostTarget = components['schemas']['CrosspostTarget'];
export type CrosspostTargetRequest = components['schemas']['CrosspostTargetRequest'];
export type CrosspostWarning = components['schemas']['CrosspostWarning'];
export type CrosspostItem = components['schemas']['CrosspostItem'];
export type CrosspostItemPage = components['schemas']['CrosspostItemPage'];
/** Status enum is inlined on CrosspostItem by springdoc (no separate schema). */
export type CrosspostItemStatus = NonNullable<CrosspostItem['status']>;
export type CrosspostPreviewRequest = components['schemas']['CrosspostPreviewRequest'];
export type CrosspostPreviewResponse = components['schemas']['CrosspostPreviewResponse'];
export type CrosspostPreviewTarget = components['schemas']['CrosspostPreviewTarget'];

export type Notification = components['schemas']['Notification'];
export type NotificationPage = components['schemas']['NotificationPage'];
export type NotificationSettings = components['schemas']['NotificationSettings'];
export type UpdateNotificationSettingsRequest =
  components['schemas']['UpdateNotificationSettingsRequest'];
export type MarkNotificationsReadRequest = components['schemas']['MarkNotificationsReadRequest'];
export type ChannelPreference = components['schemas']['ChannelPreference'];
export type QuietHours = components['schemas']['QuietHours'];
export type TelegramLinkStatus = components['schemas']['TelegramLinkStatus'];
export type TelegramLinkStartResponse = components['schemas']['TelegramLinkStartResponse'];
export type WebPushSubscribeRequest = components['schemas']['WebPushSubscribeRequest'];
export type VapidPublicKeyResponse = components['schemas']['VapidPublicKeyResponse'];
export type UnsubscribeResponse = components['schemas']['UnsubscribeResponse'];
export type UpdateMeRequest = components['schemas']['UpdateMeRequest'];
export type UpdatePasswordRequest = components['schemas']['UpdatePasswordRequest'];
export type SessionDto = components['schemas']['SessionDto'];
export type TwoFactorSetupResponse = components['schemas']['TwoFactorSetupResponse'];
export type TwoFactorConfirmRequest = components['schemas']['TwoFactorConfirmRequest'];
export type TwoFactorDisableRequest = components['schemas']['TwoFactorDisableRequest'];

/** Analytics (BE-30 / FE-19) */
export type PostsAnalyticsResponse = components['schemas']['PostsAnalyticsResponse'];
export type PostsAnalyticsMonth = components['schemas']['PostsAnalyticsMonth'];
export type NetworkPublicationStats = components['schemas']['NetworkPublicationStats'];
export type AdRevenue = components['schemas']['AdRevenue'];
export type TopPublicationError = components['schemas']['TopPublicationError'];

/** Billing (BE-12 / BE-12b / BE-31) */
export type Plan = components['schemas']['Plan'];
export type Subscription = components['schemas']['Subscription'];
export type Wallet = components['schemas']['Wallet'];
export type Transaction = components['schemas']['Transaction'];
export type TransactionPage = components['schemas']['TransactionPage'];
export type PaymentListItem = components['schemas']['PaymentListItem'];
export type PaymentPage = components['schemas']['PaymentPage'];
export type PaymentMethod = components['schemas']['PaymentMethod'];
export type AutopaySettings = components['schemas']['AutopaySettings'];
export type UpdateAutopayRequest = components['schemas']['UpdateAutopayRequest'];
export type CheckoutRequest = components['schemas']['CheckoutRequest'];
export type CheckoutResponse = components['schemas']['CheckoutResponse'];
export type Invoice = components['schemas']['Invoice'];
export type InvoicePage = components['schemas']['InvoicePage'];
export type RequestInvoiceRequest = components['schemas']['RequestInvoiceRequest'];
export type BuyerRequisites = components['schemas']['BuyerRequisites'];
export type ChangePlanRequest = components['schemas']['ChangePlanRequest'];
export type ApplyPromoRequest = components['schemas']['ApplyPromoRequest'];
export type ApplyPromoResponse = components['schemas']['ApplyPromoResponse'];
export type StartTrialRequest = components['schemas']['StartTrialRequest'];
export type BillingPeriod = NonNullable<CheckoutRequest['period']>;
export type BillingStatus = NonNullable<Subscription['status']>;

/** Platform admin (BE-13 / A13) */
export type AdminAnnouncement = components['schemas']['AdminAnnouncement'];
export type AdminAnnouncementCreateRequest = components['schemas']['AdminAnnouncementCreateRequest'];
export type AdminAnnouncementPage = components['schemas']['AdminAnnouncementPage'];
export type AdminAnnouncementUpdateRequest = components['schemas']['AdminAnnouncementUpdateRequest'];
export type AdminAuditEntry = components['schemas']['AdminAuditEntry'];
export type AdminAuditPage = components['schemas']['AdminAuditPage'];
export type AdminFeatureFlag = components['schemas']['AdminFeatureFlag'];
export type AdminFeatureFlagUpsertRequest = components['schemas']['AdminFeatureFlagUpsertRequest'];
export type AdminImpersonateRequest = components['schemas']['AdminImpersonateRequest'];
export type AdminImpersonateResponse = components['schemas']['AdminImpersonateResponse'];
export type AdminPlanUpsertRequest = components['schemas']['AdminPlanUpsertRequest'];
export type AdminPromoCode = components['schemas']['AdminPromoCode'];
export type AdminPromoCreateRequest = components['schemas']['AdminPromoCreateRequest'];
export type AdminPromoPage = components['schemas']['AdminPromoPage'];
export type AdminPromoUpdateRequest = components['schemas']['AdminPromoUpdateRequest'];
export type AdminQueueInfo = components['schemas']['AdminQueueInfo'];
export type AdminQueueMessage = components['schemas']['AdminQueueMessage'];
export type AdminQueueMessageActionResponse =
  components['schemas']['AdminQueueMessageActionResponse'];
export type AdminQueueMessagePage = components['schemas']['AdminQueueMessagePage'];
export type AdminUserCard = components['schemas']['AdminUserCard'];
export type AdminUserPage = components['schemas']['AdminUserPage'];
export type AdminUserSummary = components['schemas']['AdminUserSummary'];
export type AdminWorkspaceCard = components['schemas']['AdminWorkspaceCard'];
export type AdminWorkspaceFeatureFlag = components['schemas']['AdminWorkspaceFeatureFlag'];
export type AdminWorkspaceFeatureFlagRequest =
  components['schemas']['AdminWorkspaceFeatureFlagRequest'];
export type AdminWorkspacePage = components['schemas']['AdminWorkspacePage'];
export type AdminWorkspaceSummary = components['schemas']['AdminWorkspaceSummary'];
export type AdminAdjustBalanceRequest = components['schemas']['AdminAdjustBalanceRequest'];
export type AdminAdjustSubscriptionRequest =
  components['schemas']['AdminAdjustSubscriptionRequest'];
export type ImpersonationBanner = components['schemas']['ImpersonationBanner'];
export type AnnouncementBanner = components['schemas']['AnnouncementBanner'];

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
