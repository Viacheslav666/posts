import { OrdSettingsPanel } from '@/features/ord/components/ord-settings-panel';

type OrdSettingsPageProps = {
  params: Promise<{ workspace: string }>;
  searchParams: Promise<{ tab?: string | string[] }>;
};

const TABS = ['connection', 'contractors', 'contracts', 'reports'] as const;

export default async function OrdSettingsPage({ params, searchParams }: OrdSettingsPageProps) {
  const { workspace } = await params;
  const query = await searchParams;
  const raw = query.tab;
  const value = Array.isArray(raw) ? raw[0] : raw;
  const tab = TABS.find((item) => item === value) ?? 'connection';
  return <OrdSettingsPanel workspaceId={workspace} initialTab={tab} />;
}