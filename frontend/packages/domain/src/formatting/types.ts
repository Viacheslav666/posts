export type PreviewNetwork = 'telegram' | 'max' | 'vk' | 'ok';

/** DB / API network ids (`tg` maps from fixture key `telegram`). */
export type NetworkId = 'vk' | 'tg' | 'ok' | 'max' | 'rutube';

export type HtmlNetworkRender = {
  html: string;
  plain_text: string;
  plain_length: number;
  valid: boolean;
  warnings: string[];
};

export type PlainNetworkRender = {
  plain: string;
  formatting_stripped: boolean;
  warning?: string;
};

export type FormattingRenderResult = {
  telegram: HtmlNetworkRender;
  max: HtmlNetworkRender;
  vk: PlainNetworkRender;
  ok: PlainNetworkRender;
  limits: {
    telegram_max_chars: number;
    max_max_chars: number;
    telegram_caption_max_chars: number;
    exceeds_telegram: boolean;
    exceeds_max: boolean;
    exceeds_telegram_caption: boolean;
  };
};

export type TiptapMark = {
  type: string;
  attrs?: Record<string, unknown>;
};

export type TiptapNode = {
  type: string;
  attrs?: Record<string, unknown>;
  marks?: TiptapMark[];
  text?: string;
  content?: TiptapNode[];
};

export const PLAIN_FORMATTING_WARNING =
  'Форматирование будет потеряно для VK/OK (plain text).';

export const TELEGRAM_MAX_CHARS = 4096;
export const MAX_MAX_CHARS = 4000;
export const TELEGRAM_CAPTION_MAX_CHARS = 1024;
