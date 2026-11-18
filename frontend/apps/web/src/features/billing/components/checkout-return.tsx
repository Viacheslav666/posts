'use client';

import { Button, Skeleton } from '@posts/ui';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useEffect, useState } from 'react';

import { applyTokenResponse, getAccessToken, getApiClient } from '@/shared/api-client';

import { completeMockPayment, listPayments } from '../api/billing-api';

type CheckoutPhase = 'pending' | 'succeeded' | 'canceled' | 'error';

type CheckoutReturnProps = {
  /** Optional workspace from app route; otherwise taken from query. */
  workspaceId?: string;
};

/** After YooKassa full-page redirect the in-memory access token is gone — restore via refresh cookie. */
async function ensureAccessToken(): Promise<void> {
  if (getAccessToken()) {
    return;
  }
  const client = getApiClient();
  const result = await client.POST('/api/v1/auth/refresh', { body: {} });
  if (result.data?.accessToken) {
    applyTokenResponse(result.data);
  }
}

/**
 * Экран возврата из ЮKassa (`return_url`).
 * API отдаёт только redirect confirmationUrl (виджета в CheckoutResponse нет).
 * Статус платежа поллим через GET /billing/payments; в MSW — complete-payment.
 */
export function CheckoutReturn({ workspaceId: workspaceFromRoute }: CheckoutReturnProps) {
  const t = useTranslations('billing.return');
  const params = useSearchParams();
  const paymentId = params.get('paymentId');
  const workspaceId = workspaceFromRoute ?? params.get('workspaceId') ?? undefined;
  const mockHint = params.get('mock'); // pending | success | fail — только MSW

  const [phase, setPhase] = useState<CheckoutPhase>('pending');
  const [detail, setDetail] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let attempts = 0;

    async function resolveStatus() {
      if (!paymentId) {
        if (!cancelled) {
          setPhase('error');
          setDetail(t('missingPayment'));
        }
        return;
      }

      try {
        await ensureAccessToken();
      } catch {
        // continue — listPayments may still fail and surface the error
      }

      // MSW redirect lands with mock=pending — complete as success/fail for demos.
      if (mockHint === 'pending' || mockHint === 'success' || mockHint === 'fail') {
        try {
          const outcome =
            mockHint === 'fail' ? 'canceled' : mockHint === 'pending' ? 'succeeded' : 'succeeded';
          const updated = await completeMockPayment(paymentId, outcome === 'canceled' ? 'canceled' : 'succeeded');
          if (cancelled) return;
          if (updated.status === 'succeeded') {
            setPhase('succeeded');
          } else if (updated.status === 'canceled') {
            setPhase('canceled');
          } else {
            setPhase('pending');
          }
          return;
        } catch (error) {
          if (!cancelled) {
            setPhase('error');
            setDetail(error instanceof Error ? error.message : t('unknownError'));
          }
          return;
        }
      }

      if (!workspaceId) {
        if (!cancelled) {
          setPhase('pending');
          setDetail(t('waitingNoWorkspace'));
        }
        return;
      }

      while (!cancelled && attempts < 12) {
        attempts += 1;
        try {
          const page = await listPayments({ workspaceId, limit: 20 });
          const payment = page.items?.find((item) => item.id === paymentId);
          if (payment?.status === 'succeeded') {
            if (!cancelled) setPhase('succeeded');
            return;
          }
          if (payment?.status === 'canceled' || payment?.status === 'cancelled') {
            if (!cancelled) setPhase('canceled');
            return;
          }
        } catch (error) {
          if (!cancelled) {
            setPhase('error');
            setDetail(error instanceof Error ? error.message : t('unknownError'));
          }
          return;
        }
        await new Promise((resolve) => setTimeout(resolve, 1500));
      }

      if (!cancelled) {
        setPhase('pending');
        setDetail(t('stillPending'));
      }
    }

    void resolveStatus();
    return () => {
      cancelled = true;
    };
  }, [mockHint, paymentId, t, workspaceId]);

  const billingHref = workspaceId ? `/app/${workspaceId}/billing` : '/app';

  return (
    <div
      className="mx-auto flex min-h-[50vh] w-full max-w-lg flex-col items-center justify-center gap-4 px-4 text-center"
      data-testid="billing-checkout-return"
      data-phase={phase}
    >
      {phase === 'pending' ? (
        <>
          <Skeleton className="h-10 w-48" />
          <p className="text-sm text-muted-foreground" data-testid="billing-return-pending">
            {detail ?? t('pending')}
          </p>
        </>
      ) : null}

      {phase === 'succeeded' ? (
        <>
          <h1 className="font-display text-2xl font-semibold text-foreground">{t('successTitle')}</h1>
          <p className="text-sm text-muted-foreground" data-testid="billing-return-success">
            {t('successBody')}
          </p>
        </>
      ) : null}

      {phase === 'canceled' ? (
        <>
          <h1 className="font-display text-2xl font-semibold text-foreground">{t('errorTitle')}</h1>
          <p className="text-sm text-muted-foreground" data-testid="billing-return-canceled">
            {t('canceledBody')}
          </p>
        </>
      ) : null}

      {phase === 'error' ? (
        <>
          <h1 className="font-display text-2xl font-semibold text-foreground">{t('errorTitle')}</h1>
          <p className="text-sm text-muted-foreground" data-testid="billing-return-error">
            {detail ?? t('unknownError')}
          </p>
        </>
      ) : null}

      <Button asChild data-testid="billing-return-back">
        <Link href={billingHref}>{t('back')}</Link>
      </Button>
    </div>
  );
}
