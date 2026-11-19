/**
 * FE-21 shell store for Antispam (FE-23 / BE-35).
 * Rule/log OpenAPI types land with BE-35 — empty placeholder for parallel FE work.
 */
import type { EventSubscriptionResponse } from '@posts/api-client';

import { CHANNEL_IDS } from './channel-store';
import { PERSONAL_WORKSPACE_ID } from './workspace-store';

export type AntispamDemoSeed = {
  workspaceId: string;
  channelIds: string[];
  eventConsumers: EventSubscriptionResponse['consumers'];
};

type StoreState = {
  seed: AntispamDemoSeed;
};

function createInitialState(): StoreState {
  return {
    seed: {
      workspaceId: PERSONAL_WORKSPACE_ID,
      channelIds: [CHANNEL_IDS.tgNews, CHANNEL_IDS.maxBrand, CHANNEL_IDS.vkNews],
      eventConsumers: ['antispam'],
    },
  };
}

let state = createInitialState();

export function resetAntispamStore(): void {
  state = createInitialState();
}

export function getAntispamDemoSeed(): AntispamDemoSeed {
  return state.seed;
}

export function listAntispamRules(): readonly never[] {
  return [];
}

/** Placeholder handlers for FE-23 — register with one spread line in handlers.ts. */
export const antispamHandlers = [] as const;
