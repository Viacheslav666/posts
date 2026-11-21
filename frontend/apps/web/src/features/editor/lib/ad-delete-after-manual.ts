/** Clear stored {@code deleteAfter} when ORD auto-delete transitions from on to off (draft only). */
export function shouldClearDeleteAfterOnAdAutoOff(
  prevAdAuto: boolean,
  nextAdAuto: boolean,
  manual: boolean,
  hasDeleteAfter: boolean,
): boolean {
  return prevAdAuto && !nextAdAuto && !manual && hasDeleteAfter;
}