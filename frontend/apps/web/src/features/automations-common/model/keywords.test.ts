import { describe, expect, it } from 'vitest';

import { mergeKeywords, parseKeywordPaste, removeKeyword } from './keywords';

describe('keywords model', () => {
  it('parses comma and newline lists', () => {
    expect(parseKeywordPaste('spam, бот\nреклама;promo')).toEqual([
      'spam',
      'бот',
      'реклама',
      'promo',
    ]);
  });

  it('dedupes case-insensitively on paste', () => {
    expect(parseKeywordPaste('Spam, spam, SPAM')).toEqual(['Spam']);
  });

  it('merges without duplicates', () => {
    expect(mergeKeywords(['а'], ['А', 'б'])).toEqual(['а', 'б']);
  });

  it('removes by case-insensitive match', () => {
    expect(removeKeyword(['Hello', 'world'], 'hello')).toEqual(['world']);
  });
});
