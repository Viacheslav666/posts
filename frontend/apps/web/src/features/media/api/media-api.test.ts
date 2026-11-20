import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';

import { XSRF_COOKIE_NAME, resetApiClient, setAccessToken } from '@/shared/api-client';

import { server } from '@/mocks/server';

import { deleteMedia, importMediaUrl } from './media-api';

const WORKSPACE_ID = '019a0000-0002-7000-8000-000000000002';
const MEDIA_ID = '019a0000-00m0-7000-8000-000000000001';

describe('media-api mutations (CSRF)', () => {
  beforeEach(() => {
    resetApiClient();
    setAccessToken('msw.access.token');
    document.cookie = `${XSRF_COOKIE_NAME}=unit-test-xsrf; path=/`;
  });

  it('deleteMedia sends X-XSRF-TOKEN via shared API client', async () => {
    let xsrfHeader: string | null = null;

    server.use(
      http.delete(`/api/v1/media/${MEDIA_ID}`, ({ request }) => {
        xsrfHeader = request.headers.get('X-XSRF-TOKEN');
        return HttpResponse.json({ message: 'deleted' });
      }),
    );

    const result = await deleteMedia(MEDIA_ID);
    expect(result.message).toBe('deleted');
    expect(xsrfHeader).toBe('unit-test-xsrf');
  });

  it('importMediaUrl sends X-XSRF-TOKEN via shared API client', async () => {
    let xsrfHeader: string | null = null;

    server.use(
      http.post('/api/v1/media/import-url', async ({ request }) => {
        xsrfHeader = request.headers.get('X-XSRF-TOKEN');
        const body = (await request.json()) as { workspaceId: string; url: string };
        return HttpResponse.json(
          {
            id: '019a0000-00m0-7000-8000-000000000099',
            workspaceId: body.workspaceId,
            kind: 'image',
            status: 'ready',
            filename: 'imported.jpg',
          },
          { status: 201 },
        );
      }),
    );

    const item = await importMediaUrl({
      workspaceId: WORKSPACE_ID,
      url: 'https://example.com/photo.jpg',
      filename: 'imported.jpg',
    });
    expect(item.filename).toBe('imported.jpg');
    expect(xsrfHeader).toBe('unit-test-xsrf');
  });
});