'use client';

import { Button } from '@posts/ui';
import { Loader2 } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useEffect, useRef, useState } from 'react';

import { getErrorMessage, isApiProblemError } from '@/shared/lib/errors';

import { completeOauth, fetchMe, type OauthLoginProvider } from '../api/auth-api';
import { appHomePath } from '../lib/app-home-path';

const OAUTH_LINK_REQUIRED_CODE = 'oauth_link_required';

type CallbackError = { title: string; description: string };

type OauthCallbackProps = {
  provider: string;
};

function isLoginProvider(value: string): value is OauthLoginProvider {
  return value === 'vk' || value === 'yandex';
}

export function OauthCallback({ provider }: OauthCallbackProps) {
  const t = useTranslations('auth.oauthCallback');
  const router = useRouter();
  const searchParams = useSearchParams();
  const started = useRef(false);
  const [error, setError] = useState<CallbackError | null>(null);

  const code = searchParams.get('code') ?? '';
  const state = searchParams.get('state') ?? '';
  const deviceId = searchParams.get('device_id') ?? undefined;
  const providerError = searchParams.get('error');
  const providerErrorDescription = searchParams.get('error_description');

  useEffect(() => {
    // The authorization code is single-use; never exchange it twice (React StrictMode re-runs effects).
    if (started.current) {
      return;
    }
    started.current = true;

    if (!isLoginProvider(provider)) {
      setError({ title: t('errorTitle'), description: t('unsupportedProvider') });
      return;
    }
    if (providerError) {
      setError({
        title: t('errorTitle'),
        description:
          providerError === 'access_denied'
            ? t('accessDenied')
            : t('providerError', { reason: providerErrorDescription || providerError }),
      });
      return;
    }
    if (!code || !state) {
      setError({ title: t('errorTitle'), description: t('missingParams') });
      return;
    }

    void (async () => {
      try {
        await completeOauth(provider, { code, state, deviceId });
        const me = await fetchMe();
        router.replace(appHomePath(me));
      } catch (err) {
        if (isApiProblemError(err) && err.code === OAUTH_LINK_REQUIRED_CODE) {
          setError({ title: t('linkRequiredTitle'), description: t('linkRequiredBody') });
        } else if (isApiProblemError(err) && err.status === 401) {
          setError({ title: t('errorTitle'), description: t('expired') });
        } else {
          setError({ title: t('errorTitle'), description: getErrorMessage(err, t('generic')) });
        }
      }
    })();
  }, [code, deviceId, provider, providerError, providerErrorDescription, router, state, t]);

  if (error) {
    return (
      <div className="space-y-4" role="alert" data-testid="oauth-login-callback-error">
        <div>
          <p className="font-medium text-foreground">{error.title}</p>
          <p className="mt-1 text-sm text-muted-foreground">{error.description}</p>
        </div>
        <Button asChild className="w-full">
          <Link href="/login">{t('backToLogin')}</Link>
        </Button>
      </div>
    );
  }

  return (
    <div
      className="flex items-center gap-2 text-sm text-muted-foreground"
      role="status"
      data-testid="oauth-login-callback-loading"
    >
      <Loader2 className="size-4 animate-spin" aria-hidden />
      {t('loading')}
    </div>
  );
}
