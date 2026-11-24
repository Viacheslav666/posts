import { SectionPlaceholder } from '@/components/layout/section-placeholder';
import { PLACEHOLDER_TITLES } from '@/components/layout/nav-model';

type SoonPageProps = {
  params: Promise<{ workspace: string; slug: string }>;
};

export default async function SoonPage({ params }: SoonPageProps) {
  const { slug } = await params;
  const title = PLACEHOLDER_TITLES[slug] ?? 'Раздел';
  return <SectionPlaceholder title={title} />;
}
