'use client';

import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Skeleton,
} from '@posts/ui';
import { useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';

import { OPEN_CREATE_WORKSPACE_EVENT } from './nav-model';

import { CreateWorkspaceForm } from '@/features/workspace/components/create-workspace-form';
import { useMe } from '@/shared/hooks/use-me';

type WorkspaceSwitcherProps = {
  currentWorkspaceId: string;
};

export function WorkspaceSwitcher({ currentWorkspaceId }: WorkspaceSwitcherProps) {
  const t = useTranslations('workspaceSwitcher');
  const router = useRouter();
  const { data, isLoading } = useMe();
  const [open, setOpen] = useState(false);

  const memberships = data?.memberships ?? [];

  useEffect(() => {
    const openDialog = () => setOpen(true);
    window.addEventListener(OPEN_CREATE_WORKSPACE_EVENT, openDialog);
    return () => window.removeEventListener(OPEN_CREATE_WORKSPACE_EVENT, openDialog);
  }, []);
  const value = memberships.some((m) => m.workspaceId === currentWorkspaceId)
    ? currentWorkspaceId
    : (memberships[0]?.workspaceId ?? currentWorkspaceId);

  if (isLoading) {
    return (
      <div className="space-y-2" data-testid="workspace-switcher-loading">
        <Skeleton className="h-3 w-20" />
        <Skeleton className="h-10 w-full" />
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <label className="px-1 text-xs font-medium text-muted-foreground" htmlFor="workspace-switcher">
        {t('label')}
      </label>
      <Select
        value={value}
        onValueChange={(next) => {
          router.push(`/app/${next}`);
        }}
      >
        <SelectTrigger id="workspace-switcher" aria-label={t('label')} className="w-full">
          <SelectValue placeholder={t('placeholder')} />
        </SelectTrigger>
        <SelectContent>
          {memberships.map((membership) => (
            <SelectItem key={membership.workspaceId} value={membership.workspaceId!}>
              {membership.workspaceName}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogTrigger asChild>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="w-full justify-start px-1 text-xs"
            data-testid="create-workspace-open"
          >
            {t('create')}
          </Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('createTitle')}</DialogTitle>
            <DialogDescription>{t('createDescription')}</DialogDescription>
          </DialogHeader>
          <CreateWorkspaceForm onSuccess={() => setOpen(false)} />
        </DialogContent>
      </Dialog>
    </div>
  );
}
