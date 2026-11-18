import { redirect } from 'next/navigation';

type AutomationsPageProps = {
  params: Promise<{ workspace: string }>;
};

export default async function AutomationsPage({ params }: AutomationsPageProps) {
  const { workspace } = await params;
  redirect(`/app/${workspace}/automations/grabber`);
}
