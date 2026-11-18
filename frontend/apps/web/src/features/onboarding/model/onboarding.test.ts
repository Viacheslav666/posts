import { describe, expect, it } from 'vitest';

import { computeOnboardingProgress } from './onboarding';

describe('computeOnboardingProgress', () => {
  it('tracks three steps and completion', () => {
    const partial = computeOnboardingProgress({
      workspaceId: 'ws',
      hasWorkspace: true,
      hasChannel: false,
      hasPost: false,
      dismissed: false,
    });
    expect(partial.completedCount).toBe(1);
    expect(partial.allDone).toBe(false);
    expect(partial.steps.find((s) => s.id === 'channel')?.done).toBe(false);

    const done = computeOnboardingProgress({
      workspaceId: 'ws',
      hasWorkspace: true,
      hasChannel: true,
      hasPost: true,
      dismissed: false,
    });
    expect(done.allDone).toBe(true);
    expect(done.completedCount).toBe(3);
  });

  it('respects dismissed flag', () => {
    const progress = computeOnboardingProgress({
      workspaceId: 'ws',
      hasWorkspace: false,
      hasChannel: false,
      hasPost: false,
      dismissed: true,
    });
    expect(progress.dismissed).toBe(true);
  });
});
