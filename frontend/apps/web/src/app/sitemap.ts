import type { MetadataRoute } from 'next';

export default function sitemap(): MetadataRoute.Sitemap {
  const base = process.env.NEXT_PUBLIC_SITE_URL?.replace(/\/$/, '') || 'http://localhost:3000';
  const routes = [
    '',
    '/pricing',
    '/login',
    '/register',
    '/legal/offer',
    '/legal/privacy',
    '/legal/personal-data',
    '/legal/contacts',
  ];
  const lastModified = new Date();
  return routes.map((path) => ({
    url: `${base}${path || '/'}`,
    lastModified,
    changeFrequency: path === '' ? 'weekly' : 'monthly',
    priority: path === '' ? 1 : 0.6,
  }));
}
