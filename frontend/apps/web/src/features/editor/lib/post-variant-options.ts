import type { PostVariantOptions } from '@posts/api-client';

export function resolvePublishAtIso(
  publishMode: 'schedule' | 'now',
  scheduledAt: string,
  now = new Date(),
): string {
  return publishMode === 'now' ? now.toISOString() : scheduledAt;
}

/** Do not send unpin when pin is off (publisher also ignores unpin without pin). */
export function sanitizeVariantOptions(variant: PostVariantOptions): PostVariantOptions {
  if (!variant.pin) {
    const { unpinAt: _removed, ...rest } = variant;
    return { ...rest, pin: false, unpinAt: undefined };
  }
  return variant;
}

export function publishAtForUnpinCheck(
  submitMode: 'draft' | 'schedule' | 'now',
  publishMode: 'schedule' | 'now',
  scheduledAt: string,
  now = new Date(),
): string | null {
  if (submitMode === 'draft') {
    return null;
  }
  if (submitMode === 'now' || publishMode === 'now') {
    return now.toISOString();
  }
  return scheduledAt || null;
}

export function validateUnpinAt(unpinAt: string | undefined, publishAtIso: string): boolean {
  if (!unpinAt) {
    return true;
  }
  const unpin = new Date(unpinAt).getTime();
  const publish = new Date(publishAtIso).getTime();
  if (Number.isNaN(unpin) || Number.isNaN(publish)) {
    return false;
  }
  return unpin > publish;
}