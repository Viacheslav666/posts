import { redirect } from 'next/navigation';

type LegacyInvitePageProps = {
  searchParams: Promise<{ token?: string }>;
};

/** Back-compat for invitation emails that used {@code /invitations/accept}. */
export default async function LegacyInvitationAcceptPage({ searchParams }: LegacyInvitePageProps) {
  const { token } = await searchParams;
  const query = token ? `?token=${encodeURIComponent(token)}` : '';
  redirect(`/invite${query}`);
}