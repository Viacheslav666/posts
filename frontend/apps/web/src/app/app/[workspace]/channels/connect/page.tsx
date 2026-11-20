import { redirect } from 'next/navigation';

type ChannelsConnectPageProps = {
  params: Promise<{ workspace: string }>;
};

/** OAuth callback lives under {@code …/connect/{network}/callback}; this hub redirects to channels list. */
export default async function ChannelsConnectPage({ params }: ChannelsConnectPageProps) {
  const { workspace } = await params;
  redirect(`/app/${workspace}/channels`);
}