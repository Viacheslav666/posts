import { describe, expect, it } from 'vitest';

import {
  applyClonedConfigToForm,
  buildGrabberConfig,
  defaultFormValues,
  formValuesFromRule,
  isPrivateOrMetadataUrl,
} from './config';
import { wordReplacementsFromText } from './schemas';

describe('grabber config helpers', () => {
  it('builds moderate config by default', () => {
    const config = buildGrabberConfig({
      ...defaultFormValues('019a0000-chan-7000-8000-000000000001'),
      name: 'Rule',
      sourceIdsText: '-1, club2',
      userResponsibilityAccepted: true,
    });
    expect(config.publishMode).toBe('moderate');
    expect(config.targetMode).toBe('wall_now');
    expect(config.sourceIds).toEqual(['-1', '-2']);
    expect(config.configVersion).toBe(3);
    expect(config.filters).toMatchObject({ max_age_days: 60 });
    expect(config.firstComment).toMatchObject({ pending_be23: false, enabled: false });
    expect(config.aiRewriteEnabled).toBe(false);
  });

  it('round-trips BE-27 filter/transform/schedule/limits fields without loss', () => {
    const values = {
      ...defaultFormValues('019a0000-chan-7000-8000-000000000001'),
      name: 'Parity',
      sourceIdsText: '-100',
      userResponsibilityAccepted: true,
      excludeLinks: true,
      skipIfHomoglyphs: true,
      skipIfCaps: true,
      skipIfNoPhoto: true,
      skipIfHasPoll: true,
      maxAgeDays: 7,
      stopWordsText: 'реклама, купи',
      stripHashtags: true,
      stripPhones: true,
      stripPhoto: true,
      wordReplacementsText: 'foo=bar\nold→new',
      removeKeywordsText: 'spam',
      scheduleEnabled: true,
      scheduleTimezone: 'Asia/Yekaterinburg',
      scheduleDays: [1, 3, 5],
      hoursFrom: 9,
      hoursTo: 18,
      rateLimitPerSourcePerDay: 3,
      minIntervalMinutes: 15,
      firstCommentEnabled: true,
      firstCommentText: 'Привет [name_of_the_source]',
      prependText: 'Источник: [id_of_the_source]',
      appendText: '[rand]a|b[/rand]',
      attachmentsMode: 'carousel' as const,
      linkToSource: true,
      similarityThreshold: 0.85,
      similarityLookback: 12,
    };

    const config = buildGrabberConfig(values);
    const restored = formValuesFromRule({
      id: 'r1',
      name: values.name,
      type: 'grabber',
      targetChannelId: values.targetChannelId,
      status: 'active',
      userResponsibilityAcceptedAt: new Date().toISOString(),
      config,
      version: 1,
    });

    expect(restored.excludeLinks).toBe(true);
    expect(restored.skipIfHomoglyphs).toBe(true);
    expect(restored.skipIfNoPhoto).toBe(true);
    expect(restored.skipIfHasPoll).toBe(true);
    expect(restored.maxAgeDays).toBe(7);
    expect(restored.stopWordsText).toContain('реклама');
    expect(restored.stripHashtags).toBe(true);
    expect(restored.stripPhones).toBe(true);
    expect(restored.stripPhoto).toBe(true);
    expect(restored.wordReplacementsText).toContain('foo=bar');
    expect(restored.removeKeywordsText).toContain('spam');
    expect(restored.scheduleEnabled).toBe(true);
    expect(restored.scheduleTimezone).toBe('Asia/Yekaterinburg');
    expect(restored.scheduleDays).toEqual([1, 3, 5]);
    expect(restored.hoursFrom).toBe(9);
    expect(restored.hoursTo).toBe(18);
    expect(restored.rateLimitPerSourcePerDay).toBe(3);
    expect(restored.minIntervalMinutes).toBe(15);
    expect(restored.firstCommentEnabled).toBe(true);
    expect(restored.firstCommentText).toContain('[name_of_the_source]');
    expect(restored.prependText).toContain('[id_of_the_source]');
    expect(restored.appendText).toContain('[rand]');
    expect(restored.attachmentsMode).toBe('carousel');
    expect(restored.linkToSource).toBe(true);
    expect(restored.similarityThreshold).toBe(0.85);
    expect(restored.similarityLookback).toBe(12);
    expect(restored.publishMode).toBe('moderate');
  });

  it('keeps sources and publish mode when applying cloned config', () => {
    const current = {
      ...defaultFormValues('chan'),
      name: 'Target',
      sourceIdsText: '-999',
      publishMode: 'moderate' as const,
      userResponsibilityAccepted: true,
    };
    const donor = buildGrabberConfig({
      ...defaultFormValues('chan'),
      name: 'Donor',
      sourceIdsText: '-1',
      publishMode: 'auto',
      stripAllText: true,
      minIntervalMinutes: 30,
      userResponsibilityAccepted: true,
    });
    const next = applyClonedConfigToForm(current, donor);
    expect(next.sourceIdsText).toBe('-999');
    expect(next.publishMode).toBe('moderate');
    expect(next.stripAllText).toBe(true);
    expect(next.minIntervalMinutes).toBe(30);
  });

  it('parses word replacement lines', () => {
    expect(wordReplacementsFromText('a=b\nx → y')).toEqual({ a: 'b', x: 'y' });
  });

  it('detects SSRF-prone hosts (TC-GRAB-12)', () => {
    expect(isPrivateOrMetadataUrl('http://127.0.0.1/feed')).toBe(true);
    expect(isPrivateOrMetadataUrl('http://169.254.169.254/latest')).toBe(true);
    expect(isPrivateOrMetadataUrl('http://10.1.2.3/x')).toBe(true);
    expect(isPrivateOrMetadataUrl('https://example.com/feed.xml')).toBe(false);
  });
});
