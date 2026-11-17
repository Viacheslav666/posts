import type { NextConfig } from 'next';
import path from 'node:path';
import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts');

const nextConfig: NextConfig = {
  output: 'standalone',
  // Monorepo: include workspace packages in the standalone trace.
  outputFileTracingRoot: path.join(__dirname, '../..'),
  transpilePackages: ['@posts/ui', '@posts/domain', '@posts/api-client'],
  webpack: (config, { isServer }) => {
    // MSW package exports mark browser/node entrypoints as null for the opposite runtime.
    config.resolve.alias = {
      ...config.resolve.alias,
      ...(isServer ? { 'msw/browser': false } : { 'msw/node': false }),
    };
    return config;
  },
};

export default withNextIntl(nextConfig);
