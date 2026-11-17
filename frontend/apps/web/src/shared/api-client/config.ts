function isApiMockingEnabled(): boolean {
  if (process.env.NEXT_PUBLIC_API_MOCKING === 'true') {
    return true;
  }
  if (process.env.NEXT_PUBLIC_API_MOCKING === 'false') {
    return false;
  }
  return process.env.NODE_ENV === 'development';
}

function sameOriginBaseUrl(): string {
  if (typeof window !== 'undefined' && window.location?.origin) {
    return window.location.origin;
  }
  // Vitest / SSR fallback (jsdom sets url to http://localhost/)
  return 'http://localhost';
}

/**
 * Absolute API origin for `openapi-fetch` (relative baseUrl is invalid for `URL`).
 * Prefer same-origin so the refresh cookie stays first-party and MSW can intercept.
 * Set `NEXT_PUBLIC_API_BASE_URL` when talking to a remote API with mocking disabled.
 */
export function getApiBaseUrl(): string {
  if (isApiMockingEnabled()) {
    return sameOriginBaseUrl();
  }

  const configured = process.env.NEXT_PUBLIC_API_BASE_URL?.trim();
  if (!configured || configured === '/') {
    return sameOriginBaseUrl();
  }
  return configured.replace(/\/$/, '');
}
