'use client';

import { Toaster, TooltipProvider } from '@posts/ui';
import type { ReactNode } from 'react';

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
            {children}
            <Toaster />
          </TooltipProvider>
        </QueryProvider>
      </MswProvider>
    </ThemeProvider>
  );
}
