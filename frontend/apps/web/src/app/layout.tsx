import { NextIntlClientProvider } from 'next-intl';
import { getLocale, getMessages, getTranslations } from 'next-intl/server';
import localFont from 'next/font/local';
import type { Metadata } from 'next';
import type { ReactNode } from 'react';

import { AppProviders } from '@/shared/providers/app-providers';

import './globals.css';

const sans = localFont({
  src: [
    { path: './fonts/ibm-plex-sans-latin-400-normal.woff2', weight: '400', style: 'normal' },
    { path: './fonts/ibm-plex-sans-latin-500-normal.woff2', weight: '500', style: 'normal' },
    { path: './fonts/ibm-plex-sans-latin-600-normal.woff2', weight: '600', style: 'normal' },
    { path: './fonts/ibm-plex-sans-cyrillic-400-normal.woff2', weight: '400', style: 'normal' },
    { path: './fonts/ibm-plex-sans-cyrillic-500-normal.woff2', weight: '500', style: 'normal' },
    { path: './fonts/ibm-plex-sans-cyrillic-600-normal.woff2', weight: '600', style: 'normal' },
  ],
  variable: '--font-sans',
  display: 'swap',
});

const display = localFont({
  src: [
    { path: './fonts/source-serif-4-latin-600-normal.woff2', weight: '600', style: 'normal' },
    { path: './fonts/source-serif-4-latin-700-normal.woff2', weight: '700', style: 'normal' },
    { path: './fonts/source-serif-4-cyrillic-600-normal.woff2', weight: '600', style: 'normal' },
    { path: './fonts/source-serif-4-cyrillic-700-normal.woff2', weight: '700', style: 'normal' },
  ],
  variable: '--font-display',
  display: 'swap',
});

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations('meta');
  return {
    title: {
      default: t('title'),
      template: '%s · Posts',
    },
    description: t('description'),
    metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000'),
    openGraph: {
      title: t('title'),
      description: t('description'),
      locale: 'ru_RU',
      type: 'website',
      siteName: 'Posts',
    },
    icons: {
      icon: [{ url: '/icon.svg', type: 'image/svg+xml' }],
      apple: [{ url: '/apple-icon' }],
    },
    manifest: '/manifest.webmanifest',
  };
}

export default async function RootLayout({ children }: { children: ReactNode }) {
  const locale = await getLocale();
  const messages = await getMessages();

  return (
    <html lang={locale} suppressHydrationWarning className={`${sans.variable} ${display.variable}`}>
      <body>
        <a
          href="#main-content"
          className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-[100] focus:rounded-md focus:bg-background focus:px-3 focus:py-2 focus:shadow-md"
        >
          Перейти к содержимому
        </a>
        <NextIntlClientProvider messages={messages}>
          <AppProviders>{children}</AppProviders>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
