import { getTranslations } from 'next-intl/server';

export default async function HealthPage() {
  const t = await getTranslations('health');

  return (
    <main id="main-content" className="mx-auto flex min-h-screen max-w-lg flex-col justify-center px-6">
      <h1 className="font-display text-3xl font-bold text-foreground">{t('title')}</h1>
      <p className="mt-3 text-muted-foreground">{t('status')}</p>
    </main>
  );
}
