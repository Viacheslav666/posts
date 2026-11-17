import type { ReactNode } from 'react';

import { PublicHeader } from '@/components/layout/public-header';

export default function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <PublicHeader />
      {children}
    </>
  );
}
