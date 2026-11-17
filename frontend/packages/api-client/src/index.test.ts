import { describe, expect, it } from 'vitest';

import { createPostsApiClient } from './index';

describe('createPostsApiClient', () => {
  it('creates a client with default same-origin baseUrl', () => {
    const client = createPostsApiClient();
    expect(client).toBeTruthy();
    expect(typeof client.GET).toBe('function');
    expect(typeof client.POST).toBe('function');
  });

  it('accepts an absolute baseUrl', () => {
    const client = createPostsApiClient({ baseUrl: 'http://localhost:8080' });
    expect(client).toBeTruthy();
  });
});
