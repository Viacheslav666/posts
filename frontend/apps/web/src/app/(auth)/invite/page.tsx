import { getTranslations } from 'next-intl/server';

import { AuthShell } from '@/features/auth/components/auth-shell';
import { AcceptInvitationForm } from '@/features/team/components/accept-invitation-form';

type InvitePageProps = {
  searchParams: Promise<{ token?: string }>;
};

export default async function InvitePage({ searchParams }: InvitePageProps) {
  const { token } = await searchParams;
  const t = await getTranslations('invite');
  const common = await getTranslations('common');

  return (
    <AuthShell brand={common('brand')} title={t('title')} description={t('description')}>
      <AcceptInvitationForm initialToken={token ?? ''} />
    </AuthShell>
  );
}
