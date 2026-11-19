'use client';

import { Input } from '@posts/ui';
import { useState } from 'react';

import { vkIdsFromText } from '../model/schemas';

type TokenFieldProps = {
  value: string[];
  onChange: (next: string[]) => void;
  disabled?: boolean;
  testId?: string;
  placeholder?: string;
};

/**
 * Keeps the raw text while the field is focused.
 * Parsing on every keystroke and writing the joined tokens back drops the separator,
 * so a second id or hashtag cannot be typed.
 */
export function TokenField({ value, onChange, disabled, testId, placeholder }: TokenFieldProps) {
  const [draft, setDraft] = useState<string | null>(null);
  const shown = draft ?? value.join(', ');

  return (
    <Input
      value={shown}
      disabled={disabled}
      placeholder={placeholder}
      data-testid={testId}
      onFocus={() => setDraft(value.join(', '))}
      onChange={(event) => {
        const raw = event.target.value;
        setDraft(raw);
        onChange(vkIdsFromText(raw));
      }}
      onBlur={() => setDraft(null)}
    />
  );
}
