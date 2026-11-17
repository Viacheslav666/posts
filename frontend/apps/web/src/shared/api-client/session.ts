/**
 * Access JWT and short-lived pending login live only in memory (never localStorage/sessionStorage).
 * Refresh uses httpOnly cookie.
 */

let accessToken: string | null = null;
let pendingLogin: PendingLogin | null = null;

export function getAccessToken(): string | null {
  return accessToken;
}

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function clearAccessToken(): void {
  accessToken = null;
}

export type PendingLogin = {
  email: string;
  password: string;
};

/** Keep credentials in process memory for the 2FA step (lost on full page reload). */
export function savePendingLogin(value: PendingLogin): void {
  pendingLogin = { email: value.email, password: value.password };
}

export function readPendingLogin(): PendingLogin | null {
  return pendingLogin;
}

export function clearPendingLogin(): void {
  pendingLogin = null;
}
