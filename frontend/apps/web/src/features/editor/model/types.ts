import type { NetworkId, TiptapNode } from '@posts/domain';

import type { EditorChannelOption } from '@/features/channels/hooks/use-workspace-channels';

/** Channel option in the editor picker (backed by GET /channels). */
export type EditorChannel = EditorChannelOption;

/** @deprecated Use EditorChannel — kept for older imports. */
export type MockChannel = EditorChannel;

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
