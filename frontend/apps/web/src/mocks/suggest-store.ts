/**
 * FE-21 shell store for VK Suggest (FE-22 / BE-34).
 * OpenAPI schemas for suggest rules/queue are not in main yet — keep an empty
 * typed placeholder so FE-22 can extend without touching handlers registration.
 */
import type { EventSubscriptionResponse } from '@posts/api-client';

import { CHANNEL_IDS } from './channel-store';
import { PERSONAL_WORKSPACE_ID } from './workspace-store';

/** Placeholder until BE-34 publishes SuggestRule schemas. */
export type SuggestDemoSeed = {
  workspaceId: string;
  channelIds: string[];
  /** Linked event-subscription consumers for demo@posts.app. */
  eventConsumers: EventSubscriptionResponse['consumers'];
};

type StoreState = {
  seed: SuggestDemoSeed;
};

function createInitialState(): StoreState {
  return {
    seed: {
      workspaceId: PERSONAL_WORKSPACE_ID,
      channelIds: [CHANNEL_IDS.vkNews],
      eventConsumers: ['suggest'],
    },
  };
}

let state = createInitialState();

export function resetSuggestStore(): void {
  state = createInitialState();
}

export function getSuggestDemoSeed(): SuggestDemoSeed {
  return state.seed;
}

export function listSuggestRules(): readonly never[] {
  return [];
}

/** Placeholder handlers for FE-22 — register with one spread line in handlers.ts. */
export const suggestHandlers = [] as const;
