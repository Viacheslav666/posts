'use client';

import { tiptapJsonToCanonicalHtml, type TiptapNode } from '@posts/domain';
import Placeholder from '@tiptap/extension-placeholder';
import { EditorContent, useEditor, type Editor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { useCallback, useEffect, useImperativeHandle, useState, forwardRef } from 'react';

import { Spoiler } from '../lib/spoiler-extension';
import { openBraceQuery, type EditorVariableDef } from '../lib/variables';
import { EditorToolbar } from './editor-toolbar';
import { VariablesSuggestion } from './variables-suggestion';

export type TipTapEditorHandle = {
  insertText: (text: string) => void;
  getEditor: () => Editor | null;
};

type TipTapEditorProps = {
  initialDoc?: TiptapNode | null;
  placeholder?: string;
  onChange: (payload: { html: string; doc: TiptapNode }) => void;
  editable?: boolean;
  'aria-label'?: string;
  showVariables?: boolean;
};

function textBeforeCursor(editor: Editor): string {
  const { from } = editor.state.selection;
  return editor.state.doc.textBetween(Math.max(0, from - 80), from, '\n', '\n');
}

function replaceOpenBrace(editor: Editor, insert: string) {
  const { from } = editor.state.selection;
  const before = textBeforeCursor(editor);
  const match = before.match(/\{[^{}\n]*$/);
  if (!match) {
    editor.chain().focus().insertContent(insert).run();
    return;
  }
  const start = from - match[0].length;
  editor
    .chain()
    .focus()
    .deleteRange({ from: start, to: from })
    .insertContent(insert)
    .run();
}

export const TipTapEditor = forwardRef<TipTapEditorHandle, TipTapEditorProps>(
  function TipTapEditor(
    {
      initialDoc,
      placeholder,
      onChange,
      editable = true,
      'aria-label': ariaLabel,
      showVariables = true,
    },
    ref,
  ) {
    const [suggestionQuery, setSuggestionQuery] = useState<string | null>(null);
    const [activeIndex, setActiveIndex] = useState(0);

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
        handleKeyDown: (_view, event) => {
          if (suggestionQuery === null) {
            return false;
          }
          if (event.key === 'Escape') {
            setSuggestionQuery(null);
            return true;
          }
          if (event.key === 'ArrowDown') {
            setActiveIndex((i) => i + 1);
            return true;
          }
          if (event.key === 'ArrowUp') {
            setActiveIndex((i) => Math.max(0, i - 1));
            return true;
          }
          if (event.key === 'Enter' || event.key === 'Tab') {
            // Selection handled via click / parent; close on enter without item
            return false;
          }
          return false;
        },
      },
      onUpdate: ({ editor: current }) => {
        const doc = current.getJSON() as TiptapNode;
        onChange({
          doc,
          html: tiptapJsonToCanonicalHtml(doc),
        });
        const query = openBraceQuery(textBeforeCursor(current));
        setSuggestionQuery(query);
        setActiveIndex(0);
      },
      onSelectionUpdate: ({ editor: current }) => {
        const query = openBraceQuery(textBeforeCursor(current));
        setSuggestionQuery(query);
      },
    });

    useImperativeHandle(
      ref,
      () => ({
        insertText: (text: string) => {
          editor?.chain().focus().insertContent(text).run();
        },
        getEditor: () => editor,
      }),
      [editor],
    );

    useEffect(() => {
      if (!editor) {
        return;
      }
      editor.setEditable(editable);
    }, [editor, editable]);

    const insertVariable = useCallback(
      (item: EditorVariableDef) => {
        if (!editor) return;
        replaceOpenBrace(editor, item.insert);
        setSuggestionQuery(null);
      },
      [editor],
    );

    return (
      <div className="overflow-hidden rounded-md border border-input bg-background">
        <EditorToolbar
          editor={editor}
          showVariables={showVariables && editable}
          onInsertText={(text) => {
            editor?.chain().focus().insertContent(text).run();
          }}
        />
        <div className="relative">
          {suggestionQuery !== null && editable ? (
            <VariablesSuggestion
              query={suggestionQuery}
              activeIndex={activeIndex}
              onSelect={insertVariable}
            />
          ) : null}
          <EditorContent editor={editor} />
        </div>
      </div>
    );
  },
);
