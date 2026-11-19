import type { AntispamRule } from '@posts/api-client';

import type { AntispamRuleFormValues } from './schemas';
import {
  ATTACHMENT_KEYS,
  sectionsForNetwork,
  type AttachmentKey,
  type AntispamSection,
} from './types';

export type AntispamKeywordsConfig = {
  black: string[];
  white: string[];
  black_list_ids: string[];
  white_list_ids: string[];
};

export type AntispamLinksConfig = {
  all: boolean;
  vk: boolean;
  domain_whitelist: string[];
};

export type AntispamAttachmentsConfig = Record<AttachmentKey, boolean>;

export type AntispamCapsConfig = {
  enabled: boolean;
  ratio: number;
  min_letters: number;
};

export type AntispamRepeatConfig = {
  enabled: boolean;
  window_hours: number;
};

export type AntispamAuthorsConfig = {
  communities: boolean;
  non_members: boolean;
  sender_chat: boolean;
};

export type AntispamContextConfig = {
  under_ads: boolean;
  under_pinned: boolean;
};

export type AntispamActionsConfig = {
  delete: boolean;
  ban: {
    enabled: boolean;
    days: number;
    reason: string;
    comment_visible: boolean;
  };
  reply: {
    enabled: boolean;
    template: string;
  };
};

export type AntispamExceptionsConfig = {
  admin_ids: string[];
  user_whitelist: string[];
  exclude_own_community: boolean;
};

/** Typed config matching RuleConfig.fromMap defaults. */
export type AntispamConfig = {
  filters: {
    keywords: AntispamKeywordsConfig;
    links: AntispamLinksConfig;
    attachments: AntispamAttachmentsConfig;
    caps: AntispamCapsConfig;
    repeat: AntispamRepeatConfig;
    authors: AntispamAuthorsConfig;
    context: AntispamContextConfig;
  };
  actions: AntispamActionsConfig;
  exceptions: AntispamExceptionsConfig;
};

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function asBool(value: unknown, fallback: boolean): boolean {
  if (typeof value === 'boolean') return value;
  if (value === 'true') return true;
  if (value === 'false') return false;
  return fallback;
}

function asNumber(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}

function asStringList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.map((item) => String(item)).filter(Boolean);
}

function asString(value: unknown, fallback: string): string {
  return typeof value === 'string' ? value : fallback;
}

export function defaultAttachments(): AntispamAttachmentsConfig {
  return {
    photo: false,
    photo_without_text: false,
    video: false,
    audio: false,
    doc: false,
    gif: false,
    sticker: false,
  };
}

export function defaultConfig(): AntispamConfig {
  return {
    filters: {
      keywords: { black: [], white: [], black_list_ids: [], white_list_ids: [] },
      links: { all: false, vk: false, domain_whitelist: [] },
      attachments: defaultAttachments(),
      caps: { enabled: false, ratio: 0.7, min_letters: 5 },
      repeat: { enabled: false, window_hours: 12 },
      authors: { communities: false, non_members: false, sender_chat: false },
      context: { under_ads: false, under_pinned: false },
    },
    actions: {
      delete: true,
      ban: { enabled: false, days: 7, reason: '', comment_visible: false },
      reply: {
        enabled: false,
        template: 'Ваше сообщение удалено: {reasons}',
      },
    },
    exceptions: {
      admin_ids: [],
      user_whitelist: [],
      exclude_own_community: true,
    },
  };
}

export function withConfigDefaults(raw: unknown): AntispamConfig {
  const config = asRecord(raw);
  const filtersRaw = asRecord(config.filters);
  const actionsRaw = asRecord(config.actions);
  const exceptionsRaw = asRecord(config.exceptions);
  const keywordsRaw = asRecord(filtersRaw.keywords);
  const linksRaw = asRecord(filtersRaw.links);
  const attachmentsRaw = asRecord(filtersRaw.attachments);
  const capsRaw = asRecord(filtersRaw.caps);
  const repeatRaw = asRecord(filtersRaw.repeat);
  const authorsRaw = asRecord(filtersRaw.authors);
  const contextRaw = asRecord(filtersRaw.context);
  const banRaw = asRecord(actionsRaw.ban);
  const replyRaw = asRecord(actionsRaw.reply);
  const defaults = defaultConfig();

  const attachments = defaultAttachments();
  for (const key of ATTACHMENT_KEYS) {
    attachments[key] = asBool(attachmentsRaw[key], attachments[key]);
  }

  return {
    filters: {
      keywords: {
        black: asStringList(keywordsRaw.black),
        white: asStringList(keywordsRaw.white),
        black_list_ids: asStringList(keywordsRaw.black_list_ids),
        white_list_ids: asStringList(keywordsRaw.white_list_ids),
      },
      links: {
        all: asBool(linksRaw.all, false),
        vk: asBool(linksRaw.vk, false),
        domain_whitelist: asStringList(linksRaw.domain_whitelist),
      },
      attachments,
      caps: {
        enabled: asBool(capsRaw.enabled, false),
        ratio: asNumber(capsRaw.ratio, 0.7),
        min_letters: asNumber(capsRaw.min_letters, 5),
      },
      repeat: {
        enabled: asBool(repeatRaw.enabled, false),
        window_hours: asNumber(repeatRaw.window_hours, 12),
      },
      authors: {
        communities: asBool(authorsRaw.communities, false),
        non_members: asBool(authorsRaw.non_members, false),
        sender_chat: asBool(authorsRaw.sender_chat, false),
      },
      context: {
        under_ads: asBool(contextRaw.under_ads, false),
        under_pinned: asBool(contextRaw.under_pinned, false),
      },
    },
    actions: {
      delete: asBool(actionsRaw.delete, true),
      ban: {
        enabled: asBool(banRaw.enabled, false),
        days: asNumber(banRaw.days, 7),
        reason: asString(banRaw.reason, ''),
        comment_visible: asBool(banRaw.comment_visible, false),
      },
      reply: {
        enabled: asBool(replyRaw.enabled, false),
        template: asString(replyRaw.template, defaults.actions.reply.template),
      },
    },
    exceptions: {
      admin_ids: asStringList(exceptionsRaw.admin_ids),
      user_whitelist: asStringList(exceptionsRaw.user_whitelist),
      exclude_own_community: asBool(exceptionsRaw.exclude_own_community, true),
    },
  };
}

export function defaultFormValues(channelId?: string, network?: string): AntispamRuleFormValues {
  const config = defaultConfig();
  const sections = sectionsForNetwork(network);
  return {
    name: '',
    channelId: channelId ?? '',
    sections: sections.length > 0 ? [sections[0]!] : [],
    priority: 100,
    dryRun: false,
    activate: true,
    blackWords: [],
    whiteWords: [],
    blackListIds: [],
    whiteListIds: [],
    keywordMatchMode: 'substring',
    linksAll: false,
    linksVk: false,
    domainWhitelist: [],
    attachments: { ...config.filters.attachments },
    capsEnabled: false,
    capsRatio: 0.7,
    capsMinLetters: 5,
    repeatEnabled: false,
    repeatWindowHours: 12,
    authorsCommunities: false,
    authorsNonMembers: false,
    authorsSenderChat: false,
    contextUnderAds: false,
    contextUnderPinned: false,
    actionDelete: true,
    banEnabled: false,
    banDays: 7,
    banReason: '',
    banCommentVisible: false,
    replyEnabled: false,
    replyTemplate: config.actions.reply.template,
    adminIds: [],
    userWhitelist: [],
    excludeOwnCommunity: true,
  };
}

export function buildConfig(values: AntispamRuleFormValues): AntispamConfig {
  return {
    filters: {
      keywords: {
        black: values.blackWords,
        white: values.whiteWords,
        black_list_ids: values.blackListIds,
        white_list_ids: values.whiteListIds,
      },
      links: {
        all: values.linksAll,
        vk: values.linksVk,
        domain_whitelist: values.domainWhitelist,
      },
      attachments: { ...values.attachments },
      caps: {
        enabled: values.capsEnabled,
        ratio: values.capsRatio,
        min_letters: values.capsMinLetters,
      },
      repeat: {
        enabled: values.repeatEnabled,
        window_hours: values.repeatWindowHours,
      },
      authors: {
        communities: values.authorsCommunities,
        non_members: values.authorsNonMembers,
        sender_chat: values.authorsSenderChat,
      },
      context: {
        under_ads: values.contextUnderAds,
        under_pinned: values.contextUnderPinned,
      },
    },
    actions: {
      delete: values.actionDelete,
      ban: {
        enabled: values.banEnabled,
        days: values.banDays,
        reason: values.banReason,
        comment_visible: values.banCommentVisible,
      },
      reply: {
        enabled: values.replyEnabled,
        template: values.replyTemplate,
      },
    },
    exceptions: {
      admin_ids: values.adminIds,
      user_whitelist: values.userWhitelist,
      exclude_own_community: values.excludeOwnCommunity,
    },
  };
}

export function formValuesFromRule(rule: AntispamRule): AntispamRuleFormValues {
  const config = withConfigDefaults(rule.config);
  const sections = (rule.sections ?? []).filter((s): s is AntispamSection =>
    Boolean(s),
  ) as AntispamSection[];
  return {
    name: rule.name ?? '',
    channelId: rule.channelId ?? '',
    sections,
    priority: rule.priority ?? 100,
    dryRun: Boolean(rule.dryRun),
    activate: rule.status === 'active',
    blackWords: config.filters.keywords.black,
    whiteWords: config.filters.keywords.white,
    blackListIds: config.filters.keywords.black_list_ids,
    whiteListIds: config.filters.keywords.white_list_ids,
    keywordMatchMode: 'substring',
    linksAll: config.filters.links.all,
    linksVk: config.filters.links.vk,
    domainWhitelist: config.filters.links.domain_whitelist,
    attachments: { ...config.filters.attachments },
    capsEnabled: config.filters.caps.enabled,
    capsRatio: config.filters.caps.ratio,
    capsMinLetters: config.filters.caps.min_letters,
    repeatEnabled: config.filters.repeat.enabled,
    repeatWindowHours: config.filters.repeat.window_hours,
    authorsCommunities: config.filters.authors.communities,
    authorsNonMembers: config.filters.authors.non_members,
    authorsSenderChat: config.filters.authors.sender_chat,
    contextUnderAds: config.filters.context.under_ads,
    contextUnderPinned: config.filters.context.under_pinned,
    actionDelete: config.actions.delete,
    banEnabled: config.actions.ban.enabled,
    banDays: config.actions.ban.days,
    banReason: config.actions.ban.reason,
    banCommentVisible: config.actions.ban.comment_visible,
    replyEnabled: config.actions.reply.enabled,
    replyTemplate: config.actions.reply.template,
    adminIds: config.exceptions.admin_ids,
    userWhitelist: config.exceptions.user_whitelist,
    excludeOwnCommunity: config.exceptions.exclude_own_community,
  };
}

/** Config as OpenAPI `additionalProperties` blob. */
export function configAsApiBlob(config: AntispamConfig): Record<string, never> {
  return config as unknown as Record<string, never>;
}

export function previewReplyTemplate(template: string, reasons: string[]): string {
  const joined = reasons.length > 0 ? reasons.join(', ') : 'спам';
  return template.replaceAll('{reasons}', joined);
}

export function collectWordListUsage(
  rules: AntispamRule[],
  listId: string,
): { black: number; white: number } {
  let black = 0;
  let white = 0;
  for (const rule of rules) {
    const config = withConfigDefaults(rule.config);
    if (config.filters.keywords.black_list_ids.includes(listId)) black += 1;
    if (config.filters.keywords.white_list_ids.includes(listId)) white += 1;
  }
  return { black, white };
}
