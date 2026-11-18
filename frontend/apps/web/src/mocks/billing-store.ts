import type {
  AutopaySettings,
  CheckoutResponse,
  Invoice,
  PaymentListItem,
  PaymentMethod,
  Plan,
  Transaction,
  Wallet,
} from '@posts/api-client';

import { PERSONAL_WORKSPACE_ID, ACME_WORKSPACE_ID, getWorkspace, setWorkspaceBilling } from './workspace-store';

export type PlanLimits = {
  channels: number | null;
  seats: number | null;
  posts_month: number | null;
  ai_credits: number | null;
  grabber_rules: number | null;
  stories_month: number | null;
};

const PLAN_DEFS: Array<{
  code: string;
  name: string;
  priceMonth: number;
  priceYear: number;
  priceDayKopecks: number;
  billingModel: 'balance' | 'subscription';
  limits: PlanLimits;
}> = [
  {
    code: 'free',
    name: 'Free',
    priceMonth: 0,
    priceYear: 0,
    priceDayKopecks: 0,
    billingModel: 'balance',
    limits: {
      channels: 3,
      seats: 1,
      posts_month: 30,
      ai_credits: 20,
      grabber_rules: 0,
      stories_month: 0,
    },
  },
  {
    code: 'daily_6',
    name: 'До 6 каналов',
    priceMonth: 390,
    priceYear: 3744,
    priceDayKopecks: 1300,
    billingModel: 'balance',
    limits: {
      channels: 6,
      seats: 1,
      posts_month: null,
      ai_credits: 200,
      grabber_rules: 1,
      stories_month: 30,
    },
  },
  {
    code: 'daily_12',
    name: 'До 12 каналов',
    priceMonth: 510,
    priceYear: 4896,
    priceDayKopecks: 1700,
    billingModel: 'balance',
    limits: {
      channels: 12,
      seats: 2,
      posts_month: null,
      ai_credits: 400,
      grabber_rules: 2,
      stories_month: 50,
    },
  },
  {
    code: 'daily_25',
    name: 'До 25 каналов',
    priceMonth: 810,
    priceYear: 7776,
    priceDayKopecks: 2700,
    billingModel: 'balance',
    limits: {
      channels: 25,
      seats: 3,
      posts_month: null,
      ai_credits: 1000,
      grabber_rules: 5,
      stories_month: 100,
    },
  },
  {
    code: 'daily_50',
    name: 'До 50 каналов',
    priceMonth: 1110,
    priceYear: 10_656,
    priceDayKopecks: 3700,
    billingModel: 'balance',
    limits: {
      channels: 50,
      seats: 5,
      posts_month: null,
      ai_credits: 2000,
      grabber_rules: 10,
      stories_month: 200,
    },
  },
  // Legacy codes kept for older MSW fixtures / admin demos
  {
    code: 'start',
    name: 'Старт',
    priceMonth: 390,
    priceYear: 3744,
    priceDayKopecks: 1300,
    billingModel: 'balance',
    limits: {
      channels: 10,
      seats: 1,
      posts_month: null,
      ai_credits: 200,
      grabber_rules: 1,
      stories_month: 30,
    },
  },
  {
    code: 'pro',
    name: 'Про',
    priceMonth: 990,
    priceYear: 9504,
    priceDayKopecks: 2700,
    billingModel: 'balance',
    limits: {
      channels: 30,
      seats: 3,
      posts_month: null,
      ai_credits: 1000,
      grabber_rules: 5,
      stories_month: 100,
    },
  },
  {
    code: 'agency',
    name: 'Агентство',
    priceMonth: 2990,
    priceYear: 28704,
    priceDayKopecks: 9700,
    billingModel: 'balance',
    limits: {
      channels: 100,
      seats: 10,
      posts_month: null,
      ai_credits: 5000,
      grabber_rules: 20,
      stories_month: 500,
    },
  },
];

function nextId(prefix: string, suffix: string): string {
  return `019a0000-${prefix}-7000-8000-${suffix.padStart(12, '0')}`;
}

type WalletRecord = {
  balanceKopecks: number;
  bonusBalanceKopecks: number;
  planCode: string;
  billingStatus: NonNullable<Wallet['billingStatus']>;
  paymentMethod: PaymentMethod | null;
};

type BillingState = {
  plans: Plan[];
  wallets: Record<string, WalletRecord>;
  autopay: Record<string, AutopaySettings>;
  transactions: Transaction[];
  payments: PaymentListItem[];
  invoices: Invoice[];
};

function daysRemaining(balanceKopecks: number, priceDayKopecks: number): number | undefined {
  if (priceDayKopecks <= 0) {
    return undefined;
  }
  return Math.floor(balanceKopecks / priceDayKopecks);
}

function seedTransactions(workspaceId: string): Transaction[] {
  const now = Date.now();
  return [
    {
      id: nextId('tx01', '1'),
      createdAt: new Date(now - 3 * 86_400_000).toISOString(),
      amountKopecks: 20_000,
      kind: 'payment',
      bonus: false,
      method: 'yookassa',
      receiptUrl: 'yookassa-receipt-demo-1',
      details: 'Пополнение через ЮKassa',
      paymentId: nextId('pay1', '1'),
    },
    {
      id: nextId('tx01', '2'),
      createdAt: new Date(now - 2 * 86_400_000).toISOString(),
      amountKopecks: -2700,
      kind: 'charge',
      bonus: false,
      details: 'Посуточное списание',
    },
    {
      id: nextId('tx01', '3'),
      createdAt: new Date(now - 1 * 86_400_000).toISOString(),
      amountKopecks: -2700,
      kind: 'charge',
      bonus: false,
      details: 'Посуточное списание',
    },
    {
      id: nextId('tx01', '4'),
      createdAt: new Date(now - 12 * 3600_000).toISOString(),
      amountKopecks: 5000,
      kind: 'promo',
      bonus: true,
      details: 'Промокод WELCOME',
    },
    {
      id: nextId('tx01', '5'),
      createdAt: new Date(now - 6 * 3600_000).toISOString(),
      amountKopecks: -1500,
      kind: 'refund',
      bonus: false,
      method: 'yookassa',
      details: 'Частичный возврат',
      paymentId: nextId('pay1', '1'),
    },
  ].map((tx) => ({ ...tx, /* workspace scoped via filter below */ }));
}

function createInitialState(): BillingState {
  const plans = PLAN_DEFS.map((def, index) => ({
    id: nextId('bill', String(index + 1)),
    code: def.code,
    name: def.name,
    priceMonth: def.priceMonth,
    priceYear: def.priceYear,
    priceDayKopecks: def.priceDayKopecks,
    priceMonthDisplay: def.priceDayKopecks > 0 ? (def.priceDayKopecks * 30) / 100 : def.priceMonth,
    billingModel: def.billingModel,
    limits: { ...def.limits } as unknown as Plan['limits'],
  }));

  // Personal: low balance (~2 days) for e2e «низкий баланс»; not frozen.
  const personalPrice = 2700;
  const personalBalance = personalPrice * 2;
  // ACME: healthy balance
  const acmePrice = 9700;
  const acmeBalance = acmePrice * 40;

  const wallets: Record<string, WalletRecord> = {
    [PERSONAL_WORKSPACE_ID]: {
      balanceKopecks: personalBalance,
      bonusBalanceKopecks: 5000,
      planCode: 'pro',
      billingStatus: 'active',
      paymentMethod: {
        providerPaymentMethodId: 'pm_demo_personal',
        cardMask: '**** 4242',
        type: 'bank_card',
      },
    },
    [ACME_WORKSPACE_ID]: {
      balanceKopecks: acmeBalance,
      bonusBalanceKopecks: 0,
      planCode: 'agency',
      billingStatus: 'active',
      paymentMethod: null,
    },
  };

  const autopay: Record<string, AutopaySettings> = {
    [PERSONAL_WORKSPACE_ID]: {
      workspaceId: PERSONAL_WORKSPACE_ID,
      enabled: false,
      thresholdKopecks: 10_000,
      topupAmountKopecks: 50_000,
      paymentMethod: wallets[PERSONAL_WORKSPACE_ID]!.paymentMethod ?? undefined,
      lastTriggeredAt: undefined,
    },
    [ACME_WORKSPACE_ID]: {
      workspaceId: ACME_WORKSPACE_ID,
      enabled: false,
      thresholdKopecks: 10_000,
      topupAmountKopecks: 50_000,
    },
  };

  const personalTx = seedTransactions(PERSONAL_WORKSPACE_ID).map((tx) => ({
    ...tx,
    // tag via details prefix for filtering in list — workspace filtered in handler by wallet scope
  }));

  return {
    plans,
    wallets,
    autopay,
    transactions: personalTx.map((tx) => ({ ...tx, details: tx.details })),
    payments: [
      {
        id: nextId('pay1', '1'),
        createdAt: new Date(Date.now() - 3 * 86_400_000).toISOString(),
        amountKopecks: 20_000,
        status: 'succeeded',
        purpose: 'topup',
        provider: 'yookassa',
        receiptId: 'yookassa-receipt-demo-1',
        method: 'bank_card',
      },
    ],
    invoices: [
      {
        id: nextId('inv1', '1'),
        workspaceId: PERSONAL_WORKSPACE_ID,
        amount: 5000,
        amountKopecks: 500_000,
        currency: 'RUB',
        status: 'issued',
        invoiceNumber: 'СЧ-2026-0001',
        pdfUrl: `/api/v1/billing/invoices/${nextId('inv1', '1')}/pdf`,
        actPdfUrl: `/api/v1/billing/invoices/${nextId('inv1', '1')}/act`,
        createdAt: new Date(Date.now() - 5 * 86_400_000).toISOString(),
      },
    ],
  };
}

/** Workspace id stamped onto demo txs (personal only in seed). */
const DEMO_TX_WORKSPACE = PERSONAL_WORKSPACE_ID;

let state = createInitialState();
let txWorkspaceIndex: Record<string, string> = Object.fromEntries(
  state.transactions.map((tx) => [tx.id!, DEMO_TX_WORKSPACE]),
);
let paymentWorkspaceIndex: Record<string, string> = Object.fromEntries(
  state.payments.map((p) => [p.id!, DEMO_TX_WORKSPACE]),
);

export function resetBillingStore(): void {
  state = createInitialState();
  txWorkspaceIndex = Object.fromEntries(state.transactions.map((tx) => [tx.id!, DEMO_TX_WORKSPACE]));
  paymentWorkspaceIndex = Object.fromEntries(state.payments.map((p) => [p.id!, DEMO_TX_WORKSPACE]));
}

export function listPlans(): Plan[] {
  return state.plans.map((plan) => ({
    ...plan,
    limits: { ...(plan.limits ?? {}) } as Plan['limits'],
  }));
}

export function getPlanByCode(code: string): Plan | undefined {
  return state.plans.find((plan) => plan.code === code);
}

export function getPlanLimits(code: string): PlanLimits {
  const found = PLAN_DEFS.find((plan) => plan.code === code);
  return found?.limits ?? PLAN_DEFS[0]!.limits;
}

export function upsertPlanLimits(input: {
  code: string;
  name: string;
  priceMonth: number;
  priceYear: number;
  limits: Record<string, number | null>;
  priceDayKopecks?: number;
  billingModel?: string;
}): Plan {
  const existing = state.plans.find((plan) => plan.code === input.code);
  if (existing) {
    existing.name = input.name;
    existing.priceMonth = input.priceMonth;
    existing.priceYear = input.priceYear;
    existing.limits = { ...input.limits } as unknown as Plan['limits'];
    if (input.priceDayKopecks !== undefined) {
      existing.priceDayKopecks = input.priceDayKopecks;
    }
    if (input.billingModel !== undefined) {
      existing.billingModel = input.billingModel;
    }
    return { ...existing, limits: { ...(existing.limits ?? {}) } as Plan['limits'] };
  }
  const created: Plan = {
    id: nextId('bill', String(state.plans.length + 10)),
    code: input.code,
    name: input.name,
    priceMonth: input.priceMonth,
    priceYear: input.priceYear,
    priceDayKopecks: input.priceDayKopecks ?? 0,
    billingModel: input.billingModel ?? 'balance',
    limits: { ...input.limits } as unknown as Plan['limits'],
  };
  state.plans = [...state.plans, created];
  return { ...created, limits: { ...(created.limits ?? {}) } as Plan['limits'] };
}

function ensureWallet(workspaceId: string): WalletRecord {
  const existing = state.wallets[workspaceId];
  if (existing) {
    return existing;
  }
  const workspace = getWorkspace(workspaceId);
  const planCode = workspace?.planCode ?? 'free';
  const plan = getPlanByCode(planCode);
  const created: WalletRecord = {
    balanceKopecks: 0,
    bonusBalanceKopecks: 0,
    planCode,
    billingStatus: workspace?.billingStatus ?? 'active',
    paymentMethod: null,
  };
  if (plan?.priceDayKopecks) {
    // keep zeros
  }
  state.wallets[workspaceId] = created;
  state.autopay[workspaceId] = {
    workspaceId,
    enabled: false,
    thresholdKopecks: 10_000,
    topupAmountKopecks: 50_000,
  };
  return created;
}

export function getWallet(workspaceId: string): Wallet {
  const wallet = ensureWallet(workspaceId);
  const workspace = getWorkspace(workspaceId);
  const planCode = workspace?.planCode ?? wallet.planCode;
  const plan = getPlanByCode(planCode);
  const priceDayKopecks = plan?.priceDayKopecks ?? 0;
  const billingStatus = workspace?.billingStatus ?? wallet.billingStatus;
  return {
    workspaceId,
    balanceKopecks: wallet.balanceKopecks,
    bonusBalanceKopecks: wallet.bonusBalanceKopecks,
    refundableKopecks: Math.max(0, wallet.balanceKopecks - wallet.bonusBalanceKopecks),
    daysRemaining: daysRemaining(wallet.balanceKopecks, priceDayKopecks),
    priceDayKopecks,
    planCode,
    billingStatus,
  };
}

/** Test helper: patch wallet fields (also syncs workspace billingStatus when set). */
export function patchWallet(
  workspaceId: string,
  patch: Partial<WalletRecord> & { billingStatus?: WalletRecord['billingStatus'] },
): Wallet {
  const wallet = ensureWallet(workspaceId);
  if (patch.balanceKopecks !== undefined) {
    wallet.balanceKopecks = patch.balanceKopecks;
  }
  if (patch.bonusBalanceKopecks !== undefined) {
    wallet.bonusBalanceKopecks = patch.bonusBalanceKopecks;
  }
  if (patch.planCode !== undefined) {
    wallet.planCode = patch.planCode;
  }
  if (patch.billingStatus !== undefined) {
    wallet.billingStatus = patch.billingStatus;
    setWorkspaceBilling(workspaceId, { billingStatus: patch.billingStatus });
  }
  if (patch.paymentMethod !== undefined) {
    wallet.paymentMethod = patch.paymentMethod;
  }
  return getWallet(workspaceId);
}

export function getAutopay(workspaceId: string): AutopaySettings {
  ensureWallet(workspaceId);
  const settings = state.autopay[workspaceId]!;
  const wallet = state.wallets[workspaceId]!;
  return {
    ...settings,
    paymentMethod: wallet.paymentMethod ?? undefined,
  };
}

export function updateAutopay(
  workspaceId: string,
  input: { enabled: boolean; thresholdKopecks?: number; topupAmountKopecks?: number },
): AutopaySettings {
  const wallet = ensureWallet(workspaceId);
  const settings = state.autopay[workspaceId]!;
  if (input.enabled && !wallet.paymentMethod) {
    const error = new Error('Saved payment method required to enable autopay');
    (error as Error & { code: string }).code = 'validation_failed';
    throw error;
  }
  settings.enabled = input.enabled;
  if (input.thresholdKopecks !== undefined) {
    settings.thresholdKopecks = input.thresholdKopecks;
  }
  if (input.topupAmountKopecks !== undefined) {
    if (input.topupAmountKopecks < 10_000) {
      const error = new Error('Autopay top-up below minimum');
      (error as Error & { code: string }).code = 'validation_failed';
      throw error;
    }
    settings.topupAmountKopecks = input.topupAmountKopecks;
  }
  settings.paymentMethod = wallet.paymentMethod ?? undefined;
  return { ...settings };
}

export function getPaymentMethod(workspaceId: string): PaymentMethod | null {
  return ensureWallet(workspaceId).paymentMethod;
}

export function deletePaymentMethod(workspaceId: string): void {
  const wallet = ensureWallet(workspaceId);
  wallet.paymentMethod = null;
  const settings = state.autopay[workspaceId];
  if (settings) {
    settings.enabled = false;
    settings.paymentMethod = undefined;
  }
}

function paginate<T extends { id?: string }>(
  items: T[],
  cursor?: string | null,
  limit = 20,
): { items: T[]; nextCursor?: string; hasMore: boolean } {
  const start = cursor ? Number.parseInt(cursor, 10) || 0 : 0;
  const slice = items.slice(start, start + limit);
  const next = start + limit;
  const hasMore = next < items.length;
  return {
    items: slice,
    hasMore,
    nextCursor: hasMore ? String(next) : undefined,
  };
}

export function listTransactions(
  workspaceId: string,
  options?: { kind?: string | null; cursor?: string | null; limit?: number },
) {
  let items = state.transactions.filter((tx) => txWorkspaceIndex[tx.id!] === workspaceId);
  if (options?.kind) {
    items = items.filter((tx) => tx.kind === options.kind);
  }
  return paginate(items, options?.cursor, options?.limit ?? 20);
}

export function listPayments(
  workspaceId: string,
  options?: { cursor?: string | null; limit?: number },
) {
  const items = state.payments.filter((p) => paymentWorkspaceIndex[p.id!] === workspaceId);
  return paginate(items, options?.cursor, options?.limit ?? 20);
}

export function listInvoices(
  workspaceId: string,
  options?: { cursor?: string | null; limit?: number },
) {
  const items = state.invoices.filter((inv) => inv.workspaceId === workspaceId);
  return paginate(items, options?.cursor, options?.limit ?? 20);
}

export function getInvoice(invoiceId: string): Invoice | undefined {
  return state.invoices.find((inv) => inv.id === invoiceId);
}

export function requestInvoice(
  workspaceId: string,
  amountKopecks: number,
  buyer: Record<string, string>,
): Invoice {
  const id = nextId('inv1', String(state.invoices.length + 10));
  const invoice: Invoice = {
    id,
    workspaceId,
    amount: amountKopecks / 100,
    amountKopecks,
    currency: 'RUB',
    status: 'issued',
    invoiceNumber: `СЧ-2026-${String(state.invoices.length + 1).padStart(4, '0')}`,
    pdfUrl: `/api/v1/billing/invoices/${id}/pdf`,
    actPdfUrl: `/api/v1/billing/invoices/${id}/act`,
    buyerRequisites: buyer as unknown as Invoice['buyerRequisites'],
    createdAt: new Date().toISOString(),
  };
  state.invoices = [invoice, ...state.invoices];
  return invoice;
}

export function checkoutTopup(workspaceId: string, amountKopecks: number): CheckoutResponse {
  if (amountKopecks < 10_000) {
    const error = new Error('Top-up amount below minimum');
    (error as Error & { code: string }).code = 'validation_failed';
    throw error;
  }
  ensureWallet(workspaceId);
  const paymentId = nextId('pay1', String(state.payments.length + 20));
  const confirmationUrl = `/billing/return?paymentId=${paymentId}&workspaceId=${workspaceId}&mock=pending`;
  const payment: PaymentListItem = {
    id: paymentId,
    createdAt: new Date().toISOString(),
    amountKopecks,
    status: 'pending',
    purpose: 'topup',
    provider: 'yookassa',
    confirmationUrl,
    method: 'bank_card',
  };
  state.payments = [payment, ...state.payments];
  paymentWorkspaceIndex[paymentId] = workspaceId;
  return {
    paymentId,
    confirmationUrl,
    provider: 'yookassa',
  };
}

export function getPayment(paymentId: string): PaymentListItem | undefined {
  return state.payments.find((p) => p.id === paymentId);
}

export function completeMockPayment(
  paymentId: string,
  outcome: 'succeeded' | 'canceled' | 'pending' = 'succeeded',
): PaymentListItem | undefined {
  const payment = state.payments.find((p) => p.id === paymentId);
  if (!payment) {
    return undefined;
  }
  const workspaceId = paymentWorkspaceIndex[paymentId];
  if (!workspaceId) {
    return payment;
  }
  payment.status = outcome;
  if (outcome !== 'succeeded') {
    return payment;
  }
  const wallet = ensureWallet(workspaceId);
  wallet.balanceKopecks += payment.amountKopecks ?? 0;
  if (wallet.billingStatus === 'frozen' && wallet.balanceKopecks > 0) {
    wallet.billingStatus = 'active';
    setWorkspaceBilling(workspaceId, { billingStatus: 'active' });
  }
  if (!wallet.paymentMethod) {
    wallet.paymentMethod = {
      providerPaymentMethodId: `pm_${paymentId}`,
      cardMask: '**** 4242',
      type: 'bank_card',
    };
  }
  payment.receiptId = `yookassa-receipt-${paymentId.slice(-8)}`;
  const txId = nextId('tx01', String(state.transactions.length + 20));
  const tx: Transaction = {
    id: txId,
    createdAt: new Date().toISOString(),
    amountKopecks: payment.amountKopecks,
    kind: 'payment',
    bonus: false,
    method: 'yookassa',
    receiptUrl: payment.receiptId,
    details: 'Пополнение через ЮKassa',
    paymentId,
  };
  state.transactions = [tx, ...state.transactions];
  txWorkspaceIndex[txId] = workspaceId;
  return payment;
}

export function changePlan(workspaceId: string, planCode: string): {
  id: string;
  workspaceId: string;
  planCode: string;
  status: NonNullable<Wallet['billingStatus']>;
  billingModel: string;
  cancelAtPeriodEnd: boolean;
} {
  const plan = getPlanByCode(planCode);
  if (!plan) {
    const error = new Error('Plan not found');
    (error as Error & { code: string }).code = 'not_found';
    throw error;
  }
  const wallet = ensureWallet(workspaceId);
  wallet.planCode = planCode;
  setWorkspaceBilling(workspaceId, { planCode });
  return {
    id: workspaceId,
    workspaceId,
    planCode,
    status: wallet.billingStatus,
    billingModel: plan.billingModel ?? 'balance',
    cancelAtPeriodEnd: false,
  };
}

/** Minimal PDF bytes for invoice/act download mocks. */
export function mockPdfBytes(label: string): Uint8Array {
  const text = `%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%% ${label}\n`;
  return new TextEncoder().encode(text);
}
