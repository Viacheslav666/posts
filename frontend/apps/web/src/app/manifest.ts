import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Posts',
    short_name: 'Posts',
    description: 'SMM-платформа отложенного постинга для команд',
    start_url: '/',
    display: 'standalone',
    background_color: '#0B1F17',
    theme_color: '#0B1F17',
    lang: 'ru',
    icons: [
      {
        src: '/icon.svg',
        sizes: 'any',
        type: 'image/svg+xml',
        purpose: 'any',
      },
    ],
  };
}
