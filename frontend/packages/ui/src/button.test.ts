import { describe, expect, it } from 'vitest';

import { Button } from './components/button';
import { badgeVariants } from './components/badge';
import { cn } from './lib/utils';

describe('UI package', () => {
  it('exports Button component', () => {
    expect(Button).toBeTruthy();
    expect(Button.displayName).toBe('Button');
  });

  it('merges class names', () => {
    expect(cn('px-2', 'px-4')).toBe('px-4');
  });

  it('builds status badge classes', () => {
    expect(badgeVariants({ variant: 'published' })).toContain('status-published');
  });
});
