'use client';

import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';
import { usePathname } from 'next/navigation';

import { createAppApiClient, applyTokenResponse, clearAccessToken } from '@/shared/api-client';
import { getAccessToken } from '@/shared/api-client/session';
import { redirectToLogin, isPublicAuthRoute } from '@/shared/lib/auth-redirect';

type SessionBootstrapProps = {
  children: ReactNode;
};

/**
 * Restores the access JWT from the httpOnly refresh cookie before workspace queries run.
 */
export function SessionBootstrap({ children }: SessionBootstrapProps) {
  const pathname = usePathname();
  const [ready, setReady] = useState(() => {
    if (typeof window === 'undefined') {
      return true;
    }
    return isPublicAuthRoute(pathname) || Boolean(getAccessToken());
  });

  useEffect(() => {
    if (isPublicAuthRoute(pathname)) {
      setReady(true);
      return;
    }
    if (getAccessToken()) {
      setReady(true);
      return;
    }

    let cancelled = false;
    void (async () => {
      const client = createAppApiClient();
      const { data, error, response } = await client.POST('/api/v1/auth/refresh', { body: {} });
      if (cancelled) {
        return;
      }
      if (!error && response.ok && data?.accessToken) {
        applyTokenResponse(data);
        setReady(true);
        return;
      }
      clearAccessToken();
      redirectToLogin('session_expired');
      setReady(true);
    })();

    return () => {
      cancelled = true;
    };
  }, [pathname]);

  if (!ready) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center text-sm text-muted-foreground" role="status">
        Вход…
      </div>
    );
  }

  return <>{children}</>;
}