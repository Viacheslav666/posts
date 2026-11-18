import { describe, expect, it } from 'vitest';

import {
  POST_STATUS_LABELS_RU,
  PUBLICATION_STATE_LABELS_RU,
  postStatusLabelRu,
  publicationStateLabelRu,
  RAW_STATUS_ENUMS,
} from './status-labels';

describe('status labels (RU)', () => {
  it('maps core post statuses to Russian', () => {
    expect(postStatusLabelRu('scheduled')).toBe('Запланирован');
    expect(postStatusLabelRu('failed')).toBe('Ошибка');
    expect(postStatusLabelRu('published')).toBe('Опубликован');
    expect(postStatusLabelRu('draft')).toBe('Черновик');
  });

  it('maps publication states to Russian', () => {
    expect(publicationStateLabelRu('failed_retryable')).toBe('Ошибка (повтор)');
    expect(publicationStateLabelRu('succeeded')).toBe('Успешно');
  });

  it('never uses English enum as the label value', () => {
    for (const [key, label] of Object.entries(POST_STATUS_LABELS_RU)) {
      expect(label.toLowerCase()).not.toBe(key);
      expect(label).not.toMatch(/^[a-z_]+$/);
    }
    for (const [key, label] of Object.entries(PUBLICATION_STATE_LABELS_RU)) {
      expect(label.toLowerCase()).not.toBe(key);
      expect(label).not.toMatch(/^[a-z_]+$/);
    }
  });

  it('lists every enum for UI regression scans', () => {
    expect(RAW_STATUS_ENUMS).toContain('scheduled');
    expect(RAW_STATUS_ENUMS).toContain('failed_permanent');
  });
});
