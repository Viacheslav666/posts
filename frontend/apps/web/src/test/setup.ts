import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';

import { clearAccessToken, clearPendingLogin, resetApiClient } from '@/shared/api-client';
import { server } from '@/mocks/server';
import { resetWorkspaceStore } from '@/mocks/workspace-store';

// ProseMirror / TipTap need layout APIs that jsdom does not implement.
beforeAll(() => {
  if (typeof window !== 'undefined') {
    const rect = {
      x: 0,
      y: 0,
      top: 0,
      left: 0,
      bottom: 0,
      right: 0,
      width: 0,
      height: 0,
      toJSON: () => ({}),
    };
    const clientRects = {
      item: () => null,
      length: 0,
      [Symbol.iterator]: function* () {},
    } as DOMRectList;
    Range.prototype.getBoundingClientRect = () => rect as DOMRect;
    Range.prototype.getClientRects = () => clientRects;
    Element.prototype.getBoundingClientRect = () => rect as DOMRect;
    Element.prototype.getClientRects = () => clientRects;
    document.elementFromPoint = () => null;
  }
});

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
