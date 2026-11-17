import type { NetworkId, TiptapNode } from '@posts/domain';

export type MockChannel = {
  id: string;
  name: string;
  network: NetworkId;
  projectName: string;
};

export type EditorVariant = {
  network: NetworkId;
  /** When set, overrides base content for this network. */
  overridden: boolean;
  html: string;
  doc: TiptapNode | null;
};

export type EditorDraft = {
  id: string;
  workspaceId: string;
  updatedAt: string;
  baseHtml: string;
  baseDoc: TiptapNode | null;
  selectedChannelIds: string[];
  variants: EditorVariant[];
  previewNetwork: 'vk' | 'tg' | 'ok' | 'max';
  activeVariant: 'base' | NetworkId;
};

export const EMPTY_DOC: TiptapNode = {
  type: 'doc',
  content: [{ type: 'paragraph' }],
};
