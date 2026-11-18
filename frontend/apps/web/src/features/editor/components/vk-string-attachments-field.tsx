'use client';

import { Input } from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useMemo } from 'react';

import {
  invalidVkStringAttachments,
  parseVkAttachmentsInput,
  VK_STRING_ATTACHMENTS_MAX,
} from '../lib/vk-string-attachments';

type VkStringAttachmentsFieldProps = {
  value: string[];
  onChange: (next: string[]) => void;
  visible: boolean;
};

export function VkStringAttachmentsField({
  value,
  onChange,
  visible,
}: VkStringAttachmentsFieldProps) {
  const t = useTranslations('editor.stringAttachments');
  const invalid = useMemo(() => invalidVkStringAttachments(value), [value]);

  if (!visible) {
    return null;
  }

  return (
    <section
      className="space-y-2 rounded-md border border-border p-3"
      aria-label={t('label')}
      data-testid="vk-string-attachments"
    >
      <div>
        <h2 className="text-sm font-semibold">{t('title')}</h2>
        <p className="text-xs text-muted-foreground">{t('hint')}</p>
      </div>
      <Input
        value={value.join(', ')}
        aria-label={t('title')}
        aria-invalid={invalid.length > 0}
        aria-describedby="vk-string-attachments-hint"
        placeholder={t('placeholder')}
        data-testid="vk-string-attachments-input"
        onChange={(event) => onChange(parseVkAttachmentsInput(event.target.value))}
      />
      <p id="vk-string-attachments-hint" className="text-xs text-muted-foreground">
        {t('format', { max: VK_STRING_ATTACHMENTS_MAX })}
      </p>
      {invalid.length > 0 ? (
        <p className="text-sm text-destructive" role="alert" data-testid="vk-string-attachments-error">
          {t('invalid', { tokens: invalid.join(', ') })}
        </p>
      ) : null}
    </section>
  );
}
