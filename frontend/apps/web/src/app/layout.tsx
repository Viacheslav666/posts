import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Posts',
  description: 'SMM-платформа отложенного постинга',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ru">
      <body>{children}</body>
    </html>
  );
}
