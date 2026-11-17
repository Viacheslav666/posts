import { CROP_PRESETS, type CropPreset } from '../model/types';

export type CropRect = {
  x: number;
  y: number;
  width: number;
  height: number;
};

/**
 * Compute a centered crop rectangle for the given aspect preset
 * that fits inside the source image.
 */
export function centeredCropRect(
  sourceWidth: number,
  sourceHeight: number,
  preset: CropPreset,
): CropRect {
  const aspect = CROP_PRESETS[preset];
  const sourceAspect = sourceWidth / sourceHeight;

  if (sourceAspect > aspect) {
    const height = sourceHeight;
    const width = Math.round(height * aspect);
    return {
      x: Math.round((sourceWidth - width) / 2),
      y: 0,
      width,
      height,
    };
  }

  const width = sourceWidth;
  const height = Math.round(width / aspect);
  return {
    x: 0,
    y: Math.round((sourceHeight - height) / 2),
    width,
    height,
  };
}

/**
 * Draw a crop into an offscreen canvas and return a JPEG blob + object URL.
 * Uses same-origin canvas only (no external images).
 */
export async function cropImageToBlob(
  source: CanvasImageSource & { width: number; height: number },
  crop: CropRect,
  mime: string = 'image/jpeg',
  quality = 0.92,
): Promise<{ blob: Blob; width: number; height: number }> {
  const canvas = document.createElement('canvas');
  canvas.width = crop.width;
  canvas.height = crop.height;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('Canvas 2D unavailable');
  }
  ctx.drawImage(
    source,
    crop.x,
    crop.y,
    crop.width,
    crop.height,
    0,
    0,
    crop.width,
    crop.height,
  );

  const blob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (result) => {
        if (!result) {
          reject(new Error('Failed to encode cropped image'));
          return;
        }
        resolve(result);
      },
      mime,
      quality,
    );
  });

  return { blob, width: crop.width, height: crop.height };
}
