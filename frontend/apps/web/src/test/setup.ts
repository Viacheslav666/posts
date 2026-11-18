import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';

import { clearAccessToken, clearPendingLogin, resetApiClient } from '@/shared/api-client';
import { resetChannelStore } from '@/mocks/channel-store';
import { resetMediaStoreForTests } from '@/mocks/media-store';
import { resetOrdStore } from '@/mocks/ord-store';
import { resetPostStore } from '@/mocks/post-store';
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
      bottom: 720,
      right: 800,
      width: 800,
      height: 720,
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

    if (!('ResizeObserver' in window)) {
      class ResizeObserverStub {
        observe() {}
        unobserve() {}
        disconnect() {}
      }
      // @ts-expect-error jsdom polyfill
      window.ResizeObserver = ResizeObserverStub;
    }
  }
});

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetWorkspaceStore();
  resetChannelStore();
  resetMediaStoreForTests();
  resetPostStore();
  resetOrdStore();
  resetApiClient();
  clearAccessToken();
  clearPendingLogin();
});

afterAll(() => {
  server.close();
});
