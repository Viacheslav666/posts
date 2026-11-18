'use client';

import { useTranslations } from 'next-intl';

import { filterVariables, type EditorVariableDef } from '../lib/variables';

type VariablesSuggestionProps = {
  query: string;
  activeIndex: number;
  onSelect: (item: EditorVariableDef) => void;
};

export function VariablesSuggestion({
  query,
  activeIndex,
  onSelect,
}: VariablesSuggestionProps) {
  const t = useTranslations('editor.variables');
  const items = filterVariables(query);

  if (items.length === 0) {
    return null;
  }

  return (
    <ul
      role="listbox"
      aria-label={t('autocompleteLabel')}
      className="absolute bottom-full left-2 z-20 mb-1 max-h-56 w-72 overflow-y-auto rounded-md border border-border bg-popover p-1 shadow-md"
      data-testid="variables-suggestion"
    >
      {items.map((item, index) => (
        <li key={item.id} role="option" aria-selected={index === activeIndex}>
          <button
            type="button"
            className={`flex w-full flex-col items-start gap-0.5 rounded-sm px-2 py-1.5 text-left text-sm ${
              index === activeIndex ? 'bg-accent' : 'hover:bg-accent/70'
            }`}
            onMouseDown={(event) => {
              event.preventDefault();
              onSelect(item);
            }}
          >
            <span className="font-mono text-xs">{item.label}</span>
            <span className="text-xs text-muted-foreground">
              {t(`items.${item.descriptionKey}`)}
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}
