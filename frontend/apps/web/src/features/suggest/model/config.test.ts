import { describe, expect, it } from 'vitest';

import {
  buildSuggestConfig,
  defaultFormValues,
  formValuesFromSettings,
  matchModeFromApi,
  matchModeToApi,
  parseSuggestConfig,
  withConfigDefaults,
} from './config';
import { scheduleWindowToSuggest, suggestScheduleToWindow } from './schedule-map';

describe('suggest config helpers', () => {
  it('applies SuggestConfigSupport-like defaults', () => {
    const config = withConfigDefaults({});
    expect(config.config_version).toBe(1);
    expect(config.filters.keyword_match).toBe('contains');
    expect(config.dedup.enabled).toBe(true);
    expect(config.reject_policy).toBe('delete');
    expect(config.publish_edit_mode).toBe('wall_post_edit');
    expect(config.ads_pause.enabled).toBe(true);
    expect(config.schedule.windows[0]?.from).toBe('09:00');
  });

  it('maps keyword match contains ↔ substring', () => {
    expect(matchModeToApi('substring')).toBe('contains');
    expect(matchModeToApi('whole_word')).toBe('whole_word');
    expect(matchModeFromApi('contains')).toBe('substring');
    expect(matchModeFromApi('whole_word')).toBe('whole_word');
  });

  it('round-trips form values through build/parse without loss', () => {
    const values = {
      ...defaultFormValues('019a0000-chan-7000-8000-000000000001'),
      mode: 'hybrid' as const,
      stopWords: ['спам', 'казино'],
      keywordInclude: ['новость'],
      keywordMatchMode: 'whole_word' as const,
      requireText: true,
      perAuthorDay: 3,
      globalHour: 10,
      scheduleEnabled: true,
      scheduleDays: [1, 3, 5],
      scheduleIntervals: [{ start: '10:00', end: '18:00' }],
      scheduleTimezone: 'Asia/Yekaterinburg',
      minIntervalMinutes: 15,
      dedupSimilarityThreshold: 0.85,
      rejectPolicy: 'keep' as const,
      publishEditMode: 'fallback_new_post' as const,
      enrichmentHashtags: ['#posts'],
      enrichmentAppendText: '— команда Posts',
      adsPauseAfterMinutes: 90,
    };

    const config = buildSuggestConfig(values);
    expect(config.filters.keyword_match).toBe('whole_word');
    expect(config.filters.stop_words).toEqual(['спам', 'казино']);
    expect(config.schedule.enabled).toBe(true);
    expect(config.schedule.windows[0]).toMatchObject({
      days_of_week: [1, 3, 5],
      from: '10:00',
      to: '18:00',
    });
    expect(config.publish_edit_mode).toBe('fallback_new_post');

    const restored = formValuesFromSettings({
      id: 's1',
      channelId: values.channelId,
      mode: values.mode,
      status: 'active',
      config: config as never,
      version: 1,
    });

    expect(restored.stopWords).toEqual(['спам', 'казино']);
    expect(restored.keywordInclude).toEqual(['новость']);
    expect(restored.keywordMatchMode).toBe('whole_word');
    expect(restored.requireText).toBe(true);
    expect(restored.perAuthorDay).toBe(3);
    expect(restored.globalHour).toBe(10);
    expect(restored.scheduleEnabled).toBe(true);
    expect(restored.scheduleTimezone).toBe('Asia/Yekaterinburg');
    expect(restored.scheduleDays).toEqual([1, 3, 5]);
    expect(restored.scheduleIntervals[0]).toEqual({ start: '10:00', end: '18:00' });
    expect(restored.minIntervalMinutes).toBe(15);
    expect(restored.dedupSimilarityThreshold).toBe(0.85);
    expect(restored.rejectPolicy).toBe('keep');
    expect(restored.publishEditMode).toBe('fallback_new_post');
    expect(restored.enrichmentHashtags).toEqual(['#posts']);
    expect(restored.enrichmentAppendText).toBe('— команда Posts');
    expect(restored.adsPauseAfterMinutes).toBe(90);
  });

  it('parseSuggestConfig is alias for withConfigDefaults', () => {
    expect(parseSuggestConfig({ reject_policy: 'keep' }).reject_policy).toBe('keep');
  });

  it('maps ISO days 1-7 ↔ JS weekdays for schedule windows', () => {
    const backend = scheduleWindowToSuggest(
      {
        days: [0, 1, 6],
        intervals: [{ start: '09:00', end: '12:00' }],
        timezone: 'Europe/Moscow',
      },
      true,
      { minIntervalMinutes: 5 },
    );
    expect(backend.windows[0]?.days_of_week).toEqual([1, 6, 7]);
    expect(backend.min_interval_minutes).toBe(5);

    const ui = suggestScheduleToWindow(backend);
    expect(ui.window.days).toEqual([0, 1, 6]);
    expect(ui.enabled).toBe(true);
  });

  it('default form uses manual mode and Moscow timezone', () => {
    const values = defaultFormValues();
    expect(values.mode).toBe('manual');
    expect(values.scheduleTimezone).toBe('Europe/Moscow');
    expect(values.dedupEnabled).toBe(true);
  });
});
