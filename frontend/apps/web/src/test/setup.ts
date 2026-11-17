import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';

import { clearAccessToken, clearPendingLogin, resetApiClient } from '@/shared/api-client';
import { server } from '@/mocks/server';

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetApiClient();
  clearAccessToken();
  clearPendingLogin();
});

afterAll(() => {
  server.close();
});
