'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

import { saveDraft } from '../model/draft-db';
import type { EditorDraft } from '../model/types';

const AUTOSAVE_MS = 5000;

export type AutosaveStatus = 'idle' | 'dirty' | 'saving' | 'saved' | 'error';

export function useDraftAutosave(draft: EditorDraft | null, enabled = true) {
  const [status, setStatus] = useState<AutosaveStatus>('idle');
  const [lastSavedAt, setLastSavedAt] = useState<string | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const draftRef = useRef(draft);
  draftRef.current = draft;

  const flush = useCallback(async () => {
    const current = draftRef.current;
    if (!current || !enabled) {
      return;
    }
    setStatus('saving');
    try {
      await saveDraft(current);
      setLastSavedAt(new Date().toISOString());
      setStatus('saved');
    } catch {
      setStatus('error');
    }
  }, [enabled]);

  useEffect(() => {
    if (!draft || !enabled) {
      return;
    }
    setStatus('dirty');
    if (timerRef.current) {
      clearTimeout(timerRef.current);
    }
    timerRef.current = setTimeout(() => {
      void flush();
    }, AUTOSAVE_MS);
    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }
    };
  }, [draft, enabled, flush]);

  useEffect(() => {
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (status === 'dirty' || status === 'saving') {
        event.preventDefault();
        event.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [status]);

  return { status, lastSavedAt, flush, autosaveMs: AUTOSAVE_MS };
}
