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

export function getErrorMessage(error: unknown, fallback = 'Unexpected error'): string {
  if (error instanceof AppError || error instanceof Error) {
    return error.message;
  }
  if (typeof error === 'string') {
    return error;
  }
  return fallback;
}
