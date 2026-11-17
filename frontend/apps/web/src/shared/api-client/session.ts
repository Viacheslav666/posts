/**
 * Access JWT lives only in memory (never localStorage). Refresh uses httpOnly cookie.
 */

let accessToken: string | null = null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function clearAccessToken(): void {
  accessToken = null;
}

const PENDING_LOGIN_KEY = 'posts.auth.pendingLogin';

export type PendingLogin = {
  email: string;
  password: string;
};

export function savePendingLogin(value: PendingLogin): void {
  if (typeof sessionStorage === 'undefined') {
    return;
  }
  sessionStorage.setItem(PENDING_LOGIN_KEY, JSON.stringify(value));
}

export function readPendingLogin(): PendingLogin | null {
  if (typeof sessionStorage === 'undefined') {
    return null;
  }
  const raw = sessionStorage.getItem(PENDING_LOGIN_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as PendingLogin;
    if (typeof parsed.email === 'string' && typeof parsed.password === 'string') {
      return parsed;
    }
  } catch {
    // ignore malformed payload
  }
  return null;
}

export function clearPendingLogin(): void {
  if (typeof sessionStorage === 'undefined') {
    return;
  }
  sessionStorage.removeItem(PENDING_LOGIN_KEY);
}
