'use client';

import { cn } from '@posts/ui';
import { FileText, Film, Image as ImageIcon, Music } from 'lucide-react';
import { useState } from 'react';

type MediaPreviewProps = {
  previewUrl?: string | null;
  kind?: string;
  iconClassName?: string;
};

function MediaKindIcon({ kind, className }: { kind?: string; className?: string }) {
  const iconClass = cn('h-8 w-8', className);
  if (kind === 'video' || kind === 'clip') return <Film className={iconClass} aria-hidden />;
  if (kind === 'audio') return <Music className={iconClass} aria-hidden />;
  if (kind === 'doc') return <FileText className={iconClass} aria-hidden />;
  return <ImageIcon className={iconClass} aria-hidden />;
}

/** Card thumbnail. Falls back to the kind icon when the URL is missing or the image fails. */
export function MediaPreview({ previewUrl, kind, iconClassName }: MediaPreviewProps) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const showImage = Boolean(previewUrl) && failedUrl !== previewUrl;

  return (
    <span className="absolute inset-0 flex items-center justify-center">
      {showImage ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={previewUrl ?? undefined}
          alt=""
          loading="lazy"
          data-testid="media-preview-image"
          className="h-full w-full object-cover"
          onError={() => setFailedUrl(previewUrl ?? null)}
        />
      ) : (
        <span data-testid="media-preview-fallback">
          <MediaKindIcon kind={kind} className={iconClassName} />
        </span>
      )}
    </span>
  );
}
