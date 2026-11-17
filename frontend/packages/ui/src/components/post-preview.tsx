'use client';

import type { HTMLAttributes } from 'react';

import { cn } from '../lib/utils';
import { NetworkIcon, type SocialNetwork } from './network-icon';

export type PostPreviewNetwork = 'vk' | 'telegram' | 'ok' | 'max';

export type PostPreviewProps = HTMLAttributes<HTMLElement> & {
  network: PostPreviewNetwork;
  /** Sanitized HTML for TG/Max; plain text for VK/OK. */
  contentHtml?: string;
  contentText?: string;
  channelName?: string;
  mode?: 'mobile' | 'desktop';
};

const shellStyles: Record<PostPreviewNetwork, string> = {
  vk: 'border-[#C9D5E5] bg-[#EDEEF0] dark:border-[#2A3340] dark:bg-[#1A1F26]',
  telegram: 'border-[#9EC9E0] bg-[#C8DCE8] dark:border-[#1E3A4A] dark:bg-[#0F1C24]',
  ok: 'border-[#E0C89A] bg-[#F3E9D7] dark:border-[#3D3220] dark:bg-[#1F1A12]',
  max: 'border-[#C5B8F0] bg-[#E8E3F7] dark:border-[#2E2750] dark:bg-[#161225]',
};

const bubbleStyles: Record<PostPreviewNetwork, string> = {
  vk: 'rounded-xl bg-white text-[#222] dark:bg-[#22282F] dark:text-[#E8EAED]',
  telegram: 'rounded-2xl rounded-bl-md bg-white text-[#1B1F23] shadow-sm dark:bg-[#182530] dark:text-[#E8F0F5]',
  ok: 'rounded-lg bg-white text-[#2B2114] dark:bg-[#2A2318] dark:text-[#F5EFE3]',
  max: 'rounded-2xl bg-white text-[#1C1830] dark:bg-[#221C3A] dark:text-[#EEEAF8]',
};

const toIconNetwork: Record<PostPreviewNetwork, SocialNetwork> = {
  vk: 'vk',
  telegram: 'telegram',
  ok: 'ok',
  max: 'max',
};

export function PostPreview({
  network,
  contentHtml,
  contentText,
  channelName = 'Канал',
  mode = 'mobile',
  className,
  ...props
}: PostPreviewProps) {
  const isHtml = network === 'telegram' || network === 'max';

  return (
    <section
      aria-label={`Предпросмотр ${network}`}
      className={cn(
        'flex flex-col overflow-hidden border',
        shellStyles[network],
        mode === 'mobile' ? 'max-w-[360px]' : 'max-w-xl',
        className,
      )}
      {...props}
    >
      <header className="flex items-center gap-2 border-b border-black/5 px-3 py-2 dark:border-white/10">
        <NetworkIcon network={toIconNetwork[network]} size="sm" />
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold text-foreground">{channelName}</p>
          <p className="text-xs text-muted-foreground">сейчас</p>
        </div>
      </header>
      <div className={cn('flex-1 p-3', mode === 'desktop' && 'p-4')}>
        <article className={cn('px-3 py-2 text-sm leading-relaxed', bubbleStyles[network])}>
          {isHtml && contentHtml ? (
            <div
              className="prose-preview break-words [&_a]:underline [&_blockquote]:border-l-2 [&_blockquote]:border-current/30 [&_blockquote]:pl-2 [&_code]:rounded [&_code]:bg-black/5 [&_code]:px-1 [&_pre]:overflow-x-auto [&_pre]:rounded [&_pre]:bg-black/5 [&_pre]:p-2 [&_tg-spoiler]:rounded [&_tg-spoiler]:bg-current/20"
              // Content must be sanitized by the caller (DOMPurify).
              dangerouslySetInnerHTML={{ __html: contentHtml }}
            />
          ) : (
            <p className="whitespace-pre-wrap break-words">{contentText || ' '}</p>
          )}
        </article>
      </div>
    </section>
  );
}
