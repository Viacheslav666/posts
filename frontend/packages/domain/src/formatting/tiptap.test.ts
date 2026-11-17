import { describe, expect, it } from 'vitest';

import { renderFromTiptap, tiptapJsonToCanonicalHtml } from './tiptap';

describe('tiptapJsonToCanonicalHtml', () => {
  it('maps bold/italic/link marks', () => {
    const html = tiptapJsonToCanonicalHtml({
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            { type: 'text', text: 'Hello ', marks: [{ type: 'bold' }] },
            {
              type: 'text',
              text: 'world',
              marks: [{ type: 'italic' }, { type: 'link', attrs: { href: 'https://example.com' } }],
            },
          ],
        },
      ],
    });
    expect(html).toBe('<b>Hello </b><a href="https://example.com"><i>world</i></a>');
  });

  it('renders through network pipeline', () => {
    const result = renderFromTiptap({
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [{ type: 'text', text: 'bold', marks: [{ type: 'bold' }] }],
        },
      ],
    });
    expect(result.telegram.html).toBe('<b>bold</b>');
    expect(result.vk.plain).toBe('bold');
    expect(result.vk.formatting_stripped).toBe(true);
  });
});
