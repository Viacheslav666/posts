'use client';

type MediaPlaybackProps = {
  src: string;
  poster?: string | null;
  className?: string;
  label?: string;
};

/** Ready video or clip. Native controls, no autoplay, inline on iPhone Safari. */
export function hasPlayback(kind?: string | null, playbackUrl?: string | null): playbackUrl is string {
  return (kind === 'video' || kind === 'clip') && typeof playbackUrl === 'string' && playbackUrl.length > 0;
}

export function MediaPlayback({ src, poster, className, label }: MediaPlaybackProps) {
  return (
    <video
      src={src}
      poster={poster || undefined}
      controls
      playsInline
      preload="metadata"
      className={className}
      aria-label={label}
      data-testid="media-playback"
    />
  );
}
