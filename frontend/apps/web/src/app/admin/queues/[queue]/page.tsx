import { AdminQueueMessagesPage } from '@/features/admin/components/queue-messages-page';

type PageProps = {
  params: Promise<{ queue: string }>;
};

export default async function Page({ params }: PageProps) {
  const { queue } = await params;
  return <AdminQueueMessagesPage queue={decodeURIComponent(queue)} />;
}
