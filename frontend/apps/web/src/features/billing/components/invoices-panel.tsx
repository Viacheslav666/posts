'use client';

import { Button, EmptyState, PendingFeatureNotice, Skeleton, toast } from '@posts/ui';
import { useMutation } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { useState } from 'react';

import { getErrorMessage } from '@/shared/lib/errors';

import { downloadInvoiceActPdf, downloadInvoicePdf, requestInvoice } from '../api/billing-api';
import { useInvoices } from '../hooks/use-billing-queries';
import { loadLegalDraft } from '../model/legal-schema';
import { formatKopecks, rublesToKopecks } from '../model/money';

type InvoicesPanelProps = {
  workspaceId: string;
  canManage?: boolean;
};

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

function formatDate(iso?: string): string {
  if (!iso) return '—';
  try {
    return new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short' }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export function InvoicesPanel({ workspaceId, canManage }: InvoicesPanelProps) {
  const t = useTranslations('billing.invoices');
  const [cursor, setCursor] = useState<string | undefined>();
  const [accumulated, setAccumulated] = useState<NonNullable<ReturnType<typeof useInvoices>['data']>['items']>([]);
  const query = useInvoices(workspaceId, { cursor, limit: 10 });

  const items = !cursor ? (query.data?.items ?? []) : [...(accumulated ?? []), ...(query.data?.items ?? [])];

  const downloadPdf = useMutation({
    mutationFn: (id: string) => downloadInvoicePdf(id),
    onSuccess: ({ blob, filename }) => {
      triggerDownload(blob, filename);
      toast({ title: t('downloadOk') });
    },
    onError: (error) => {
      toast({ variant: 'destructive', title: t('downloadError'), description: getErrorMessage(error) });
    },
  });

  const downloadAct = useMutation({
    mutationFn: (id: string) => downloadInvoiceActPdf(id),
    onSuccess: ({ blob, filename }) => {
      triggerDownload(blob, filename);
      toast({ title: t('downloadOk') });
    },
    onError: (error) => {
      toast({ variant: 'destructive', title: t('downloadError'), description: getErrorMessage(error) });
    },
  });

  const createInvoice = useMutation({
    mutationFn: async () => {
      const draft = loadLegalDraft(workspaceId);
      if (!draft) {
        throw new Error(t('needRequisites'));
      }
      return requestInvoice({
        workspaceId,
        amountKopecks: rublesToKopecks(5000),
        buyer: {
          name: draft.companyName,
          inn: draft.inn,
          kpp: draft.kpp || undefined,
          ogrn: draft.ogrn || undefined,
          address: draft.address,
        },
      });
    },
    onSuccess: () => {
      setCursor(undefined);
      setAccumulated([]);
      void query.refetch();
      toast({ title: t('created') });
    },
    onError: (error) => {
      toast({ variant: 'destructive', title: t('createError'), description: getErrorMessage(error) });
    },
  });

  return (
    <section className="space-y-4 rounded-xl border border-border p-4" data-testid="billing-invoices">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-foreground">{t('title')}</h2>
          <p className="text-sm text-muted-foreground">{t('description')}</p>
        </div>
        {canManage ? (
          <Button
            type="button"
            size="sm"
            variant="outline"
            data-testid="billing-invoice-request"
            disabled={createInvoice.isPending}
            onClick={() => createInvoice.mutate()}
          >
            {createInvoice.isPending ? t('creating') : t('request')}
          </Button>
        ) : null}
      </div>

      <PendingFeatureNotice title={t('receiptsPendingTitle')} body={t('receiptsPendingBody')} />

      {query.isLoading && !cursor ? (
        <div className="space-y-2">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      ) : null}

      {!query.isLoading && items.length === 0 ? (
        <EmptyState title={t('emptyTitle')} description={t('emptyBody')} data-testid="billing-invoices-empty" />
      ) : null}

      {items.length > 0 ? (
        <div className="overflow-x-auto" tabIndex={0} role="region" aria-label={t('title')}>
          <table className="w-full min-w-[560px] text-left text-sm" data-testid="billing-invoices-table">
            <thead className="border-b border-border text-muted-foreground">
              <tr>
                <th className="px-2 py-2 font-medium">{t('columns.number')}</th>
                <th className="px-2 py-2 font-medium">{t('columns.date')}</th>
                <th className="px-2 py-2 font-medium">{t('columns.amount')}</th>
                <th className="px-2 py-2 font-medium">{t('columns.status')}</th>
                <th className="px-2 py-2 font-medium">{t('columns.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((invoice) => (
                <tr key={invoice.id} className="border-b border-border/60" data-testid={`billing-invoice-${invoice.id}`}>
                  <td className="px-2 py-2">{invoice.invoiceNumber ?? invoice.id}</td>
                  <td className="px-2 py-2">{formatDate(invoice.createdAt)}</td>
                  <td className="px-2 py-2">{formatKopecks(invoice.amountKopecks ?? 0)}</td>
                  <td className="px-2 py-2">{invoice.status ?? '—'}</td>
                  <td className="px-2 py-2">
                    <div className="flex flex-wrap gap-2">
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        data-testid={`billing-invoice-pdf-${invoice.id}`}
                        disabled={downloadPdf.isPending}
                        onClick={() => invoice.id && downloadPdf.mutate(invoice.id)}
                      >
                        {t('pdf')}
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        data-testid={`billing-invoice-act-${invoice.id}`}
                        disabled={downloadAct.isPending}
                        onClick={() => invoice.id && downloadAct.mutate(invoice.id)}
                      >
                        {t('act')}
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

      {query.data?.hasMore ? (
        <Button
          type="button"
          variant="outline"
          size="sm"
          data-testid="billing-invoices-more"
          onClick={() => {
            setAccumulated(items);
            setCursor(query.data?.nextCursor);
          }}
        >
          {t('loadMore')}
        </Button>
      ) : null}
    </section>
  );
}
