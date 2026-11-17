import type { HTMLAttributes } from 'react';

import { cn } from '../lib/utils';

export type SocialNetwork = 'vk' | 'telegram' | 'ok' | 'max' | 'rutube';

const networkMeta: Record<
  SocialNetwork,
  {
    label: string;
    bg: string;
    glyph: string;
  }
> = {
  // Brand hues darkened enough for white text ≥ 4.5:1 contrast at small sizes.
  vk: { label: 'VK', bg: 'bg-[#0057C2]', glyph: 'VK' },
  telegram: { label: 'Telegram', bg: 'bg-[#0A6FA0]', glyph: 'TG' },
  ok: { label: 'OK', bg: 'bg-[#A35A00]', glyph: 'OK' },
  max: { label: 'Max', bg: 'bg-[#5B45D6]', glyph: 'MX' },
  rutube: { label: 'Rutube', bg: 'bg-[#C40000]', glyph: 'RT' },
};

export type NetworkIconProps = HTMLAttributes<HTMLSpanElement> & {
  network: SocialNetwork;
  size?: 'sm' | 'md' | 'lg';
};

const sizeClasses = {
  sm: 'h-6 w-6 text-[11px]',
  md: 'h-8 w-8 text-xs',
  lg: 'h-10 w-10 text-sm',
};

export function NetworkIcon({ network, size = 'md', className, ...props }: NetworkIconProps) {
  const meta = networkMeta[network];

  return (
    <span
      role="img"
      aria-label={meta.label}
      title={meta.label}
      className={cn(
        'inline-flex items-center justify-center rounded-md font-bold text-white shadow-sm',
        meta.bg,
        sizeClasses[size],
        className,
      )}
      {...props}
    >
      <span aria-hidden="true">{meta.glyph}</span>
    </span>
  );
}
