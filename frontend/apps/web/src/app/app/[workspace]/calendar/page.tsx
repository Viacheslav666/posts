import { CalendarPage } from '@/features/calendar/components/calendar-page';

type CalendarRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function WorkspaceCalendarPage({ params }: CalendarRouteProps) {
  const { workspace } = await params;
  return <CalendarPage workspaceId={workspace} />;
}
