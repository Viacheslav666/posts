import { Suspense } from 'react';

import { CheckoutReturn } from '@/features/billing/components/checkout-return';

export default function BillingReturnRoute() {
  return (
    <Suspense fallback={<div className="p-8 text-center text-sm text-muted-foreground">Загрузка…</div>}>
      <CheckoutReturn />
    </Suspense>
  );
}
