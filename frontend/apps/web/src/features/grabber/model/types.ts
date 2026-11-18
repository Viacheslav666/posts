export type IngestRuleType = 'grabber' | 'rss' | 'website';
export type PublishMode = 'moderate' | 'auto' | 'draft';
export type ScheduleMode = 'immediate' | 'delay' | 'slot';
export type RuleStatus = 'draft' | 'active' | 'paused' | 'auto_paused' | 'archived';
export type ItemStatus =
  | 'discovered'
  | 'filtered_out'
  | 'suppressed_dedup'
  | 'pending_moderation'
  | 'rejected'
  | 'approved'
  | 'scheduled'
  | 'published'
  | 'publish_failed'
  | 'failed_validation'
  | 'failed_ingest';

export const SCAN_INTERVALS = [15, 30, 60, 120, 180, 360, 720, 1440] as const;

export const DEFAULT_ATTRIBUTION_TEMPLATE = 'Источник: {source_name} — {source_url}';

export function isWebsiteSourceEnabled(): boolean {
  if (process.env.NEXT_PUBLIC_GRABBER_WEBSITE_ENABLED === 'true') {
    return true;
  }
  if (process.env.NEXT_PUBLIC_GRABBER_WEBSITE_ENABLED === 'false') {
    return false;
  }
  // MSW / local default: BE-17b is on main; keep website selectable for demos.
  return process.env.NEXT_PUBLIC_API_MOCKING === 'true' || process.env.NODE_ENV === 'development';
}
