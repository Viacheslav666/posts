/**
 * FE-21 shell store for Crosspost (FE-24 / BE-36).
 * Rule OpenAPI types land with BE-36 — empty placeholder for parallel FE work.
 */
import type { EventSubscriptionResponse } from '@posts/api-client';

import { CHANNEL_IDS } from './channel-store';
import { PERSONAL_WORKSPACE_ID } from './workspace-store';

export type CrosspostDemoSeed = {
  workspaceId: string;
  sourceChannelIds: string[];
  targetChannelIds: string[];
  eventConsumers: EventSubscriptionResponse['consumers'];
};

type StoreState = {
  seed: CrosspostDemoSeed;
};

function createInitialState(): StoreState {
  return {
    seed: {
      workspaceId: PERSONAL_WORKSPACE_ID,
      sourceChannelIds: [CHANNEL_IDS.tgNews],
      targetChannelIds: [CHANNEL_IDS.vkNews, CHANNEL_IDS.maxBrand],
      eventConsumers: ['crosspost'],
    },
  };
}

let state = createInitialState();

export function resetCrosspostStore(): void {
  state = createInitialState();
}

export function getCrosspostDemoSeed(): CrosspostDemoSeed {
  return state.seed;
}

export function listCrosspostRules(): readonly never[] {
  return [];
}

/** Placeholder handlers for FE-24 — register with one spread line in handlers.ts. */
export const crosspostHandlers = [] as const;
