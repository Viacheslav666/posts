import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  output: 'standalone',
  transpilePackages: ['@posts/ui', '@posts/domain', '@posts/api-client'],
};

export default nextConfig;
