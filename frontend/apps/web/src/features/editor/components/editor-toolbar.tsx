'use client';

import { Button } from '@posts/ui';
import type { Editor } from '@tiptap/react';
import {
  Bold,
  Code,
  Italic,
  Link2,
  Quote,
  Strikethrough,
  Underline as UnderlineIcon,
  EyeOff,
} from 'lucide-react';
import { useTranslations } from 'next-intl';
import type { ReactNode } from 'react';

import { VariablesMenu } from './variables-menu';

type EditorToolbarProps = {
  editor: Editor | null;
  showVariables?: boolean;
  onInsertText?: (text: string) => void;
};

function ToolButton({
  pressed,
  label,
  onClick,
  children,
}: {
  pressed: boolean;
  label: string;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <Button
      type="button"
      size="icon"
      variant={pressed ? 'secondary' : 'ghost'}
      aria-label={label}
      aria-pressed={pressed}
      onClick={onClick}
      className="h-8 w-8"
    >
      {children}
    </Button>
  );
}

export function EditorToolbar({
  editor,
  showVariables = false,
  onInsertText,
}: EditorToolbarProps) {
  const t = useTranslations('editor.toolbar');

  if (!editor) {
    return null;
  }

  const setLink = () => {
    const previous = editor.getAttributes('link').href as string | undefined;
    const url = window.prompt(t('linkPrompt'), previous ?? 'https://');
    if (url === null) {
      return;
    }
    if (url === '') {
      editor.chain().focus().extendMarkRange('link').unsetLink().run();
      return;
    }
    editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
  };

  return (
    <div
      role="toolbar"
      aria-label={t('label')}
      className="flex flex-wrap items-center gap-0.5 border-b border-border p-1"
    >
      <ToolButton
        label={t('bold')}
        pressed={editor.isActive('bold')}
        onClick={() => editor.chain().focus().toggleBold().run()}
      >
        <Bold aria-hidden className="h-4 w-4" />
      </ToolButton>
      <ToolButton
        label={t('italic')}
        pressed={editor.isActive('italic')}
        onClick={() => editor.chain().focus().toggleItalic().run()}
      >
        <Italic aria-hidden className="h-4 w-4" />
      </ToolButton>
      <ToolButton
        label={t('underline')}
        pressed={editor.isActive('underline')}
        onClick={() => editor.chain().focus().toggleUnderline().run()}
      >
        <UnderlineIcon aria-hidden className="h-4 w-4" />
      </ToolButton>
      <ToolButton
        label={t('strike')}
        pressed={editor.isActive('strike')}
        onClick={() => editor.chain().focus().toggleStrike().run()}
      >
        <Strikethrough aria-hidden className="h-4 w-4" />
      </ToolButton>
      <ToolButton
        label={t('code')}
        pressed={editor.isActive('code')}
        onClick={() => editor.chain().focus().toggleCode().run()}
      >
        <Code aria-hidden className="h-4 w-4" />
      </ToolButton>
      <ToolButton
        label={t('spoiler')}
        pressed={editor.isActive('spoiler')}
        onClick={() => editor.chain().focus().toggleMark('spoiler').run()}
      >
        <EyeOff aria-hidden className="h-4 w-4" />
      </ToolButton>
      <ToolButton
        label={t('quote')}
        pressed={editor.isActive('blockquote')}
        onClick={() => editor.chain().focus().toggleBlockquote().run()}
      >
        <Quote aria-hidden className="h-4 w-4" />
      </ToolButton>
      <ToolButton label={t('link')} pressed={editor.isActive('link')} onClick={setLink}>
        <Link2 aria-hidden className="h-4 w-4" />
      </ToolButton>
      {showVariables && onInsertText ? (
        <div className="ml-1">
          <VariablesMenu onInsert={onInsertText} />
        </div>
      ) : null}
    </div>
  );
}
