'use client';

const LOGIN_PREFIXES = [
  '/login',
  '/logout',
  '/unsubscribe',
  '/register',
  '/forgot-password',
  '/reset-password',
  '/verify-email',
  '/invite',
  '/two-factor',
  // OAuth provider redirects (VK ID, Yandex ID) land here before any Posts session exists.
  '/auth/callback',
];

export function isPublicAuthRoute(pathname: string): boolean {
  if (pathname === '/' || pathname.startsWith('/pricing') || pathname.startsWith('/legal')) {
    return true;
  }
  return LOGIN_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

let redirectScheduled = false;

/** Navigate to login once; avoids redirect loops while queries fail. */
export function redirectToLogin(reason?: string): void {
  if (typeof window === 'undefined' || redirectScheduled) {
    return;
  }
  const pathname = window.location.pathname;
  if (isPublicAuthRoute(pathname)) {
    return;
  }
  redirectScheduled = true;
  const params = new URLSearchParams();
  params.set('returnUrl', pathname + window.location.search);
  if (reason) {
    params.set('reason', reason);
  }
  window.location.assign(`/login?${params.toString()}`);
}

export function resetAuthRedirectForTests(): void {
  redirectScheduled = false;
}