import { get, set, del } from 'idb-keyval';

import type { EditorDraft } from './types';

const keyFor = (workspaceId: string, draftId: string) =>
  `posts:editor-draft:${workspaceId}:${draftId}`;

export async function loadDraft(workspaceId: string, draftId: string): Promise<EditorDraft | undefined> {
  if (typeof indexedDB === 'undefined') {
    return undefined;
  }
  return get<EditorDraft>(keyFor(workspaceId, draftId));
}

export async function saveDraft(draft: EditorDraft): Promise<void> {
  if (typeof indexedDB === 'undefined') {
    return;
  }
  await set(keyFor(draft.workspaceId, draft.id), {
    ...draft,
    updatedAt: new Date().toISOString(),
  });
}

export async function clearDraft(workspaceId: string, draftId: string): Promise<void> {
  if (typeof indexedDB === 'undefined') {
    return;
  }
  await del(keyFor(workspaceId, draftId));
}
