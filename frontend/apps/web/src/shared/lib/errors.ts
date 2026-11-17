import type { ProblemDetail } from '@posts/api-client';

export class AppError extends Error {
  readonly code: string;
  readonly status?: number;

  constructor(message: string, options?: { code?: string; status?: number; cause?: unknown }) {
    super(message, options?.cause ? { cause: options.cause } : undefined);
    this.name = 'AppError';
    this.code = options?.code ?? 'UNKNOWN';
    this.status = options?.status;
  }
}

/** RFC 7807 ProblemDetail from Posts API (`application/problem+json`). */
export class ApiProblemError extends AppError {
  readonly problem: ProblemDetail;
  readonly errors?: ProblemDetail['errors'];

  constructor(problem: ProblemDetail, options?: { cause?: unknown }) {
    const status = typeof problem.status === 'number' ? problem.status : undefined;
    const code = problem.code ?? 'unknown';
    const message = problem.detail || problem.title || 'Request failed';
    super(message, { code, status, cause: options?.cause });
    this.name = 'ApiProblemError';
    this.problem = problem;
    this.errors = problem.errors;
  }
}

/** Provisional code used by MSW until BE-5 formalizes 2FA challenge in OpenAPI. */
export const TWO_FACTOR_REQUIRED_CODE = 'two_factor_required';

/** OpenAPI / BE-6 ProblemCodes: role cannot perform the action. */
export const INSUFFICIENT_PERMISSIONS_CODE = 'insufficient_permissions';

/** OpenAPI ProblemCodes: channel/seat/post quota of the current plan. */
export const PLAN_LIMIT_EXCEEDED_CODE = 'plan_limit_exceeded';

export function isApiProblemError(error: unknown): error is ApiProblemError {
  return error instanceof ApiProblemError;
}

export function isTwoFactorRequired(error: unknown): boolean {
  return isApiProblemError(error) && error.code === TWO_FACTOR_REQUIRED_CODE;
}

export function isInsufficientPermissions(error: unknown): boolean {
  return isApiProblemError(error) && error.code === INSUFFICIENT_PERMISSIONS_CODE;
}

export function isPlanLimitExceeded(error: unknown): boolean {
  return isApiProblemError(error) && error.code === PLAN_LIMIT_EXCEEDED_CODE;
}

export function getErrorMessage(error: unknown, fallback = 'Unexpected error'): string {
  if (error instanceof AppError || error instanceof Error) {
    return error.message;
  }
  if (typeof error === 'string') {
    return error;
  }
  return fallback;
}

export function toApiProblemError(error: unknown, status?: number): ApiProblemError {
  if (error instanceof ApiProblemError) {
    return error;
  }

  if (error && typeof error === 'object') {
    const candidate = error as ProblemDetail;
    if (
      typeof candidate.title === 'string' ||
      typeof candidate.detail === 'string' ||
      typeof candidate.code === 'string'
    ) {
      return new ApiProblemError({
        ...candidate,
        status: candidate.status ?? status,
      });
    }
  }

  return new ApiProblemError({
    title: 'Request failed',
    detail: getErrorMessage(error, 'Request failed'),
    status,
    code: 'unknown',
  });
}
