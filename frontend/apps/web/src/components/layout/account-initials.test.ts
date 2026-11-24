import { describe, expect, it } from 'vitest';

import { accountInitials, accountTooltip, isSocialLogin } from './account-initials';

describe('accountInitials', () => {
  it('uses the first letters of the first and last name', () => {
    expect(accountInitials('Никита Галкин', 'n@posts.app')).toBe('НГ');
    expect(accountInitials('Ada Lovelace', null)).toBe('AL');
  });

  it('uses the first two letters of a single name', () => {
    expect(accountInitials('Никита', null)).toBe('НИ');
  });

  it('uses the login when the name is missing', () => {
    expect(accountInitials('  ', 'demo@posts.app')).toBe('DE');
    expect(accountInitials(null, 'n.galkin@posts.app')).toBe('NG');
  });

  it('returns null when nothing yields letters', () => {
    expect(accountInitials('', '@@@')).toBeNull();
  });
});

describe('account tooltip', () => {
  it('joins the name and the login method', () => {
    expect(accountTooltip('Никита Галкин', 'n@posts.app', 'vk')).toBe('Никита Галкин · VK');
    expect(accountTooltip('Никита Галкин', 'n@posts.app', 'email')).toBe('Никита Галкин · Email');
    expect(isSocialLogin('vk')).toBe(true);
    expect(isSocialLogin('email')).toBe(false);
  });
});
