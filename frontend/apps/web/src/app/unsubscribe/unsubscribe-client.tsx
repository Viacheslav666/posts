'use client';

import { useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';

import { getApiClient } from '@/shared/api-client';

export function UnsubscribeClient() {
  const searchParams = useSearchParams();
  const token = searchParams.get('token') ?? '';
  const [message, setMessage] = useState('Отписываем…');
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!token) {
      setError(true);
      setMessage('Некорректная ссылка отписки.');
      return;
    }
    void (async () => {
      try {
        const client = getApiClient();
        const { data, error: apiError, response } = await client.GET('/api/v1/notifications/unsubscribe', {
          params: { query: { token } },
        });
        if (apiError || !response.ok) {
          setError(true);
          setMessage('Не удалось отписаться. Ссылка недействительна или устарела.');
          return;
        }
        setMessage(data?.message === 'unsubscribed' ? 'Вы отписаны от email-уведомлений.' : 'Готово.');
      } catch {
        setError(true);
        setMessage('Не удалось отписаться. Попробуйте позже.');
      }
    })();
  }, [token]);

  return (
    <main className="mx-auto flex min-h-[50vh] max-w-lg flex-col items-center justify-center gap-2 px-4 text-center">
      <h1 className="text-xl font-semibold">Email-уведомления</h1>
      <p className={`text-sm ${error ? 'text-destructive' : 'text-muted-foreground'}`} role="status">
        {message}
      </p>
    </main>
  );
}