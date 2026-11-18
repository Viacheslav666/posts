import { describe, expect, it } from 'vitest';

import {
  mapErirErrorMessage,
  ordEridStatusLabelRu,
  ordReportStatusLabelRu,
  RAW_ORD_REPORT_STATUS_ENUMS,
} from './report-status-labels';

describe('ord report status labels', () => {
  it('maps known statuses to Russian', () => {
    expect(ordReportStatusLabelRu('draft')).toBe('Черновик');
    expect(ordReportStatusLabelRu('submitted')).toBe('Отправлен в ОРД');
    expect(ordReportStatusLabelRu('error')).toBe('Ошибка ЕРИР');
    expect(ordEridStatusLabelRu('not_found')).toBe('Не найден в ОРД');
  });

  it('maps every RAW enum in at least one label table', () => {
    for (const status of RAW_ORD_REPORT_STATUS_ENUMS) {
      const label = ordReportStatusLabelRu(status);
      const eridLabel = ordEridStatusLabelRu(status);
      expect(label !== status || eridLabel !== status).toBe(true);
    }
  });
});

describe('mapErirErrorMessage', () => {
  it('translates common ERIR / ORD failure patterns', () => {
    expect(mapErirErrorMessage('ERIR validation failed: bad stats')).toMatch(/ЕРИР|ОРД/);
    expect(mapErirErrorMessage('request timed out')).toMatch(/Таймаут/);
    expect(mapErirErrorMessage('API key rejected')).toMatch(/авторизации/);
    expect(mapErirErrorMessage(null)).toBeNull();
  });
});
