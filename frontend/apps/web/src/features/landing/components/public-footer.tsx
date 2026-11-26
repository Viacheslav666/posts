import { getTranslations } from 'next-intl/server';
import Link from 'next/link';
import type { ReactNode } from 'react';

import { LegalRequisitesView, loadLegalRequisiteRows } from './legal-requisites';

export async function PublicFooter({ testId = 'public-footer' }: { testId?: string }) {
  const t = await getTranslations('landing');
  const common = await getTranslations('common');
  const { rows } = await loadLegalRequisiteRows();
  const year = new Date().getFullYear();

  return (
    <footer className="border-t border-border/60 bg-background" data-testid={testId}>
      <div className="mx-auto grid w-full max-w-6xl gap-10 px-4 py-14 sm:grid-cols-2 sm:px-6 lg:grid-cols-4 lg:px-8">
        <div>
          <p className="font-display text-xl font-bold text-foreground">{common('brand')}</p>
          <p className="mt-3 text-sm text-muted-foreground">{t('footer.rights', { year })}</p>
        </div>
        <FooterColumn title={t('footer.product')}>
          <Link href="/pricing" className="hover:text-foreground">
            {t('footer.pricing')}
          </Link>
          <Link href="/login" className="hover:text-foreground">
            {t('footer.login')}
          </Link>
          <Link href="/register" className="hover:text-foreground">
            {t('footer.register')}
          </Link>
        </FooterColumn>
        <FooterColumn title={t('footer.legal')}>
          <Link href="/legal/offer" className="hover:text-foreground">
            {t('footer.offer')}
          </Link>
          <Link href="/legal/privacy" className="hover:text-foreground">
            {t('footer.privacy')}
          </Link>
          <Link href="/legal/personal-data" className="hover:text-foreground">
            {t('footer.personalData')}
          </Link>
        </FooterColumn>
        <FooterColumn title={t('footer.company')}>
          <Link href="/legal/contacts" className="hover:text-foreground">
            {t('footer.contacts')}
          </Link>
          <LegalRequisitesView rows={rows} variant="compact" />
        </FooterColumn>
      </div>
    </footer>
  );
}

function FooterColumn({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div>
      <p className="text-sm font-semibold text-foreground">{title}</p>
      <div className="mt-3 flex flex-col gap-2 text-sm text-muted-foreground">{children}</div>
    </div>
  );
}
