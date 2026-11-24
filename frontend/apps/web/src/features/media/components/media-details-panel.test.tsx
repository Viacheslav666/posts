import { render, screen } from '@testing-library/react';
import { NextIntlClientProvider } from 'next-intl';
import { describe, expect, it } from 'vitest';

import messages from '@/messages/ru.json';

import type { MediaLibraryItem } from '../model/types';
import { MediaDetailsPanel } from './media-details-panel';

function renderDetails(item: MediaLibraryItem) {
  return render(
    <NextIntlClientProvider locale="ru" messages={messages}>
      <MediaDetailsPanel item={item} open onOpenChange={() => undefined} />
    </NextIntlClientProvider>,
  );
}

describe('MediaDetailsPanel playback', () => {
  it('renders a video element with src and poster for a ready video', () => {
    renderDetails({
      id: 'vid-1',
      filename: 'reel.mp4',
      kind: 'video',
      status: 'ready',
      mime: 'video/mp4',
      previewUrl: 'https://cdn.example/poster.jpg',
      playbackUrl: 'https://cdn.example/play.mp4',
    });

    const video = screen.getByTestId('media-playback');
    expect(video.tagName).toBe('VIDEO');
    expect(video).toHaveAttribute('src', 'https://cdn.example/play.mp4');
    expect(video).toHaveAttribute('poster', 'https://cdn.example/poster.jpg');
    expect(video).toHaveAttribute('playsinline');
    expect(video).toHaveAttribute('preload', 'metadata');
    expect(video).toHaveAttribute('controls');
    expect(video).not.toHaveAttribute('autoplay');
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('falls back to the preview image when playbackUrl is missing', () => {
    renderDetails({
      id: 'vid-2',
      filename: 'reel.mov',
      kind: 'clip',
      status: 'processing',
      mime: 'video/quicktime',
      previewUrl: 'https://cdn.example/poster.jpg',
      playbackUrl: null,
    });

    expect(screen.queryByTestId('media-playback')).not.toBeInTheDocument();
    expect(screen.getByRole('img')).toHaveAttribute('src', 'https://cdn.example/poster.jpg');
  });
});
