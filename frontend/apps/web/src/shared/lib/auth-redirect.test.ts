import { describe, expect, it, beforeEach } from 'vitest';

import { isPublicAuthRoute, resetAuthRedirectForTests } from './auth-redirect';

describe('auth-redirect', () => {
  beforeEach(() => {
    resetAuthRedirectForTests();
  });

  it('treats workspace routes as protected', () => {
    expect(isPublicAuthRoute('/app/ws-1/posts')).toBe(false);
  });

  it('treats login and logout as public', () => {
    expect(isPublicAuthRoute('/login')).toBe(true);
    expect(isPublicAuthRoute('/logout')).toBe(true);
  });
});