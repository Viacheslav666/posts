'use client';

import { useEffect, useState, type ReactNode } from 'react';

type MswProviderProps = {
  children: ReactNode;
};

const shouldEnableMocks =
  process.env.NEXT_PUBLIC_API_MOCKING === 'true' || process.env.NODE_ENV === 'development';

export function MswProvider({ children }: MswProviderProps) {
  const [ready, setReady] = useState(!shouldEnableMocks);

  useEffect(() => {
    if (!shouldEnableMocks) {
      return;
    }

    let cancelled = false;

    async function startWorker() {
      const { worker } = await import('@/mocks/browser');
      await worker.start({
        onUnhandledRequest: 'bypass',
        serviceWorker: {
          url: '/mockServiceWorker.js',
        },
      });
      if (!cancelled) {
        setReady(true);
      }
    }

    void startWorker();

    return () => {
      cancelled = true;
    };
  }, []);

  if (!ready) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground" role="status">
        Загрузка…
      </div>
    );
  }

  return children;
}
