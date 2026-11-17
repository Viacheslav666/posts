'use client';

import { tiptapJsonToCanonicalHtml, type TiptapNode } from '@posts/domain';
import Placeholder from '@tiptap/extension-placeholder';
import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { useEffect } from 'react';

import { Spoiler } from '../lib/spoiler-extension';
import { EditorToolbar } from './editor-toolbar';

type TipTapEditorProps = {
  initialDoc?: TiptapNode | null;
  placeholder?: string;
  onChange: (payload: { html: string; doc: TiptapNode }) => void;
  editable?: boolean;
  'aria-label'?: string;
};

export function TipTapEditor({
  initialDoc,
  placeholder,
  onChange,
  editable = true,
  'aria-label': ariaLabel,
}: TipTapEditorProps) {
  const editor = useEditor({
    immediatelyRender: false,
    editable,
    extensions: [
      StarterKit.configure({
        heading: false,
        codeBlock: false,
        link: {
          openOnClick: false,
          HTMLAttributes: { rel: 'noopener noreferrer', target: '_blank' },
        },
      }),
      Spoiler,
      Placeholder.configure({
        placeholder: placeholder ?? '',
      }),
    ],
    content: initialDoc ?? { type: 'doc', content: [{ type: 'paragraph' }] },
    editorProps: {
      attributes: {
        role: 'textbox',
        'aria-multiline': 'true',
        'aria-label': ariaLabel ?? 'Post editor',
        class:
          'min-h-[220px] max-h-[480px] overflow-y-auto px-3 py-2 text-sm leading-relaxed outline-none focus-visible:ring-0',
      },
    },
    onUpdate: ({ editor: current }) => {
      const doc = current.getJSON() as TiptapNode;
      onChange({
        doc,
        html: tiptapJsonToCanonicalHtml(doc),
      });
    },
  });

  useEffect(() => {
    if (!editor) {
      return;
    }
    editor.setEditable(editable);
  }, [editor, editable]);

  return (
    <div className="overflow-hidden rounded-md border border-input bg-background">
      <EditorToolbar editor={editor} />
      <EditorContent editor={editor} />
    </div>
  );
}
