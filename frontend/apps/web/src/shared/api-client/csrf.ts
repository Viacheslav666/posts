const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

/** Spring {@link CookieCsrfTokenRepository} default cookie name. */
export const XSRF_COOKIE_NAME = 'XSRF-TOKEN';

export function isMutatingHttpMethod(method: string): boolean {
  return MUTATING_METHODS.has(method.toUpperCase());
}

export function readXsrfTokenFromDocumentCookie(): string | null {
  if (typeof document === 'undefined') {
    return null;
  }
  const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${XSRF_COOKIE_NAME}=([^;]*)`));
  if (!match?.[1]) {
    return null;
  }
  try {
    return decodeURIComponent(match[1]);
  } catch {
    return match[1];
  }
}

/** Public auth endpoints must not carry a stale Bearer access token (resource server would 401 first). */
export function isPublicAuthApiPath(url: string): boolean {
  try {
    const path = new URL(url, 'http://localhost').pathname;
    return /^\/api\/v1\/auth\/(register|email\/verify|login|refresh|logout|password\/|oauth\/)/.test(path);
  } catch {
    return false;
  }
}