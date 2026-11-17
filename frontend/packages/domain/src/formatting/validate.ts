import { stripTags, unicodeLength } from './html-utils';
import { renderFromCanonicalHtml } from './render';
import type { NetworkId } from './types';

export type NetworkTextCapability = {
  id: NetworkId;
  /** Official max length; null/undefined = unknown (do not block). */
  maxChars?: number | null;
  captionMaxChars?: number | null;
  formattingMode?: string | null;
};

export type ValidationIssue = {
  severity: 'error' | 'warning';
  code: string;
  network: NetworkId;
  message: string;
};

const NETWORK_LABEL: Record<NetworkId, string> = {
  vk: 'VK',
  tg: 'Telegram',
  ok: 'OK',
  max: 'Max',
  rutube: 'Rutube',
};

/**
 * Client-side validation against `GET /networks/capabilities` snapshot.
 * Backend remains authoritative; this blocks schedule UX for known hard limits.
 */
export function validateCanonicalHtml(
  canonicalHtml: string,
  capabilities: NetworkTextCapability[],
  options?: { selectedNetworks?: NetworkId[]; hasMedia?: boolean },
): ValidationIssue[] {
  const selected = new Set(options?.selectedNetworks ?? capabilities.map((c) => c.id));
  const rendered = renderFromCanonicalHtml(canonicalHtml);
  const issues: ValidationIssue[] = [];

  for (const cap of capabilities) {
    if (!selected.has(cap.id)) {
      continue;
    }

    const plainLength =
      cap.id === 'tg'
        ? rendered.telegram.plain_length
        : cap.id === 'max'
          ? rendered.max.plain_length
          : unicodeLength(stripTags(canonicalHtml));

    if (typeof cap.maxChars === 'number' && plainLength > cap.maxChars) {
      issues.push({
        severity: 'error',
        code: 'limit:max_chars',
        network: cap.id,
        message: `${NETWORK_LABEL[cap.id]}: превышен лимит ${cap.maxChars} символов (${plainLength}).`,
      });
    }

    if (
      options?.hasMedia &&
      typeof cap.captionMaxChars === 'number' &&
      plainLength > cap.captionMaxChars
    ) {
      issues.push({
        severity: 'error',
        code: 'limit:caption_max_chars',
        network: cap.id,
        message: `${NETWORK_LABEL[cap.id]}: превышен лимит подписи ${cap.captionMaxChars} символов (${plainLength}).`,
      });
    }

    if (
      (cap.id === 'vk' || cap.id === 'ok') &&
      (cap.formattingMode === 'plain' || !cap.formattingMode) &&
      rendered.vk.formatting_stripped
    ) {
      issues.push({
        severity: 'warning',
        code: 'formatting_stripped',
        network: cap.id,
        message: rendered.vk.warning ?? 'Форматирование будет потеряно.',
      });
    }

    if (cap.id === 'tg') {
      for (const w of rendered.telegram.warnings) {
        if (w.startsWith('unsupported:')) {
          issues.push({
            severity: 'warning',
            code: w,
            network: 'tg',
            message: `Telegram: тег не поддерживается (${w.replace('unsupported:', '')}).`,
          });
        }
      }
    }

    if (cap.id === 'max') {
      for (const w of rendered.max.warnings) {
        if (w.startsWith('unsupported:')) {
          issues.push({
            severity: 'warning',
            code: w,
            network: 'max',
            message: `Max: тег не поддерживается (${w.replace('unsupported:', '')}).`,
          });
        }
      }
    }
  }

  return issues;
}

export function hasBlockingValidationErrors(issues: ValidationIssue[]): boolean {
  return issues.some((issue) => issue.severity === 'error');
}
