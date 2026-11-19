'use client';

import { Input, Select, SelectContent, SelectItem, SelectTrigger, SelectValue, cn } from '@posts/ui';
import { useTranslations } from 'next-intl';

import {
  clampLimitAmount,
  LIMIT_PERIODS,
  type LimitPeriod,
  type LimitValue,
} from '../model/limits';

export type LimitInputProps = {
  value: LimitValue;
  onChange: (next: LimitValue) => void;
  disabled?: boolean;
  id?: string;
  className?: string;
  'aria-label'?: string;
};

export function LimitInput({
  value,
  onChange,
  disabled,
  id = 'limit-input',
  className,
  'aria-label': ariaLabel,
}: LimitInputProps) {
  const t = useTranslations('automations.common.limit');

  return (
    <div
      className={cn('flex flex-wrap items-center gap-2', className)}
      data-testid="limit-input"
      role="group"
      aria-label={ariaLabel ?? t('label')}
    >
      <Input
        id={id}
        type="number"
        min={0}
        step={1}
        inputMode="numeric"
        disabled={disabled}
        value={value.amount ?? ''}
        placeholder={t('amountPlaceholder')}
        aria-label={t('amount')}
        className="w-24"
        onChange={(event) => {
          const raw = event.target.value;
          onChange({
            ...value,
            amount: raw === '' ? null : clampLimitAmount(Number(raw)),
          });
        }}
      />
      <span className="text-sm text-muted-foreground">{t('per')}</span>
      <Select
        value={value.period}
        disabled={disabled}
        onValueChange={(period) => onChange({ ...value, period: period as LimitPeriod })}
      >
        <SelectTrigger className="w-36" aria-label={t('period')} data-testid="limit-period">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {LIMIT_PERIODS.map((period) => (
            <SelectItem key={period} value={period}>
              {t(`periods.${period}`)}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}
