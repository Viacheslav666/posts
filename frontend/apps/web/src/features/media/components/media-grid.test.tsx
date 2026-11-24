import { fireEvent, render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import { describe, expect, it } from 'vitest';

import messages from '@/messages/ru.json';

import type { MediaLibraryItem } from '../model/types';
import { MediaGrid } from './media-grid';

const ready = (item: MediaLibraryItem): MediaLibraryItem => ({
  status: 'ready',
  kind: 'image',
  ...item,
});

function renderGrid(items: MediaLibraryItem[]) {
  return render(
    <NextIntlClientProvider locale="ru" messages={messages}>
      <MediaGrid items={items} />
    </NextIntlClientProvider>,
  );
}

describe('MediaGrid previews', () => {
  it('renders the preview image when previewUrl is set', () => {
    renderGrid([
      ready({
        id: 'photo-1',
        filename: 'photo.jpg',
        kind: 'image',
        previewUrl: 'https://cdn.example/preview.jpg',
      }),
    ]);

    const img = screen.getByTestId('media-preview-image');
    expect(img).toHaveAttribute('src', 'https://cdn.example/preview.jpg');
    expect(img).toHaveAttribute('loading', 'lazy');
    expect(img).toHaveClass('object-cover');
    expect(screen.queryByTestId('media-preview-fallback')).not.toBeInTheDocument();
  });

  it('renders the kind icon when previewUrl is null', () => {
    renderGrid([
      ready({
        id: 'doc-1',
        filename: 'brief.pdf',
        kind: 'doc',
        previewUrl: undefined,
      }),
    ]);

    expect(screen.queryByTestId('media-preview-image')).not.toBeInTheDocument();
    expect(screen.getByTestId('media-preview-fallback')).toBeInTheDocument();
  });

  it('falls back to the icon when the image fails to load', () => {
    renderGrid([
      ready({
        id: 'video-1',
        filename: 'clip.mp4',
        kind: 'video',
        previewUrl: 'https://cdn.example/poster.jpg',
      }),
    ]);

    fireEvent.error(screen.getByTestId('media-preview-image'));

    expect(screen.queryByTestId('media-preview-image')).not.toBeInTheDocument();
    expect(screen.getByTestId('media-preview-fallback')).toBeInTheDocument();
  });
});
