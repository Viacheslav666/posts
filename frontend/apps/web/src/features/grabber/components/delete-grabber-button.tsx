'use client';

import { Button, ConfirmActionDialog, toast } from '@posts/ui';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useState } from 'react';

import { queryKeys } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import { archiveIngestRule } from '../api/grabber-api';

type DeleteGrabberButtonProps = {
  ruleId: string;
  workspaceId: string;
  /** After archive, leave the detail page for the rules list. */
  redirectToList?: boolean;
};

export function DeleteGrabberButton({
  ruleId,
  workspaceId,
  redirectToList = false,
}: DeleteGrabberButtonProps) {
  const t = useTranslations('grabber.rules');
  const router = useRouter();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);

  const mutation = useMutation({
    mutationFn: () => archiveIngestRule(ruleId),
    onSuccess: async () => {
      setOpen(false);
      await queryClient.invalidateQueries({ queryKey: queryKeys.ingest.all(workspaceId) });
      toast({ title: t('deleteSuccess') });
      if (redirectToList) {
        router.push(`/app/${workspaceId}/automations/grabber`);
      }
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  return (
    <>
      <Button
        size="sm"
        variant="outline"
        disabled={mutation.isPending}
        onClick={() => setOpen(true)}
        data-testid={`grabber-delete-${ruleId}`}
      >
        {t('delete')}
      </Button>
      <ConfirmActionDialog
        open={open}
        onOpenChange={setOpen}
        title={t('deleteTitle')}
        description={t('deleteBody')}
        confirmLabel={t('deleteConfirm')}
        cancelLabel={t('deleteCancel')}
        busy={mutation.isPending}
        testId="grabber-delete-dialog"
        confirmTestId="grabber-delete-confirm"
        onConfirm={() => mutation.mutate()}
      />
    </>
  );
}
