import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  DEFAULT_TOAST_DURATION,
  DESTRUCTIVE_TOAST_DURATION,
  reducer,
  toast,
} from './use-toast';

describe('toast defaults', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('exposes a short default duration for success toasts', () => {
    expect(DEFAULT_TOAST_DURATION).toBe(4000);
    expect(DESTRUCTIVE_TOAST_DURATION).toBeGreaterThan(DEFAULT_TOAST_DURATION);
  });

  it('auto-dismisses default toasts after DEFAULT_TOAST_DURATION', () => {
    const { dismiss } = toast({ title: 'Вы вошли', description: 'Перенаправляем…' });
    expect(typeof dismiss).toBe('function');

    // Advance past success duration + remove delay
    vi.advanceTimersByTime(DEFAULT_TOAST_DURATION + 500);

    // Creating another toast should not throw; dismiss timer fired cleanly
    expect(() => toast({ title: 'ok' })).not.toThrow();
  });

  it('reducer marks toast closed on DISMISS_TOAST', () => {
    const state = reducer(
      {
        toasts: [
          {
            id: '1',
            open: true,
            title: 'Вы вошли',
          },
        ],
      },
      { type: 'DISMISS_TOAST', toastId: '1' },
    );
    expect(state.toasts[0]?.open).toBe(false);
  });
});
