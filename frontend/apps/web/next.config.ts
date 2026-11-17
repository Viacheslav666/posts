import type { NextConfig } from 'next';
import path from 'node:path';
import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin('./src/i18n/request.ts');

const nextConfig: NextConfig = {
  output: 'standalone',
  // Monorepo: include workspace packages in the standalone trace.
  outputFileTracingRoot: path.join(__dirname, '../..'),
  transpilePackages: ['@posts/ui', '@posts/domain', '@posts/api-client'],
};

export default withNextIntl(nextConfig);
