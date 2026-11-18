'use client';

import type { GrabberRule } from '@posts/api-client';
import { Button, Select, SelectContent, SelectItem, SelectTrigger, SelectValue, toast } from '@posts/ui';
import { useMutation } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { useState } from 'react';

import { getErrorMessage } from '@/shared/lib/errors';

import { cloneIngestRuleConfig } from '../api/grabber-api';

type CloneConfigControlProps = {
  ruleId: string;
  siblingRules: GrabberRule[];
  onCloned: (rule: GrabberRule) => void;
};

export function CloneConfigControl({ ruleId, siblingRules, onCloned }: CloneConfigControlProps) {
  const t = useTranslations('grabber.form.clone');
  const [sourceRuleId, setSourceRuleId] = useState<string>('');
  const options = siblingRules.filter((rule) => rule.id && rule.id !== ruleId);

  const mutation = useMutation({
    mutationFn: async () => {
      if (!sourceRuleId) throw new Error('source_required');
      return cloneIngestRuleConfig(ruleId, { sourceRuleId });
    },
    onSuccess: (rule) => {
      toast({ title: t('success') });
      onCloned(rule);
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  if (!options.length) {
    return (
      <p className="text-xs text-muted-foreground" data-testid="grabber-clone-empty">
        {t('empty')}
      </p>
    );
  }

  return (
    <div
      className="flex flex-wrap items-end gap-2 rounded-md border border-border p-3"
      data-testid="grabber-clone-config"
    >
      <div className="min-w-[220px] flex-1 space-y-1.5">
        <label className="text-sm font-medium" htmlFor="grabber-clone-source">
          {t('label')}
        </label>
        <Select value={sourceRuleId} onValueChange={setSourceRuleId}>
          <SelectTrigger id="grabber-clone-source" data-testid="grabber-clone-source">
            <SelectValue placeholder={t('placeholder')} />
          </SelectTrigger>
          <SelectContent>
            {options.map((rule) => (
              <SelectItem key={rule.id} value={rule.id!}>
                {rule.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <p className="text-xs text-muted-foreground">{t('hint')}</p>
      </div>
      <Button
        type="button"
        variant="outline"
        disabled={!sourceRuleId || mutation.isPending}
        onClick={() => mutation.mutate()}
        data-testid="grabber-clone-submit"
      >
        {t('submit')}
      </Button>
    </div>
  );
}
