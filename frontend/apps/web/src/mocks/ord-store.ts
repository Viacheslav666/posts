import type {
  AdMarking,
  CreateOrdContractRequest,
  CreateOrdContractorRequest,
  OrdConnection,
  OrdContract,
  OrdContractor,
  OrdCreativeStatus,
  OrdProvider,
  OrdRegisterCreativeResponse,
  UpdateOrdContractorRequest,
  UpsertOrdConnectionRequest,
} from '@posts/api-client';

import { isValidInn, normalizeInn } from '@/shared/lib/inn';

import { PERSONAL_WORKSPACE_ID } from './workspace-store';

export type StoredOrdConnection = OrdConnection & {
  /** Plaintext key kept only in MSW memory — never returned by API. */
  apiKey?: string;
};

export type StoredOrdCreative = OrdCreativeStatus & {
  workspaceId: string;
  kktuCodes?: string[];
};

export const KKTU_DICTIONARY = [
  { code: '1.1.1', name: 'Товары' },
  { code: '1.1.2', name: 'Услуги' },
  { code: '2.1.1', name: 'Финансовые услуги' },
  { code: '7.1.1', name: 'Информационные услуги' },
] as const;

export const OKSM_DICTIONARY = [
  { code: '643', name: 'Российская Федерация' },
  { code: '112', name: 'Республика Беларусь' },
  { code: '398', name: 'Республика Казахстан' },
] as const;

const CONTRACTOR_IDS = {
  posts: '019a0000-ordc-7000-8000-000000000001',
} as const;

type OrdStoreState = {
  connections: StoredOrdConnection[];
  contractors: OrdContractor[];
  contracts: OrdContract[];
  creatives: StoredOrdCreative[];
  seq: number;
};

function nowIso(): string {
  return new Date().toISOString();
}

function nextId(prefix: string): string {
  state.seq += 1;
  return `019a0000-${prefix}-7000-8000-${String(state.seq).padStart(12, '0')}`;
}

function createInitialState(): OrdStoreState {
  const now = nowIso();
  return {
    seq: 10,
    connections: [
      {
        id: '019a0000-ordn-7000-8000-000000000001',
        workspaceId: PERSONAL_WORKSPACE_ID,
        provider: 'vk_ord',
        status: 'active',
        hasApiKey: true,
        isDefault: true,
        publisherInn: '7707083893',
        publisherName: 'ООО Посты',
        orgType: 'juridical',
        phone: '+79990001122',
        roles: ['publisher'],
        createdAt: now,
        updatedAt: now,
        apiKey: 'vk-ord-demo-key',
      },
    ],
    contractors: [
      {
        id: CONTRACTOR_IDS.posts,
        workspaceId: PERSONAL_WORKSPACE_ID,
        inn: '7707083893',
        kpp: '770701001',
        name: 'ООО Посты',
        orgType: 'juridical',
        countryOksm: '643',
        phone: '+79990001122',
        address: 'Москва',
        createdAt: now,
        updatedAt: now,
      },
    ],
    contracts: [
      {
        id: '019a0000-ordt-7000-8000-000000000001',
        workspaceId: PERSONAL_WORKSPACE_ID,
        contractorId: CONTRACTOR_IDS.posts,
        provider: 'vk_ord',
        externalId: 'contract-demo-1',
        serial: 'Д-1/2026',
        dateStart: '2026-01-01',
        dateEnd: '2026-12-31',
        amount: 10000,
        vatRate: 'without_vat',
        selfPromo: false,
        status: 'active',
        createdAt: now,
        updatedAt: now,
      },
    ],
    creatives: [],
  };
}

let state: OrdStoreState = createInitialState();

export function resetOrdStore(): void {
  state = createInitialState();
}

export function listOrdConnections(workspaceId: string): OrdConnection[] {
  return state.connections
    .filter((item) => item.workspaceId === workspaceId)
    .map(toPublicConnection);
}

export function getOrdConnection(
  workspaceId: string,
  provider: OrdProvider,
): StoredOrdConnection | undefined {
  return state.connections.find(
    (item) => item.workspaceId === workspaceId && item.provider === provider,
  );
}

export function hasActiveOrdConnection(workspaceId: string): boolean {
  return state.connections.some(
    (item) =>
      item.workspaceId === workspaceId &&
      item.hasApiKey &&
      (item.status === 'active' || item.status === 'ok'),
  );
}

export function upsertOrdConnection(
  workspaceId: string,
  provider: OrdProvider,
  body: UpsertOrdConnectionRequest,
): StoredOrdConnection {
  const existing = getOrdConnection(workspaceId, provider);
  const now = nowIso();
  if (existing) {
    if (body.apiKey && body.apiKey.trim()) {
      existing.apiKey = body.apiKey.trim();
      existing.hasApiKey = true;
    }
    existing.publisherInn = body.publisherInn ?? existing.publisherInn;
    existing.publisherName = body.publisherName ?? existing.publisherName;
    existing.orgType = body.orgType ?? existing.orgType;
    existing.phone = body.phone ?? existing.phone;
    existing.roles = body.roles ?? existing.roles;
    existing.isDefault = body.isDefault ?? existing.isDefault;
    existing.status = existing.hasApiKey ? 'active' : 'pending';
    existing.updatedAt = now;
    return existing;
  }

  const created: StoredOrdConnection = {
    id: nextId('ordn'),
    workspaceId,
    provider,
    status: body.apiKey?.trim() ? 'active' : 'pending',
    hasApiKey: Boolean(body.apiKey?.trim()),
    isDefault: body.isDefault ?? false,
    publisherInn: body.publisherInn,
    publisherName: body.publisherName,
    orgType: body.orgType,
    phone: body.phone,
    roles: body.roles ?? ['publisher'],
    createdAt: now,
    updatedAt: now,
    apiKey: body.apiKey?.trim() || undefined,
  };
  state.connections.push(created);
  return created;
}

export function testOrdConnection(
  workspaceId: string,
  provider: OrdProvider,
): { ok: boolean; status: string; message: string } {
  const conn = getOrdConnection(workspaceId, provider);
  if (!conn?.hasApiKey) {
    return { ok: false, status: 'missing_key', message: 'API key is not configured' };
  }
  return { ok: true, status: 'active', message: 'Connection OK' };
}

export function toPublicConnection(conn: StoredOrdConnection): OrdConnection {
  const { apiKey: _secret, ...rest } = conn;
  return { ...rest, hasApiKey: Boolean(conn.hasApiKey) };
}

export function listOrdContractors(workspaceId: string): OrdContractor[] {
  return state.contractors.filter((item) => item.workspaceId === workspaceId);
}

export function getOrdContractor(
  workspaceId: string,
  contractorId: string,
): OrdContractor | undefined {
  return state.contractors.find(
    (item) => item.workspaceId === workspaceId && item.id === contractorId,
  );
}

export type OrdContractorError = { code: string; detail: string };

export function createOrdContractor(
  workspaceId: string,
  body: CreateOrdContractorRequest,
): OrdContractor | OrdContractorError {
  const inn = normalizeInn(body.inn);
  if (!isValidInn(inn)) {
    return { code: 'ad_inn_invalid', detail: 'INN checksum failed' };
  }
  if (!isValidKktuCountry(body.countryOksm)) {
    // country optional on create; validate when provided
  }
  const now = nowIso();
  const created: OrdContractor = {
    id: nextId('ordc'),
    workspaceId,
    inn,
    kpp: body.kpp ?? '',
    name: body.name,
    orgType: body.orgType,
    countryOksm: body.countryOksm ?? '643',
    phone: body.phone,
    address: body.address,
    createdAt: now,
    updatedAt: now,
  };
  state.contractors.push(created);
  return created;
}

export function updateOrdContractor(
  workspaceId: string,
  contractorId: string,
  body: UpdateOrdContractorRequest,
): OrdContractor | OrdContractorError | null {
  const existing = getOrdContractor(workspaceId, contractorId);
  if (!existing) return null;
  if (body.inn != null) {
    const inn = normalizeInn(body.inn);
    if (!isValidInn(inn)) {
      return { code: 'ad_inn_invalid', detail: 'INN checksum failed' };
    }
    existing.inn = inn;
  }
  if (body.kpp != null) existing.kpp = body.kpp;
  if (body.name != null) existing.name = body.name;
  if (body.orgType != null) existing.orgType = body.orgType;
  if (body.countryOksm != null) existing.countryOksm = body.countryOksm;
  if (body.phone != null) existing.phone = body.phone;
  if (body.address != null) existing.address = body.address;
  existing.updatedAt = nowIso();
  return existing;
}

export function listOrdContracts(workspaceId: string): OrdContract[] {
  return state.contracts.filter((item) => item.workspaceId === workspaceId);
}

export function createOrdContract(
  workspaceId: string,
  body: CreateOrdContractRequest,
): OrdContract | null {
  if (!getOrdContractor(workspaceId, body.contractorId)) {
    return null;
  }
  const now = nowIso();
  const created: OrdContract = {
    id: nextId('ordt'),
    workspaceId,
    contractorId: body.contractorId,
    provider: body.provider,
    externalId: body.externalId,
    serial: body.serial,
    dateStart: body.dateStart,
    dateEnd: body.dateEnd,
    amount: body.amount,
    vatRate: body.vatRate,
    selfPromo: body.selfPromo ?? false,
    status: 'active',
    createdAt: now,
    updatedAt: now,
  };
  state.contracts.push(created);
  return created;
}

export function isValidKktuCode(code: string): boolean {
  return KKTU_DICTIONARY.some((item) => item.code === code);
}

export function isValidKktuCountry(code?: string | null): boolean {
  if (!code) return true;
  return OKSM_DICTIONARY.some((item) => item.code === code);
}

export function validateAdMarking(ad: AdMarking | undefined | null): OrdContractorError | null {
  if (!ad) {
    return { code: 'ord_validation_failed', detail: 'Ad marking payload required' };
  }
  if (!ad.advertiserName?.trim()) {
    return { code: 'ord_validation_failed', detail: 'advertiserName is required' };
  }
  if (!isValidInn(ad.inn ?? '')) {
    return { code: 'ad_inn_invalid', detail: 'INN checksum failed' };
  }
  const codes = ad.kktuCodes ?? [];
  if (codes.length < 1 || codes.some((code) => !isValidKktuCode(code))) {
    return { code: 'ord_validation_failed', detail: 'At least one valid KKTU code is required' };
  }
  return null;
}

export function getCreativeForPost(postId: string): StoredOrdCreative | undefined {
  return state.creatives.find((item) => item.postId === postId);
}

export function registerCreative(input: {
  workspaceId: string;
  postId: string;
  provider: OrdProvider;
  ad: AdMarking;
}): OrdRegisterCreativeResponse | OrdContractorError {
  if (!hasActiveOrdConnection(input.workspaceId)) {
    return { code: 'ord_not_configured', detail: 'ORD connection is not configured' };
  }
  const validation = validateAdMarking(input.ad);
  if (validation) return validation;

  const existing = getCreativeForPost(input.postId);
  if (existing?.erid && existing.status === 'registered') {
    return { ...existing };
  }

  const now = nowIso();
  const erid = existing?.erid ?? `test-erid-msw-${input.postId.slice(-8)}`;
  const creative: StoredOrdCreative = {
    id: existing?.id ?? nextId('ordr'),
    workspaceId: input.workspaceId,
    postId: input.postId,
    provider: input.provider,
    externalId: `creative:${input.postId}:1`,
    erid,
    status: 'registered',
    registeredAt: now,
    kktuCodes: input.ad.kktuCodes,
  };
  if (existing) {
    Object.assign(existing, creative);
  } else {
    state.creatives.push(creative);
  }
  return { ...creative };
}

export const DEMO_CONTRACTOR_ID = CONTRACTOR_IDS.posts;
