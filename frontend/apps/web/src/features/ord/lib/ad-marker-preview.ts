import type { AdMarkingPlacement } from '@posts/api-client';

export const AD_PLACEMENTS: readonly AdMarkingPlacement[] = [
  'vk_menu',
  'text_start',
  'text_end',
  'vk_menu_and_text_end',
  'click_link_start',
  'click_link_end',
] as const;

export const VK_MENU_UNSUPPORTED_WARNING =
  'vk_menu_unsupported: VK wall.post has no native advertiser+erid menu; falling back to text_end and mark_as_ads=1 when publishing to VK';

export type AdMarkerPreview = {
  marker: string;
  text: string;
  warnings: string[];
};

/** Formats the mandatory Russian ad marker line (BE-25 / AdMarker.format). */
export function formatAdMarker(advertiserName: string, inn: string, erid: string): string {
  return `Реклама. ${advertiserName.trim()}. ИНН ${inn.trim()}. erid: ${erid.trim()}`;
}

function prepend(text: string, marker: string): string {
  if (!marker) return text;
  if (!text) return marker;
  return `${marker}\n${text}`;
}

function append(text: string, marker: string): string {
  if (!marker) return text;
  if (!text) return marker;
  return `${text}\n${marker}`;
}

function appendEridQuery(url: string, erid: string): string {
  if (!url || !erid || /erid=/i.test(url)) return url;
  const hash = url.indexOf('#');
  const base = hash >= 0 ? url.slice(0, hash) : url;
  const fragment = hash >= 0 ? url.slice(hash) : '';
  const sep = base.includes('?') ? '&' : '?';
  return `${base}${sep}erid=${erid}${fragment}`;
}

const URL_PATTERN = /\bhttps?:\/\/[^\s<>"')\]]+/gi;

function injectEridIntoUrls(text: string, erid: string): { text: string; changed: boolean } {
  if (!text || !erid) return { text: text ?? '', changed: false };
  let changed = false;
  const next = text.replace(URL_PATTERN, (url) => {
    const withErid = appendEridQuery(url, erid);
    if (withErid !== url) changed = true;
    return withErid;
  });
  return { text: next, changed };
}

/**
 * Client-side placement preview aligned with backend `AdMarker.apply` (post body).
 */
export function previewAdPlacement(options: {
  bodyText: string;
  advertiserName: string;
  inn: string;
  erid: string;
  placement?: AdMarkingPlacement | null;
}): AdMarkerPreview {
  const placement = options.placement ?? 'text_start';
  const marker = formatAdMarker(options.advertiserName || '…', options.inn || '…', options.erid || '…');
  const warnings: string[] = [];
  let text = options.bodyText ?? '';

  if (text.includes('erid:')) {
    return { marker, text, warnings };
  }

  if (placement === 'vk_menu' || placement === 'vk_menu_and_text_end') {
    warnings.push(VK_MENU_UNSUPPORTED_WARNING);
    return { marker, text: append(text, marker), warnings };
  }

  if (placement === 'click_link_start' || placement === 'click_link_end') {
    const injected = injectEridIntoUrls(text, options.erid || 'preview');
    text = injected.text;
    if (!injected.changed) {
      warnings.push('click_link_fallback: no external URLs found; using text marker');
      text =
        placement === 'click_link_start' ? prepend(text, marker) : append(text, marker);
    }
    return { marker, text, warnings };
  }

  if (placement === 'text_end') {
    return { marker, text: append(text, marker), warnings };
  }

  return { marker, text: prepend(text, marker), warnings };
}
