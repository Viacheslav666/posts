import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';

import { clearAccessToken, clearPendingLogin, resetApiClient } from '@/shared/api-client';
import { server } from '@/mocks/server';
import { resetWorkspaceStore } from '@/mocks/workspace-store';

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetWorkspaceStore();
  resetApiClient();
  clearAccessToken();
  clearPendingLogin();
});

afterAll(() => {
  server.close();
});
