import { Button } from '@posts/ui';

export default function HomePage() {
  return (
    <main className="mx-auto flex min-h-screen w-full max-w-5xl flex-col justify-center px-6 py-16">
      <p className="font-display text-5xl font-bold tracking-tight text-ink-950 md:text-7xl">Posts</p>
      <h1 className="mt-6 max-w-2xl text-2xl font-medium text-ink-900 md:text-3xl">
        Надёжный планировщик публикаций для команд
      </h1>
      <p className="mt-4 max-w-xl text-base text-ink-700 md:text-lg">
        Каркас монорепозитория DEV-1: API, веб и локальная инфраструктура готовы к работе агентов.
      </p>
      <div className="mt-8 flex gap-3">
        <Button>Начать</Button>
        <a className="inline-flex items-center text-sm font-medium text-pine-600 underline-offset-4 hover:underline" href="/health">
          Health
        </a>
      </div>
    </main>
  );
}
