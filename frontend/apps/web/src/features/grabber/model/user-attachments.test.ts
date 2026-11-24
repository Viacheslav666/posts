import { describe, expect, it } from 'vitest';

import { USER_ATTACHMENTS_MAX_LENGTH, validateUserAttachments } from './user-attachments';

describe('validateUserAttachments', () => {
  it('accepts blank and plain comma-separated references', () => {
    expect(validateUserAttachments('')).toBeNull();
    expect(validateUserAttachments('   ')).toBeNull();
    expect(validateUserAttachments('photo-123_456, video-123_789')).toBeNull();
  });

  it('accepts [rand] and [keyTextEntry] blocks', () => {
    expect(validateUserAttachments('[rand]photo-1_2|photo-1_3,video-1_4[/rand]')).toBeNull();
    expect(
      validateUserAttachments(
        'photo-1_1 [keyTextEntry]photo-1_2,photo-1_3|прикол,смеят[/keyTextEntry]',
      ),
    ).toBeNull();
    expect(validateUserAttachments('[RAND]photo-1_2|photo-1_3[/RAND]')).toBeNull();
  });

  it('reports invalid references', () => {
    expect(validateUserAttachments('photo-1_2, picture1')).toBe('userAttachmentsInvalid');
    expect(validateUserAttachments('[rand]photo-1_2|bad[/rand]')).toBe('userAttachmentsInvalid');
    expect(validateUserAttachments('[keyTextEntry]oops|слово[/keyTextEntry]')).toBe(
      'userAttachmentsInvalid',
    );
  });

  it('requires two [rand] variants and a keyword list in [keyTextEntry]', () => {
    expect(validateUserAttachments('[rand]photo-1_2[/rand]')).toBe('userAttachmentsRandVariants');
    expect(validateUserAttachments('[rand]photo-1_2|[/rand]')).toBe('userAttachmentsRandVariants');
    expect(validateUserAttachments('[keyTextEntry]photo-1_2[/keyTextEntry]')).toBe(
      'userAttachmentsKeyTextFormat',
    );
    expect(validateUserAttachments('[keyTextEntry]photo-1_2| , [/keyTextEntry]')).toBe(
      'userAttachmentsKeyTextFormat',
    );
  });

  it('rejects unclosed, unknown and nested blocks', () => {
    expect(validateUserAttachments('[rand]photo-1_2|photo-1_3')).toBe('userAttachmentsUnclosed');
    expect(validateUserAttachments('[foo]photo-1_2[/foo]')).toBe('userAttachmentsUnclosed');
    expect(
      validateUserAttachments('[rand][rand]photo-1_2|photo-1_3[/rand]|photo-1_4[/rand]'),
    ).toBe('userAttachmentsNested');
  });

  it('limits the length', () => {
    expect(validateUserAttachments('photo-1_2,'.repeat(USER_ATTACHMENTS_MAX_LENGTH))).toBe(
      'userAttachmentsTooLong',
    );
  });
});
