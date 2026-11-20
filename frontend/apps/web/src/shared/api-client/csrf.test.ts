import { describe, expect, it } from 'vitest';

import { isPublicAuthApiPath } from './csrf';

describe('csrf helpers', () => {
  it('treats public auth paths without bearer', () => {
    expect(isPublicAuthApiPath('http://localhost:3000/api/v1/auth/register')).toBe(true);
    expect(isPublicAuthApiPath('/api/v1/auth/email/verify')).toBe(true);
    expect(isPublicAuthApiPath('/api/v1/me')).toBe(false);
  });
});