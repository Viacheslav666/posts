import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { EditorDraft } from '../model/types';
import { EMPTY_DOC } from '../model/types';
import { useDraftAutosave } from './use-draft-autosave';

const saveDraft = vi.fn(async (_draft?: unknown) => undefined);

vi.mock('../model/draft-db', () => ({
  saveDraft: (draft: unknown) => saveDraft(draft),
}));

function sampleDraft(overrides?: Partial<EditorDraft>): EditorDraft {
  return {
    id: 'd1',
    workspaceId: 'ws',
    updatedAt: new Date().toISOString(),
    baseHtml: 'hi',
    baseDoc: EMPTY_DOC,
    selectedChannelIds: [],
    variants: [],
    previewNetwork: 'tg',
    activeVariant: 'base',
    ...overrides,
  };
}

describe('useDraftAutosave', () => {
  beforeEach(() => {
    saveDraft.mockClear();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('saves draft after 5 seconds of changes', async () => {
    const { rerender } = renderHook(
      ({ draft }) => useDraftAutosave(draft, true),
      { initialProps: { draft: sampleDraft({ baseHtml: 'a' }) } },
    );

    rerender({ draft: sampleDraft({ baseHtml: 'ab' }) });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(4999);
    });
    expect(saveDraft).not.toHaveBeenCalled();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(saveDraft).toHaveBeenCalledTimes(1);
  });
});
