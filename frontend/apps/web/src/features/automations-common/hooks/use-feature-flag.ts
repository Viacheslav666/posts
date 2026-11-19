'use client';

import { useMe } from '@/shared/hooks/use-me';

/** Feature keys for Phase 2 automations shell (FE-21). */
export const AUTOMATION_FEATURE_FLAGS = {
  suggest: 'automation.suggest',
  antispam: 'automation.antispam',
  crosspost: 'automation.crosspost',
} as const;

export type AutomationFeatureFlag =
  (typeof AUTOMATION_FEATURE_FLAGS)[keyof typeof AUTOMATION_FEATURE_FLAGS];

function isLocalOrMsw(): boolean {
  if (typeof process === 'undefined') {
    return false;
  }
  return (
    process.env.NEXT_PUBLIC_API_MOCKING === 'true' || process.env.NODE_ENV === 'development'
  );
}

/**
 * Resolved flag from `GET /me.features`.
 * In local/MSW the section is always available when the key is missing (FE-21).
 */
export function useFeatureFlag(key: string): boolean {
  const { data } = useMe();
  const value = data?.features?.[key];
  if (typeof value === 'boolean') {
    return value;
  }
  return isLocalOrMsw();
}

export function useAutomationFeatureFlags() {
  return {
    suggest: useFeatureFlag(AUTOMATION_FEATURE_FLAGS.suggest),
    antispam: useFeatureFlag(AUTOMATION_FEATURE_FLAGS.antispam),
    crosspost: useFeatureFlag(AUTOMATION_FEATURE_FLAGS.crosspost),
  };
}
