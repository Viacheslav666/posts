import type { AdMarking } from '@posts/api-client';

import type { EditorPostOptions } from './types';

export function defaultScheduledAt(from = new Date()): string {
  const d = new Date(from.getTime() + 60_000);
  d.setSeconds(0, 0);
  return d.toISOString();
}

export function createDefaultAdMarking(): AdMarking {
  return {
    advertiserName: '',
    inn: '',
    kpp: '',
    orgType: 'juridical',
    amount: 0,
    contractAmount: 0,
    contractDate: '',
    contractDateEnd: '',
    contractSerial: '',
    kktuCodes: [],
    oksmCountry: '643',
    vatRate: 'without_vat',
    roles: ['advertiser'],
    customerRole: 'advertiser',
    executor: 'self',
    placement: 'text_start',
    isForeign: false,
    selfPromo: false,
    socialAd: false,
    socialAdQuota: false,
    displayPeriodDays: 30,
    autoDeleteAfterPeriod: true,
    markingType: '1',
    description: '',
    phone: '',
    address: '',
    foreignInn: '',
    foreignRegNumber: '',
    foreignEpayment: '',
  };
}

export function createDefaultPostOptions(from = new Date()): EditorPostOptions {
  return {
    title: '',
    publishMode: 'schedule',
    scheduledAt: defaultScheduledAt(from),
    timezone: 'Europe/Moscow',
    recurrence: { freq: 'once', interval: 1 },
    stopAt: null,
    removeAfterPublish: false,
    repost: null,
    variant: {
      signed: false,
      mute: false,
      closeComments: false,
      pin: false,
      unpinAt: undefined,
      deleteAfter: undefined,
      donutDays: 0,
      coOwnerIds: [],
      primaryAttachmentsMode: 'grid',
      firstComment: '',
      buttons: [],
      poll: undefined,
      geo: undefined,
      actionButton: undefined,
      copyrightUrl: '',
    },
    isAd: false,
    ad: createDefaultAdMarking(),
    ordProvider: 'vk_ord',
  };
}
