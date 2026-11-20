'use client';

import { useEffect } from 'react';

import { logoutRequest } from '@/features/auth/api/auth-api';
import { redirectToLogin } from '@/shared/lib/auth-redirect';

export default function LogoutPage() {
  useEffect(() => {
    void (async () => {
      try {
        await logoutRequest();
      } finally {
        redirectToLogin('logout');
      }
    })();
  }, []);

  return (
    <p className="flex min-h-[40vh] items-center justify-center text-sm text-muted-foreground" role="status">
      Выход…
    </p>
  );
}