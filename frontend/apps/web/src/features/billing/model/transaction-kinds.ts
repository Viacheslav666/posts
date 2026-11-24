/** Виды ledger-записей (BE-12b). */
export const TRANSACTION_KINDS = [
  'payment',
  'charge',
  'refund',
  'promo',
  'admin_adjustment',
  'neural',
] as const;

export type TransactionKind = (typeof TRANSACTION_KINDS)[number];

export function isTransactionKind(value: string | null | undefined): value is TransactionKind {
  return value != null && (TRANSACTION_KINDS as readonly string[]).includes(value);
}

/** Чек 54-ФЗ: API кладёт в receiptUrl id ЮKassa, не download URL. */
export function isDownloadableReceiptUrl(value: string | null | undefined): boolean {
  if (!value) {
    return false;
  }
  return /^https?:\/\//i.test(value);
}
