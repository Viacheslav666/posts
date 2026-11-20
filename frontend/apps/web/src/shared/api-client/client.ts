'use client';

import {
  createPostsApiClient,
  type Middleware,
  type PostsApiClient,
  type TokenResponse,
} from '@posts/api-client';

import { toApiProblemError } from '@/shared/lib/errors';

import {
  isMutatingHttpMethod,
  isPublicAuthApiPath,
  readXsrfTokenFromDocumentCookie,
} from './csrf';
import { getApiBaseUrl } from './config';
import { clearAccessToken, getAccessToken, setAccessToken } from './session';

let clientSingleton: PostsApiClient | null = null;
let refreshInFlight: Promise<boolean> | null = null;

function isAuthRefreshPath(url: string): boolean {
  return url.includes('/api/v1/auth/refresh');
}

function isAuthLoginPath(url: string): boolean {
  return url.includes('/api/v1/auth/login');
}

async function refreshAccessToken(client: PostsApiClient): Promise<boolean> {
  if (refreshInFlight) {
    return refreshInFlight;
  }

  refreshInFlight = (async () => {
    const { data, error, response } = await client.POST('/api/v1/auth/refresh', {
      body: {},
    });
    if (error || !response.ok || !data?.accessToken) {
      clearAccessToken();
      return false;
    }
    applyTokenResponse(data);
    return true;
  })().finally(() => {
    refreshInFlight = null;
  });

  return refreshInFlight;
}

export function applyTokenResponse(tokens: TokenResponse): void {
  if (tokens.accessToken) {
    setAccessToken(tokens.accessToken);
  }
}

let csrfPrime: Promise<void> | null = null;

export function resetCsrfPrimeForTests(): void {
  csrfPrime = null;
}

function resolveApiUrl(input: string): string {
  if (input.startsWith('http://') || input.startsWith('https://')) {
    return input;
  }
  const base = getApiBaseUrl().replace(/\/$/, '');
  return input.startsWith('/') ? `${base}${input}` : `${base}/${input}`;
}

async function ensureCsrfCookiePrimed(baseUrl: string): Promise<void> {
  if (readXsrfTokenFromDocumentCookie()) {
    return;
  }
  if (typeof window === 'undefined') {
    return;
  }
  if (!csrfPrime) {
    const pingUrl = `${baseUrl.replace(/\/$/, '')}/api/v1/ping`;
    csrfPrime = fetch(pingUrl, { credentials: 'include' })
      .then(() => undefined)
      .finally(() => {
        csrfPrime = null;
      });
  }
  await csrfPrime;
}

function createCsrfMiddleware(getBaseUrl: () => string): Middleware {
  return {
    async onRequest({ request }) {
      if (!isMutatingHttpMethod(request.method)) {
        return request;
      }
      await ensureCsrfCookiePrimed(getBaseUrl());
      const xsrf = readXsrfTokenFromDocumentCookie();
      if (xsrf) {
        request.headers.set('X-XSRF-TOKEN', xsrf);
      }
      return request;
    },
  };
}

function createAuthMiddleware(client: PostsApiClient): Middleware {
  return {
    async onRequest({ request }) {
      const token = getAccessToken();
      const publicAuth = isPublicAuthApiPath(request.url);
      if (publicAuth) {
        request.headers.delete('Authorization');
        return request;
      }
      if (token && !request.headers.has('Authorization')) {
        request.headers.set('Authorization', `Bearer ${token}`);
      }
      return request;
    },
    async onResponse({ request, response }) {
      if (response.status !== 401) {
        return response;
      }
      if (isAuthRefreshPath(request.url) || isAuthLoginPath(request.url)) {
        return response;
      }
      if (request.headers.get('X-Posts-Retry') === '1') {
        return response;
      }

      const refreshed = await refreshAccessToken(client);
      if (!refreshed) {
        return response;
      }

      const retryHeaders = new Headers(request.headers);
      retryHeaders.set('X-Posts-Retry', '1');
      const token = getAccessToken();
      if (token) {
        retryHeaders.set('Authorization', `Bearer ${token}`);
      }

      return fetch(request.url, {
        method: request.method,
        headers: retryHeaders,
        body: request.method === 'GET' || request.method === 'HEAD' ? undefined : await request.clone().arrayBuffer(),
        credentials: 'include',
      });
    },
  };
}

export function createAppApiClient(): PostsApiClient {
  const baseUrl = getApiBaseUrl();
  const client = createPostsApiClient({
    baseUrl,
    credentials: 'include',
  });
  client.use(createCsrfMiddleware(() => baseUrl));
  client.use(createAuthMiddleware(client));
  return client;
}

export function getApiClient(): PostsApiClient {
  if (!clientSingleton) {
    clientSingleton = createAppApiClient();
  }
  return clientSingleton;
}

/** Reset singleton (tests). */
export function resetApiClient(): void {
  clientSingleton = null;
  refreshInFlight = null;
  resetCsrfPrimeForTests();
}

/**
 * Same-origin `fetch` with cookie credentials, bearer auth, and CSRF headers on mutating methods.
 * Use when an endpoint is absent from OpenAPI (binary downloads, mock helpers, extra query params).
 */
export async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const method = init.method ?? 'GET';
  const url = resolveApiUrl(input);
  if (isMutatingHttpMethod(method)) {
    await ensureCsrfCookiePrimed(getApiBaseUrl());
  }
  const headers = new Headers(init.headers);
  if (!isPublicAuthApiPath(url)) {
    const token = getAccessToken();
    if (token && !headers.has('Authorization')) {
      headers.set('Authorization', `Bearer ${token}`);
    }
  }
  if (isMutatingHttpMethod(method)) {
    const xsrf = readXsrfTokenFromDocumentCookie();
    if (xsrf) {
      headers.set('X-XSRF-TOKEN', xsrf);
    }
  }
  return fetch(url, { ...init, headers, credentials: 'include' });
}

type OpenApiResult<T> = {
  data?: T;
  error?: unknown;
  response: Response;
};

/**
 * Unwrap openapi-fetch result: throw {@link ApiProblemError} on ProblemDetail / non-OK.
 */
export function unwrapApi<T>(result: OpenApiResult<T>): T {
  const { data, error, response } = result;
  if (!response.ok || error) {
    throw toApiProblemError(error ?? { title: response.statusText }, response.status);
  }
  if (data === undefined) {
    throw toApiProblemError(
      { title: 'Empty response', detail: 'API returned no body', code: 'unknown' },
      response.status,
    );
  }
  return data;
}
