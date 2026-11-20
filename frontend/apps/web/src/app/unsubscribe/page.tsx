import { Suspense } from 'react';

import { UnsubscribeClient } from './unsubscribe-client';

export default function UnsubscribePage() {
  return (
    <Suspense
      fallback={
        <main className="mx-auto flex min-h-[50vh] max-w-lg items-center justify-center px-4 text-center text-sm text-muted-foreground">
          Отписываем…
        </main>
      }
    >
      <UnsubscribeClient />
    </Suspense>
  );
}