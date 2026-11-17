export type Network = 'vk' | 'tg' | 'ok' | 'max' | 'rutube';

export function isSupportedNetwork(value: string): value is Network {
  return ['vk', 'tg', 'ok', 'max', 'rutube'].includes(value);
}

export {
  MAX_MAX_CHARS,
  PLAIN_FORMATTING_WARNING,
  TELEGRAM_CAPTION_MAX_CHARS,
  TELEGRAM_MAX_CHARS,
} from './formatting/types';
export type {
  FormattingRenderResult,
  HtmlNetworkRender,
  NetworkId,
  PlainNetworkRender,
  PreviewNetwork,
  TiptapMark,
  TiptapNode,
} from './formatting/types';

export { hasMarkupTags, stripTags, unescapeHtml, unicodeLength } from './formatting/html-utils';
export { renderForPreviewNetwork, renderFromCanonicalHtml } from './formatting/render';
export {
  plainTextToTiptapDoc,
  renderFromTiptap,
  tiptapJsonToCanonicalHtml,
} from './formatting/tiptap';
export {
  hasBlockingValidationErrors,
  validateCanonicalHtml,
} from './formatting/validate';
export type { NetworkTextCapability, ValidationIssue } from './formatting/validate';
