import { describe, expect, it } from 'vitest';
import { createPostsApiClient } from './index';

describe('createPostsApiClient', () => {
  it('creates a client', () => {
    const client = createPostsApiClient('http://localhost:8080');
    expect(client).toBeTruthy();
  });
});
