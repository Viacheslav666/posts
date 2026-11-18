'use client';

import { Button, toast } from '@posts/ui';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import { startStoryConnection } from '../api/stories-api';

type StoryConnectButtonProps = {
  channelId: string;
  workspaceId: string;
  onConnected?: () => void;
};

export function StoryConnectButton({
  channelId,
  workspaceId,
  onConnected,
}: StoryConnectButtonProps) {
  const t = useTranslations('stories.connect');
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => startStoryConnection(channelId, { confirmReady: true }),
    onSuccess: async (result) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.channels.all(workspaceId) });
      toast({
        title: t('successTitle'),
        description: result.status === 'ready' ? t('ready') : t('pending'),
      });
      onConnected?.();
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
    <Button
      type="button"
      size="sm"
      variant="outline"
      data-testid={`story-connect-${channelId}`}
      disabled={mutation.isPending}
      onClick={() => mutation.mutate()}
    >
      {mutation.isPending ? t('connecting') : t('cta')}
    </Button>
  );
}
