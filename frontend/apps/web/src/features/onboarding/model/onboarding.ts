const STORAGE_PREFIX = 'posts.onboarding.dismissed.';

export type OnboardingStepId = 'workspace' | 'channel' | 'post';

export type OnboardingStep = {
  id: OnboardingStepId;
  done: boolean;
};

export type OnboardingProgress = {
  steps: OnboardingStep[];
  completedCount: number;
  total: number;
  allDone: boolean;
  dismissed: boolean;
};

export function isOnboardingDismissed(workspaceId: string): boolean {
  if (typeof window === 'undefined') return false;
  try {
    return window.localStorage.getItem(`${STORAGE_PREFIX}${workspaceId}`) === '1';
  } catch {
    return false;
  }
}

export function dismissOnboarding(workspaceId: string): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(`${STORAGE_PREFIX}${workspaceId}`, '1');
  } catch {
    // ignore quota / private mode
  }
}

export function clearOnboardingDismiss(workspaceId: string): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.removeItem(`${STORAGE_PREFIX}${workspaceId}`);
  } catch {
    // ignore
  }
}

export function computeOnboardingProgress(input: {
  workspaceId: string;
  hasWorkspace: boolean;
  hasChannel: boolean;
  hasPost: boolean;
  dismissed?: boolean;
}): OnboardingProgress {
  const steps: OnboardingStep[] = [
    { id: 'workspace', done: input.hasWorkspace },
    { id: 'channel', done: input.hasChannel },
    { id: 'post', done: input.hasPost },
  ];
  const completedCount = steps.filter((s) => s.done).length;
  const dismissed = input.dismissed ?? isOnboardingDismissed(input.workspaceId);
  return {
    steps,
    completedCount,
    total: steps.length,
    allDone: completedCount === steps.length,
    dismissed,
  };
}
