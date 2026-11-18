'use client';

import { Button } from '@posts/ui';
import { Braces, Link2, Shuffle } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { useId, useState } from 'react';

import { EDITOR_VARIABLES, type EditorVariableDef } from '../lib/variables';
import { SPINTAX_SNIPPET } from '../lib/spintax';

type VariablesMenuProps = {
  onInsert: (text: string) => void;
};

export function VariablesMenu({ onInsert }: VariablesMenuProps) {
  const t = useTranslations('editor.variables');
  const menuId = useId();
  const [open, setOpen] = useState(false);

  const insert = (item: EditorVariableDef) => {
    onInsert(item.insert);
    setOpen(false);
  };

  return (
    <div className="relative" data-testid="variables-menu">
      <Button
        type="button"
        size="sm"
        variant="outline"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen((value) => !value)}
        data-testid="variables-menu-trigger"
      >
        <Braces className="mr-1.5 h-4 w-4" aria-hidden />
        {t('button')}
      </Button>
      {open ? (
        <div
          id={menuId}
          role="menu"
          aria-label={t('menuLabel')}
          className="absolute left-0 z-30 mt-1 max-h-72 w-80 overflow-y-auto rounded-md border border-border bg-popover p-1 shadow-md"
          data-testid="variables-menu-list"
        >
          {EDITOR_VARIABLES.filter((item) => item.id !== 'rand' && item.id !== 'hyperlink').map(
            (item) => (
              <button
                key={item.id}
                type="button"
                role="menuitem"
                className="flex w-full flex-col items-start gap-0.5 rounded-sm px-2 py-1.5 text-left text-sm hover:bg-accent"
                onClick={() => insert(item)}
              >
                <span className="font-mono text-xs">{item.label}</span>
                <span className="text-xs text-muted-foreground">
                  {t(`items.${item.descriptionKey}`)}
                </span>
              </button>
            ),
          )}
          <div className="my-1 border-t border-border" />
          <button
            type="button"
            role="menuitem"
            className="flex w-full items-start gap-2 rounded-sm px-2 py-1.5 text-left text-sm hover:bg-accent"
            onClick={() => {
              onInsert(SPINTAX_SNIPPET);
              setOpen(false);
            }}
            data-testid="insert-rand"
          >
            <Shuffle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
            <span>
              <span className="block font-medium">{t('insertRand')}</span>
              <span className="text-xs text-muted-foreground">{t('items.rand')}</span>
            </span>
          </button>
          <button
            type="button"
            role="menuitem"
            className="flex w-full items-start gap-2 rounded-sm px-2 py-1.5 text-left text-sm hover:bg-accent"
            onClick={() => {
              onInsert('{hyperlink:Текст|https://example.com}');
              setOpen(false);
            }}
            data-testid="insert-hyperlink"
          >
            <Link2 className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
            <span>
              <span className="block font-medium">{t('insertHyperlink')}</span>
              <span className="text-xs text-muted-foreground">{t('items.hyperlink')}</span>
            </span>
          </button>
        </div>
      ) : null}
    </div>
  );
}
