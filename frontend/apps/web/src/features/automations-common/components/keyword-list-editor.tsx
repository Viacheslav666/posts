'use client';

import { Button, Input, cn } from '@posts/ui';
import { X } from 'lucide-react';
import { useId, useState } from 'react';
import { useTranslations } from 'next-intl';

import {
  mergeKeywords,
  parseKeywordPaste,
  removeKeyword,
  type KeywordMatchMode,
} from '../model/keywords';

export type KeywordDictionaryOption = {
  id: string;
  name: string;
  keywords: string[];
};

export type KeywordListEditorProps = {
  value: string[];
  onChange: (next: string[]) => void;
  matchMode: KeywordMatchMode;
  onMatchModeChange: (mode: KeywordMatchMode) => void;
  dictionaries?: KeywordDictionaryOption[];
  disabled?: boolean;
  maxItems?: number;
  className?: string;
  id?: string;
};

export function KeywordListEditor({
  value,
  onChange,
  matchMode,
  onMatchModeChange,
  dictionaries = [],
  disabled,
  maxItems = 500,
  className,
  id,
}: KeywordListEditorProps) {
  const t = useTranslations('automations.common.keywords');
  const reactId = useId();
  const inputId = id ?? `keyword-editor-${reactId}`;
  const [draft, setDraft] = useState('');

  const addFromDraft = () => {
    if (!draft.trim()) return;
    const parsed = parseKeywordPaste(draft);
    const merged = mergeKeywords(value, parsed).slice(0, maxItems);
    onChange(merged);
    setDraft('');
  };

  return (
    <div className={cn('space-y-3', className)} data-testid="keyword-list-editor">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm font-medium" id={`${inputId}-label`}>
          {t('label')}
        </p>
        <p className="text-xs text-muted-foreground" data-testid="keyword-count">
          {t('count', { count: value.length, max: maxItems })}
        </p>
      </div>

      <div className="flex flex-wrap gap-1.5" role="list" aria-labelledby={`${inputId}-label`}>
        {value.map((keyword) => (
          <span
            key={keyword}
            role="listitem"
            className="inline-flex items-center gap-1 rounded-md border border-border bg-muted/40 px-2 py-0.5 text-sm"
            data-testid={`keyword-chip-${keyword}`}
          >
            {keyword}
            <button
              type="button"
              className="rounded-sm p-0.5 text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label={t('remove', { keyword })}
              disabled={disabled}
              onClick={() => onChange(removeKeyword(value, keyword))}
            >
              <X className="h-3 w-3" aria-hidden />
            </button>
          </span>
        ))}
        {value.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('empty')}</p>
        ) : null}
      </div>

      <div className="flex flex-col gap-2 sm:flex-row">
        <Input
          id={inputId}
          value={draft}
          disabled={disabled}
          placeholder={t('placeholder')}
          aria-describedby={`${inputId}-hint`}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter') {
              event.preventDefault();
              addFromDraft();
            }
          }}
        />
        <Button type="button" variant="secondary" disabled={disabled} onClick={addFromDraft}>
          {t('add')}
        </Button>
      </div>
      <p id={`${inputId}-hint`} className="text-xs text-muted-foreground">
        {t('hint')}
      </p>

      <fieldset className="space-y-2" disabled={disabled}>
        <legend className="text-sm font-medium">{t('matchMode')}</legend>
        <div className="flex flex-wrap gap-3">
          {(['whole_word', 'substring'] as const).map((mode) => (
            <label key={mode} className="inline-flex items-center gap-2 text-sm">
              <input
                type="radio"
                name={`${inputId}-match`}
                value={mode}
                checked={matchMode === mode}
                onChange={() => onMatchModeChange(mode)}
                data-testid={`keyword-match-${mode}`}
              />
              {t(`modes.${mode}`)}
            </label>
          ))}
        </div>
      </fieldset>

      {dictionaries.length > 0 ? (
        <div className="space-y-2">
          <p className="text-sm font-medium">{t('importDictionary')}</p>
          <div className="flex flex-wrap gap-2">
            {dictionaries.map((dict) => (
              <Button
                key={dict.id}
                type="button"
                size="sm"
                variant="outline"
                disabled={disabled}
                data-testid={`keyword-import-${dict.id}`}
                onClick={() => onChange(mergeKeywords(value, dict.keywords).slice(0, maxItems))}
              >
                {dict.name}
              </Button>
            ))}
          </div>
        </div>
      ) : null}
    </div>
  );
}
