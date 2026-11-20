import type {
  AutopaySettings,
  CheckoutResponse,
  Invoice,
  InvoicePage,
  PaymentMethod,
  PaymentPage,
  Plan,
  RequestInvoiceRequest,
  Subscription,
  TransactionPage,
  UpdateAutopayRequest,
  Wallet,
} from '@posts/api-client';

import { apiFetch, getAccessToken, getApiBaseUrl, getApiClient, unwrapApi } from '@/shared/api-client';
import { toApiProblemError } from '@/shared/lib/errors';

export async function listBillingPlans(): Promise<Plan[]> {
  const client = getApiClient();
  return unwrapApi(await client.GET('/api/v1/billing/plans'));
}

export async function getWallet(workspaceId: string): Promise<Wallet> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/billing/wallet', {
      params: { query: { workspaceId } },
    }),
  );
}

export async function createCheckout(input: {
  workspaceId: string;
  amountKopecks: number;
  promoCode?: string;
  idempotencyKey?: string;
}): Promise<CheckoutResponse> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/billing/checkout', {
      params: {
        header: input.idempotencyKey ? { 'Idempotency-Key': input.idempotencyKey } : undefined,
      },
      body: {
        workspaceId: input.workspaceId,
        amountKopecks: input.amountKopecks,
        promoCode: input.promoCode,
      },
    }),
  );
}

export async function listTransactions(input: {
  workspaceId: string;
  kind?: string;
  cursor?: string;
  limit?: number;
}): Promise<TransactionPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/billing/transactions', {
      params: {
        query: {
          workspaceId: input.workspaceId,
          kind: input.kind,
          cursor: input.cursor,
          limit: input.limit ?? 20,
        },
      },
    }),
  );
}

export async function listPayments(input: {
  workspaceId: string;
  cursor?: string;
  limit?: number;
}): Promise<PaymentPage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/billing/payments', {
      params: {
        query: {
          workspaceId: input.workspaceId,
          cursor: input.cursor,
          limit: input.limit ?? 20,
        },
      },
    }),
  );
}

export async function listInvoices(input: {
  workspaceId: string;
  cursor?: string;
  limit?: number;
}): Promise<InvoicePage> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/billing/invoices', {
      params: {
        query: {
          workspaceId: input.workspaceId,
          cursor: input.cursor,
          limit: input.limit ?? 20,
        },
      },
    }),
  );
}

export async function requestInvoice(body: RequestInvoiceRequest): Promise<Invoice> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/billing/invoices/request', {
      body,
    }),
  );
}

export async function getAutopay(workspaceId: string): Promise<AutopaySettings> {
  const client = getApiClient();
  return unwrapApi(
    await client.GET('/api/v1/billing/autopay', {
      params: { query: { workspaceId } },
    }),
  );
}

export async function updateAutopay(body: UpdateAutopayRequest): Promise<AutopaySettings> {
  const client = getApiClient();
  return unwrapApi(await client.PUT('/api/v1/billing/autopay', { body }));
}

export async function getPaymentMethod(workspaceId: string): Promise<PaymentMethod | null> {
  const client = getApiClient();
  const result = await client.GET('/api/v1/billing/payment-method', {
    params: { query: { workspaceId } },
  });
  if (result.response.status === 204) {
    return null;
  }
  return unwrapApi(result);
}

export async function deletePaymentMethod(workspaceId: string): Promise<void> {
  const client = getApiClient();
  const result = await client.DELETE('/api/v1/billing/payment-method', {
    params: { query: { workspaceId } },
  });
  if (!result.response.ok) {
    throw toApiProblemError(result.error ?? { title: result.response.statusText }, result.response.status);
  }
}

export async function changePlan(workspaceId: string, planCode: string): Promise<Subscription> {
  const client = getApiClient();
  return unwrapApi(
    await client.POST('/api/v1/billing/plan/change', {
      body: { workspaceId, planCode },
    }),
  );
}

export type BillingPdfDownload = {
  blob: Blob;
  filename: string;
};

async function downloadBillingPdf(path: string, fallbackName: string): Promise<BillingPdfDownload> {
  const headers = new Headers({ Accept: 'application/pdf' });
  const token = getAccessToken();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  const response = await fetch(`${getApiBaseUrl()}${path}`, {
    method: 'GET',
    headers,
    credentials: 'include',
  });
  if (!response.ok) {
    let problem: unknown = { title: response.statusText };
    try {
      problem = await response.json();
    } catch {
      // ignore
    }
    throw toApiProblemError(problem, response.status);
  }
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const match = disposition.match(/filename="?([^"]+)"?/i);
  return {
    blob: await response.blob(),
    filename: match?.[1] ?? fallbackName,
  };
}

export function downloadInvoicePdf(invoiceId: string): Promise<BillingPdfDownload> {
  return downloadBillingPdf(`/api/v1/billing/invoices/${invoiceId}/pdf`, `invoice-${invoiceId}.pdf`);
}

export function downloadInvoiceActPdf(invoiceId: string): Promise<BillingPdfDownload> {
  return downloadBillingPdf(`/api/v1/billing/invoices/${invoiceId}/act`, `act-${invoiceId}.pdf`);
}

/** Mock-only helper used by /billing/return in MSW mode. */
export async function completeMockPayment(
  paymentId: string,
  outcome: 'succeeded' | 'canceled' | 'pending' = 'succeeded',
): Promise<{ id?: string; status?: string }> {
  const response = await apiFetch('/api/v1/billing/mock/complete-payment', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ paymentId, outcome }),
  });
  if (!response.ok) {
    let problem: unknown = { title: response.statusText };
    try {
      problem = await response.json();
    } catch {
      // ignore
    }
    throw toApiProblemError(problem, response.status);
  }
  return (await response.json()) as { id?: string; status?: string };
}
