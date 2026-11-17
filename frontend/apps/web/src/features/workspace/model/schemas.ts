import { z } from 'zod';

export const createWorkspaceSchema = z.object({
  name: z.string().trim().min(1, 'nameRequired').max(120, 'nameMax'),
});

export type CreateWorkspaceValues = z.infer<typeof createWorkspaceSchema>;

export const updateWorkspaceSchema = createWorkspaceSchema;

export type UpdateWorkspaceValues = z.infer<typeof updateWorkspaceSchema>;

export const createProjectSchema = z.object({
  name: z.string().trim().min(1, 'nameRequired').max(120, 'nameMax'),
  color: z
    .string()
    .trim()
    .refine((value) => value === '' || /^#[0-9A-Fa-f]{6}$/.test(value), {
      message: 'colorInvalid',
    })
    .optional(),
});

export type CreateProjectValues = z.infer<typeof createProjectSchema>;
