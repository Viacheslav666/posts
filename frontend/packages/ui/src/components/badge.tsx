import { cva, type VariantProps } from 'class-variance-authority';
import type { HTMLAttributes } from 'react';

import { cn } from '../lib/utils';

const badgeVariants = cva(
  'inline-flex items-center rounded-md border px-2.5 py-0.5 text-xs font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-primary text-primary-foreground',
        secondary: 'border-transparent bg-secondary text-secondary-foreground',
        outline: 'border-border text-foreground',
        draft: 'border-transparent bg-[hsl(var(--status-draft)/0.15)] text-[hsl(var(--status-draft))]',
        scheduled: 'border-transparent bg-[hsl(var(--status-scheduled)/0.15)] text-[hsl(var(--status-scheduled))]',
        publishing: 'border-transparent bg-[hsl(var(--status-publishing)/0.15)] text-[hsl(var(--status-publishing))]',
        published: 'border-transparent bg-[hsl(var(--status-published)/0.15)] text-[hsl(var(--status-published))]',
        failed: 'border-transparent bg-[hsl(var(--status-failed)/0.15)] text-[hsl(var(--status-failed))]',
        paused: 'border-transparent bg-[hsl(var(--status-paused)/0.15)] text-[hsl(var(--status-paused))]',
        review: 'border-transparent bg-[hsl(var(--status-review)/0.15)] text-[hsl(var(--status-review))]',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  },
);

export type PublicationStatus = 'draft' | 'scheduled' | 'publishing' | 'published' | 'failed' | 'paused' | 'review';

export type BadgeProps = HTMLAttributes<HTMLDivElement> & VariantProps<typeof badgeVariants>;

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />;
}

export { badgeVariants };
