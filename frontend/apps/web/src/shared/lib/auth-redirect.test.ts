import { afterEach, describe, expect, it, beforeEach, vi } from 'vitest';

import { isPublicAuthRoute, redirectToLogin, resetAuthRedirectForTests } from './auth-redirect';

describe('auth-redirect', () => {
  beforeEach(() => {
    resetAuthRedirectForTests();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    window.history.replaceState(null, '', '/');
  });

  it('treats workspace routes as protected', () => {
    expect(isPublicAuthRoute('/app/ws-1/posts')).toBe(false);
  });

  it('treats login and logout as public', () => {
    expect(isPublicAuthRoute('/login')).toBe(true);
    expect(isPublicAuthRoute('/logout')).toBe(true);
  });

  it('treats OAuth login callbacks as public', () => {
    expect(isPublicAuthRoute('/auth/callback/vk')).toBe(true);
    expect(isPublicAuthRoute('/auth/callback/yandex')).toBe(true);
  });

  it('does not treat channel connect callbacks as public', () => {
    expect(isPublicAuthRoute('/app/ws-1/channels/connect/vk/callback')).toBe(false);
  });

  it('does not redirect to login from an OAuth callback', () => {
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, pathname: '/auth/callback/vk', search: '?code=x', assign });

    redirectToLogin('session_expired');

    expect(assign).not.toHaveBeenCalled();
  });
});
