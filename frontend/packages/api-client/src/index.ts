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
