'use client';

import { Button, toast } from '@posts/ui';
import { useTranslations } from 'next-intl';

import { getErrorMessage } from '@/shared/lib/errors';

import { startOauth } from '../api/auth-api';

export function OauthButtons() {
  const t = useTranslations('auth.oauth');

  async function handleOauth(provider: 'vk' | 'yandex') {
    try {
      const data = await startOauth(provider);
      toast({
        title: t('stubTitle', { provider: provider === 'vk' ? 'VK ID' : 'Яндекс ID' }),
        description: t('stubDescription'),
      });
      if (data.authorizationUrl) {
        window.open(data.authorizationUrl, '_blank', 'noopener,noreferrer');
      }
    } catch (error) {
      toast({
        variant: 'destructive',
        title: t('error'),
        description: getErrorMessage(error, t('error')),
      });
    }
  }

  return (
    <div className="grid gap-2 sm:grid-cols-2">
      <Button type="button" variant="outline" className="w-full" onClick={() => void handleOauth('vk')}>
        <VkGlyph />
        {t('vk')}
      </Button>
      <Button type="button" variant="outline" className="w-full" onClick={() => void handleOauth('yandex')}>
        <YandexGlyph />
        {t('yandex')}
      </Button>
    </div>
  );
}

function VkGlyph() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden className="size-4 fill-current">
      <path d="M12.8 17.5c-5.2 0-8.3-3.6-8.4-9.6h2.6c.1 4.4 2 6.2 3.5 6.6V7.9h2.5v3.8c1.5-.16 3-.9 3.6-3.8h2.4c-.5 2.8-2.3 4.5-3.6 5.3 1.3.7 3.3 2.2 4 5.3h-2.7c-.5-1.9-1.9-3.3-3.7-3.5v3.5h-.2Z" />
    </svg>
  );
}

function YandexGlyph() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden className="size-4 fill-current">
      <path d="M12.4 4.5c1.9 0 3.2.5 4 1.5.7.9 1 2 1 3.4 0 1.7-.5 3-1.4 3.9-.9.9-2.2 1.4-3.8 1.4h-1.3V19h-2.6V4.5h3.9Zm-.2 7.7h.7c.9 0 1.6-.2 2.1-.7.5-.5.8-1.2.8-2.2 0-1-.2-1.7-.7-2.2-.5-.5-1.2-.7-2.1-.7h-.8v5.8Z" />
    </svg>
  );
}
