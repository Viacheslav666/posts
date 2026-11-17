import { describe, expect, it } from 'vitest';

import {
  applyPublicationUpdated,
  indexPublications,
  patchCalendarItem,
  patchPostInList,
  reduceConnectionState,
  type PublicationUpdatedEvent,
} from './publication-ws-reducer';

const event: PublicationUpdatedEvent = {
  type: 'publication.updated',
  postId: 'post-1',
  postStatus: 'publishing',
  publication: {
    id: 'pub-1',
    postId: 'post-1',
    state: 'in_progress',
    network: 'vk',
  },
};

describe('publication ws reducer', () => {
  it('indexes and applies publication updates', () => {
    const cache = indexPublications([
      { id: 'pub-1', postId: 'post-1', state: 'pending', network: 'vk' },
    ]);
    const next = applyPublicationUpdated(cache, event);
    expect(next.byId['pub-1']?.state).toBe('in_progress');
  });

  it('patches posts list and calendar items', () => {
    expect(
      patchPostInList([{ id: 'post-1', status: 'scheduled' }, { id: 'post-2' }], event)[0]
        ?.status,
    ).toBe('publishing');
    expect(
      patchCalendarItem([{ id: 'post-1', status: 'scheduled' }], event)[0]?.status,
    ).toBe('publishing');
  });

  it('reduces connection state including polling fallback', () => {
    let state = reduceConnectionState('idle', { type: 'connect' });
    expect(state).toBe('connecting');
    state = reduceConnectionState(state, { type: 'connected' });
    expect(state).toBe('open');
    state = reduceConnectionState(state, { type: 'fallback_polling' });
    expect(state).toBe('polling');
    expect(reduceConnectionState('closed', { type: 'connect' })).toBe('connecting');
  });
});
