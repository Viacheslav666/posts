import { describe, expect, it } from 'vitest';
import { Button } from './button';

describe('Button', () => {
  it('is a function component', () => {
    expect(typeof Button).toBe('function');
  });
});
