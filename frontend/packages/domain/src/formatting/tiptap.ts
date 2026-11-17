import { renderFromCanonicalHtml } from './render';
import type { FormattingRenderResult, TiptapMark, TiptapNode } from './types';

function escapeText(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function escapeAttr(value: string): string {
  return escapeText(value);
}

function wrapWithMarks(inner: string, marks: TiptapMark[] | undefined): string {
  if (!marks?.length) {
    return inner;
  }
  let out = inner;
  // First mark is innermost; later marks wrap outward (Tiptap convention).
  for (const mark of marks) {
    switch (mark.type) {
      case 'bold':
        out = `<b>${out}</b>`;
        break;
      case 'italic':
        out = `<i>${out}</i>`;
        break;
      case 'underline':
        out = `<u>${out}</u>`;
        break;
      case 'strike':
        out = `<s>${out}</s>`;
        break;
      case 'code':
        out = `<code>${out}</code>`;
        break;
      case 'link': {
        const href = String(mark.attrs?.href ?? '');
        out = `<a href="${escapeAttr(href)}">${out}</a>`;
        break;
      }
      case 'spoiler':
        out = `<tg-spoiler>${out}</tg-spoiler>`;
        break;
      default:
        break;
    }
  }
  return out;
}

function renderNode(node: TiptapNode): string {
  switch (node.type) {
    case 'text':
      return wrapWithMarks(escapeText(node.text ?? ''), node.marks);
    case 'hardBreak':
      return '<br/>';
    case 'paragraph': {
      const inner = (node.content ?? []).map(renderNode).join('');
      return inner;
    }
    case 'blockquote': {
      const expandable = Boolean(node.attrs?.expandable);
      const inner = (node.content ?? []).map(renderNode).join('');
      return expandable
        ? `<blockquote expandable>${inner}</blockquote>`
        : `<blockquote>${inner}</blockquote>`;
    }
    case 'codeBlock': {
      const inner = (node.content ?? []).map((child) => escapeText(child.text ?? '')).join('');
      return `<pre>${inner}</pre>`;
    }
    case 'heading': {
      const level = Math.min(6, Math.max(1, Number(node.attrs?.level ?? 1)));
      const inner = (node.content ?? []).map(renderNode).join('');
      return `<h${level}>${inner}</h${level}>`;
    }
    case 'doc':
      return (node.content ?? [])
        .map(renderNode)
        .filter((part, index, arr) => !(part === '' && index < arr.length - 1))
        .join('\n');
    default:
      return (node.content ?? []).map(renderNode).join('');
  }
}

/**
 * Convert Tiptap JSON document into canonical HTML used by network renderers / fixtures.
 * Adjacent top-level block nodes are joined with newlines (paragraph separation).
 */
export function tiptapJsonToCanonicalHtml(doc: TiptapNode): string {
  if (doc.type !== 'doc') {
    return renderNode(doc);
  }
  const blocks = doc.content ?? [];
  let joined = blocks
    .map((block, index) => {
      const html = renderNode(block);
      if (block.type === 'paragraph' && index < blocks.length - 1) {
        // Separate paragraphs visually for plain networks; keep single-block editor HTML compact.
        return html === '' ? '<br/>' : `${html}<br/>`;
      }
      return html;
    })
    .join('');
  while (joined.endsWith('<br/>')) {
    joined = joined.slice(0, -'<br/>'.length);
  }
  return joined;
}

/** Tiptap JSON → network preview payloads. */
export function renderFromTiptap(doc: TiptapNode): FormattingRenderResult {
  return renderFromCanonicalHtml(tiptapJsonToCanonicalHtml(doc));
}

/** Minimal helper to build a text-only Tiptap doc (tests / seed). */
export function plainTextToTiptapDoc(text: string): TiptapNode {
  if (!text) {
    return { type: 'doc', content: [{ type: 'paragraph' }] };
  }
  const lines = text.split('\n');
  return {
    type: 'doc',
    content: lines.map((line) =>
      line
        ? { type: 'paragraph', content: [{ type: 'text', text: line }] }
        : { type: 'paragraph' },
    ),
  };
}
