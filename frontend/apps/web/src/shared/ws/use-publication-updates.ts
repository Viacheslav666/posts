'use client';

import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';

import { listPostPublications } from '@/features/posts/api/posts-api';
import { queryKeys } from '@/shared/api-client';
import { getAccessToken } from '@/shared/api-client/session';

import {
  applyPublicationUpdated,
  patchCalendarItem,
  patchPostInList,
  reduceConnectionState,
  type PublicationUpdatedEvent,
  type WsConnectionState,
} from './publication-ws-reducer';
import { createPublicationStompClient, shouldUseStomp } from './stomp-client';

const POLL_INTERVAL_MS = 8_000;
const STOMP_FAIL_THRESHOLD = 3;

type UsePublicationUpdatesOptions = {
  workspaceId: string;
  /** When set, also poll this post's publications as fallback. */
  postId?: string;
  enabled?: boolean;
};

/**
 * Live publication statuses via STOMP with polling fallback.
 * Updates TanStack Query caches for publications, posts list, and calendar.
 */
export function usePublicationUpdates({
  workspaceId,
  postId,
  enabled = true,
}: UsePublicationUpdatesOptions): WsConnectionState {
  const queryClient = useQueryClient();
  const [connection, setConnection] = useState<WsConnectionState>('idle');
  const failCount = useRef(0);

  useEffect(() => {
    if (!enabled || !workspaceId) return;

    const applyEvent = (event: PublicationUpdatedEvent) => {
      setConnection((prev) => reduceConnectionState(prev, { type: 'event', event }));

      queryClient.setQueriesData({ queryKey: queryKeys.publications.all(workspaceId) }, (old) => {
        if (!old || typeof old !== 'object') return old;
        if ('items' in (old as object)) {
          const page = old as { items?: unknown[] };
          const items = (page.items ?? []) as Parameters<typeof applyPublicationUpdated>[0] extends never
            ? never
            : import('@posts/api-client').Publication[];
          const indexed = applyPublicationUpdated(
            {
              byId: Object.fromEntries(items.filter((i) => i.id).map((i) => [i.id!, i])),
              byPostId: {},
            },
            event,
          );
          return {
            ...page,
            items: items.map((item) =>
              item.id === event.publication.id ? indexed.byId[item.id!]! : item,
            ),
          };
        }
        return old;
      });

      if (event.publication.postId || event.postId) {
        const pid = event.publication.postId ?? event.postId!;
        queryClient.setQueryData(
          queryKeys.publications.byPost(workspaceId, pid),
          (old: { items?: import('@posts/api-client').Publication[] } | undefined) => {
            if (!old?.items) {
              return { items: [event.publication], hasMore: false };
            }
            const exists = old.items.some((i) => i.id === event.publication.id);
            return {
              ...old,
              items: exists
                ? old.items.map((i) =>
                    i.id === event.publication.id ? { ...i, ...event.publication } : i,
                  )
                : [...old.items, event.publication],
            };
          },
        );
      }

      queryClient.setQueriesData(
        { queryKey: queryKeys.posts.all(workspaceId) },
        (old: unknown) => {
          if (!old || typeof old !== 'object') return old;
          if ('items' in (old as object)) {
            const page = old as { items: import('@posts/api-client').Post[] };
            return { ...page, items: patchPostInList(page.items ?? [], event) };
          }
          if ('id' in (old as object) && (old as { id?: string }).id === event.postId) {
            return { ...(old as object), status: event.postStatus };
          }
          return old;
        },
      );

      queryClient.setQueriesData(
        { queryKey: queryKeys.calendar.all(workspaceId) },
        (old: unknown) => {
          if (!Array.isArray(old)) return old;
          return patchCalendarItem(old, event);
        },
      );
    };

    let stomp = createPublicationStompClient({
      workspaceId,
      accessToken: getAccessToken(),
      disabled: !shouldUseStomp(),
      onEvent: applyEvent,
      onConnected: () => {
        failCount.current = 0;
        setConnection((prev) => reduceConnectionState(prev, { type: 'connected' }));
      },
      onDisconnected: () => {
        setConnection((prev) => reduceConnectionState(prev, { type: 'disconnected' }));
      },
      onError: () => {
        failCount.current += 1;
        if (failCount.current >= STOMP_FAIL_THRESHOLD) {
          setConnection((prev) => reduceConnectionState(prev, { type: 'fallback_polling' }));
        }
      },
    });

    if (shouldUseStomp()) {
      setConnection((prev) => reduceConnectionState(prev, { type: 'connect' }));
      stomp.activate();
    } else {
      setConnection((prev) => reduceConnectionState(prev, { type: 'fallback_polling' }));
    }

    const poll = async () => {
      if (!postId) {
        // Soft invalidate calendar/posts to pick up status changes
        await queryClient.invalidateQueries({
          queryKey: queryKeys.calendar.all(workspaceId),
          refetchType: 'active',
        });
        return;
      }
      try {
        const page = await listPostPublications(postId);
        queryClient.setQueryData(queryKeys.publications.byPost(workspaceId, postId), page);
      } catch {
        // ignore poll errors
      }
    };

    const timer = window.setInterval(() => {
      void poll();
    }, POLL_INTERVAL_MS);

    // Immediate poll when starting in fallback mode
    if (!shouldUseStomp()) {
      void poll();
    }

    return () => {
      window.clearInterval(timer);
      void stomp.deactivate();
    };
  }, [enabled, workspaceId, postId, queryClient]);

  return connection;
}
