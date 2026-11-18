import { describe, expect, it } from 'vitest';

import { buildGrabberConfig, isPrivateOrMetadataUrl } from './config';
import { defaultFormValues } from './config';

describe('grabber config helpers', () => {
  it('builds moderate config by default', () => {
    const config = buildGrabberConfig({
      ...defaultFormValues('019a0000-chan-7000-8000-000000000001'),
      name: 'Rule',
      sourceIdsText: '-1, club2',
      userResponsibilityAccepted: true,
    });
    expect(config.publishMode).toBe('moderate');
    expect(config.sourceIds).toEqual(['-1', 'club2']);
  });

  it('detects SSRF-prone hosts (TC-GRAB-12)', () => {
    expect(isPrivateOrMetadataUrl('http://127.0.0.1/feed')).toBe(true);
    expect(isPrivateOrMetadataUrl('http://169.254.169.254/latest')).toBe(true);
    expect(isPrivateOrMetadataUrl('http://10.1.2.3/x')).toBe(true);
    expect(isPrivateOrMetadataUrl('https://example.com/feed.xml')).toBe(false);
  });
});
