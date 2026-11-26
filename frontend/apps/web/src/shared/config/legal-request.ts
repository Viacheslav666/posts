import { connection } from 'next/server';

import { getLegalRequisites, type LegalRequisites } from './legal';

/**
 * Read requisites for the current request.
 * `connection()` opts the route out of static prerender so env changes
 * after `next build` show up without a rebuild.
 */
export async function readRequestLegalRequisites(): Promise<LegalRequisites> {
  await connection();
  return getLegalRequisites();
}
