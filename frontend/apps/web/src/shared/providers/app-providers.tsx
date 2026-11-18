'use client';

import { Toaster, TooltipProvider } from '@posts/ui';
import type { ReactNode } from 'react';

import { ImpersonationBannerHost } from '@/features/admin/components/impersonation-banner-host';

import { MswProvider } from './msw-provider';
import { QueryProvider } from './query-provider';
import { ThemeProvider } from './theme-provider';

type AppProvidersProps = {
  children: ReactNode;
};

export function AppProviders({ children }: AppProvidersProps) {
  return (
    <ThemeProvider>
      <MswProvider>
        <QueryProvider>
          <TooltipProvider delayDuration={200}>
            <ImpersonationBannerHost />
            {children}
            <Toaster />
          </TooltipProvider>
        </QueryProvider>
      </MswProvider>
    </ThemeProvider>
  );
}
