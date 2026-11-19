'use client';

import type { AntispamRule, AntispamWordList } from '@posts/api-client';
import { Button, EmptyState, Input, Skeleton, toast } from '@posts/ui';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { useMemo, useState } from 'react';

import { parseKeywordPaste } from '@/features/automations-common/model/keywords';
import { queryKeys } from '@/shared/api-client';
import { getErrorMessage } from '@/shared/lib/errors';

import {
  createAntispamWordList,
  deleteAntispamWordList,
  listAntispamWordLists,
  patchAntispamWordList,
} from '../api/antispam-api';
import { collectWordListUsage } from '../model/config';
import { belongsToWorkspace } from '../model/scope';

type WordListsPanelProps = {
  workspaceId: string;
  rules: AntispamRule[];
  canManage: boolean;
};

export function WordListsPanel({ workspaceId, rules, canManage }: WordListsPanelProps) {
  const t = useTranslations('automations.antispam.wordLists');
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [mode, setMode] = useState<'black' | 'white'>('black');
  const [wordsRaw, setWordsRaw] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);

  const listsQuery = useInfiniteQuery({
    queryKey: queryKeys.antispam.wordLists(workspaceId),
    queryFn: ({ pageParam }) =>
      listAntispamWordLists({ workspaceId, cursor: pageParam, limit: 50 }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => (last.hasMore && last.nextCursor ? last.nextCursor : undefined),
  });

  const lists = useMemo(
    () =>
      (listsQuery.data?.pages ?? [])
        .flatMap((page) => page.items ?? [])
        .filter((list) => belongsToWorkspace(list, workspaceId)),
    [listsQuery.data?.pages, workspaceId],
  );

  const createMutation = useMutation({
    mutationFn: () =>
      createAntispamWordList(workspaceId, {
        name: name.trim(),
        mode,
        words: parseKeywordPaste(wordsRaw),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.antispam.all(workspaceId) });
      setName('');
      setWordsRaw('');
      toast({ title: t('createSuccess') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (listId: string) => deleteAntispamWordList(listId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.antispam.all(workspaceId) });
      toast({ title: t('deleteSuccess') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  const updateMutation = useMutation({
    mutationFn: (list: AntispamWordList) => {
      if (!belongsToWorkspace(list, workspaceId)) {
        return Promise.reject(new Error('word list is outside this workspace'));
      }
      return patchAntispamWordList(list.id!, {
        name: list.name,
        words: list.words,
        mode: list.mode,
        version: list.version ?? 1,
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.antispam.all(workspaceId) });
      setEditingId(null);
      toast({ title: t('updateSuccess') });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  if (listsQuery.isLoading) {
    return <Skeleton className="h-40 w-full" data-testid="antispam-word-lists-loading" />;
  }

  if (listsQuery.isError) {
    return (
      <EmptyState
        role="alert"
        title={t('errorTitle')}
        description={getErrorMessage(listsQuery.error)}
        action={
          <Button type="button" variant="outline" onClick={() => void listsQuery.refetch()}>
            {t('retry')}
          </Button>
        }
      />
    );
  }

  return (
    <section
      className="space-y-4 rounded-md border border-border p-4"
      data-testid="antispam-word-lists"
      aria-label={t('title')}
    >
      <div>
        <h2 className="text-lg font-semibold">{t('title')}</h2>
        <p className="text-sm text-muted-foreground">{t('description')}</p>
      </div>

      {canManage ? (
        <form
          className="space-y-3"
          onSubmit={(event) => {
            event.preventDefault();
            createMutation.mutate();
          }}
          data-testid="antispam-word-list-create"
        >
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <label htmlFor="antispam-list-name" className="text-sm font-medium">
                {t('name')}
              </label>
              <Input
                id="antispam-list-name"
                value={name}
                onChange={(event) => setName(event.target.value)}
                data-testid="antispam-word-list-name"
              />
            </div>
            <div className="space-y-1">
              <label htmlFor="antispam-list-mode" className="text-sm font-medium">
                {t('mode')}
              </label>
              <select
                id="antispam-list-mode"
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
                value={mode}
                onChange={(event) => setMode(event.target.value as 'black' | 'white')}
                data-testid="antispam-word-list-mode"
              >
                <option value="black">{t('modes.black')}</option>
                <option value="white">{t('modes.white')}</option>
              </select>
            </div>
          </div>
          <div className="space-y-1">
            <label htmlFor="antispam-list-words" className="text-sm font-medium">
              {t('words')}
            </label>
            <textarea
              id="antispam-list-words"
              className="min-h-24 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={wordsRaw}
              onChange={(event) => setWordsRaw(event.target.value)}
              placeholder={t('wordsPlaceholder')}
              data-testid="antispam-word-list-words"
            />
            <p className="text-xs text-muted-foreground">{t('importHint')}</p>
          </div>
          <Button
            type="submit"
            disabled={!name.trim() || createMutation.isPending}
            data-testid="antispam-word-list-submit"
          >
            {t('create')}
          </Button>
        </form>
      ) : null}

      {lists.length === 0 ? (
        <EmptyState title={t('emptyTitle')} description={t('emptyBody')} />
      ) : (
        <ul className="divide-y divide-border" data-testid="antispam-word-lists-items">
          {lists.map((list) => {
            const usage = collectWordListUsage(rules, list.id!);
            const isEditing = editingId === list.id;
            return (
              <li
                key={list.id}
                className="flex flex-col gap-2 py-3 sm:flex-row sm:items-start sm:justify-between"
                data-testid={`antispam-word-list-${list.id}`}
              >
                <div className="min-w-0 space-y-1">
                  <p className="font-medium">
                    {list.name}{' '}
                    <span className="text-xs font-normal text-muted-foreground">
                      ({list.mode === 'white' ? t('modes.white') : t('modes.black')})
                    </span>
                  </p>
                  <p className="text-xs text-muted-foreground">
                    {t('usage', { black: usage.black, white: usage.white, count: list.words?.length ?? 0 })}
                  </p>
                  {isEditing ? (
                    <textarea
                      className="mt-2 min-h-20 w-full max-w-lg rounded-md border border-input bg-background px-3 py-2 text-sm"
                      aria-label={t('words')}
                      defaultValue={(list.words ?? []).join('\n')}
                      data-testid={`antispam-word-list-edit-${list.id}`}
                      onBlur={(event) => {
                        updateMutation.mutate({
                          ...list,
                          words: parseKeywordPaste(event.target.value),
                        });
                      }}
                    />
                  ) : (
                    <p className="truncate text-sm text-muted-foreground">
                      {(list.words ?? []).slice(0, 8).join(', ')}
                      {(list.words?.length ?? 0) > 8 ? '…' : ''}
                    </p>
                  )}
                </div>
                {canManage ? (
                  <div className="flex flex-wrap gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => setEditingId(isEditing ? null : list.id!)}
                      data-testid={`antispam-word-list-edit-btn-${list.id}`}
                    >
                      {isEditing ? t('done') : t('edit')}
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      disabled={deleteMutation.isPending}
                      onClick={() => list.id && deleteMutation.mutate(list.id)}
                      data-testid={`antispam-word-list-delete-${list.id}`}
                    >
                      {t('delete')}
                    </Button>
                  </div>
                ) : null}
              </li>
            );
          })}
          </ul>
      )}
      {listsQuery.hasNextPage ? (
        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={listsQuery.isFetchingNextPage}
          onClick={() => void listsQuery.fetchNextPage()}
          data-testid="antispam-word-lists-load-more"
        >
          {listsQuery.isFetchingNextPage ? t('loadingMore') : t('loadMore')}
        </Button>
      ) : null}
    </section>
  );
}
