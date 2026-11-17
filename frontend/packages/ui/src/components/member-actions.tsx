import type { HTMLAttributes, ReactNode } from 'react';

import { cn } from '../lib/utils';
import { Button } from './button';

export type MemberActionsBarProps = HTMLAttributes<HTMLDivElement> & {
  canInvite?: boolean;
  canUpdate?: boolean;
  canRemove?: boolean;
  onInvite?: () => void;
  onUpdate?: () => void;
  onRemove?: () => void;
  inviteLabel?: ReactNode;
  updateLabel?: ReactNode;
  removeLabel?: ReactNode;
};

/**
 * Presentational action strip for team member rows / toolbars.
 * Visibility is controlled purely by permission props (a11y-friendly).
 */
export function MemberActionsBar({
  canInvite = false,
  canUpdate = false,
  canRemove = false,
  onInvite,
  onUpdate,
  onRemove,
  inviteLabel = 'Пригласить',
  updateLabel = 'Изменить',
  removeLabel = 'Удалить',
  className,
  ...props
}: MemberActionsBarProps) {
  if (!canInvite && !canUpdate && !canRemove) {
    return null;
  }

  return (
    <div
      className={cn('flex flex-wrap items-center gap-2', className)}
      role="group"
      aria-label="Действия с участником"
      data-testid="member-actions"
      {...props}
    >
      {canInvite ? (
        <Button type="button" size="sm" onClick={onInvite} data-testid="member-action-invite">
          {inviteLabel}
        </Button>
      ) : null}
      {canUpdate ? (
        <Button
          type="button"
          size="sm"
          variant="secondary"
          onClick={onUpdate}
          data-testid="member-action-update"
        >
          {updateLabel}
        </Button>
      ) : null}
      {canRemove ? (
        <Button
          type="button"
          size="sm"
          variant="destructive"
          onClick={onRemove}
          data-testid="member-action-remove"
        >
          {removeLabel}
        </Button>
      ) : null}
    </div>
  );
}
