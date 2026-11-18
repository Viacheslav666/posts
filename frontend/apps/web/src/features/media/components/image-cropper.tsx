'use client';

import { Button } from '@posts/ui';
import { useEffect, useMemo, useState } from 'react';
import { useTranslations } from 'next-intl';

import { centeredCropRect } from '../lib/crop-image';
import { CROP_PRESETS, type CropPreset } from '../model/types';

type ImageCropperProps = {
  src: string;
  width: number;
  height: number;
  value?: CropPreset;
  onChange: (preset: CropPreset, rect: { x: number; y: number; width: number; height: number }) => void;
  onClose?: () => void;
};

export function ImageCropper({ src, width, height, value, onChange, onClose }: ImageCropperProps) {
  const t = useTranslations('media.crop');
  const [preset, setPreset] = useState<CropPreset>(value ?? '1:1');

  const rect = useMemo(() => centeredCropRect(width, height, preset), [width, height, preset]);

  useEffect(() => {
    onChange(preset, rect);
    // Emit only when crop geometry changes — parent may pass unstable onChange.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [preset, rect.x, rect.y, rect.width, rect.height]);

  const previewAspect = CROP_PRESETS[preset];

  return (
    <div className="space-y-3" data-testid="image-cropper">
      <div className="flex flex-wrap gap-2" role="group" aria-label={t('presets')}>
        {(Object.keys(CROP_PRESETS) as CropPreset[]).map((key) => (
          <Button
            key={key}
            type="button"
            size="sm"
            variant={preset === key ? 'secondary' : 'outline'}
            onClick={() => setPreset(key)}
            aria-pressed={preset === key}
          >
            {key}
          </Button>
        ))}
      </div>
      <div
        className="relative mx-auto overflow-hidden rounded-md bg-muted"
        style={{
          width: Math.min(280, width),
          aspectRatio: String(previewAspect),
        }}
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={src}
          alt=""
          className="h-full w-full object-cover"
          style={{
            objectPosition: `${(rect.x / width) * 100}% ${(rect.y / height) * 100}%`,
          }}
        />
      </div>
      <p className="text-xs text-muted-foreground">
        {t('size', { width: rect.width, height: rect.height })}
      </p>
      {onClose ? (
        <Button type="button" size="sm" variant="ghost" onClick={onClose}>
          {t('done')}
        </Button>
      ) : null}
    </div>
  );
}
