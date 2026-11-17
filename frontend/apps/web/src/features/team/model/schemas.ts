import { z } from 'zod';

export const inviteRoles = ['admin', 'editor', 'author', 'viewer'] as const;

export const inviteMemberSchema = z.object({
  email: z.string().trim().min(1, 'emailRequired').email('emailInvalid'),
  role: z.enum(inviteRoles, { message: 'roleRequired' }),
  projectIds: z.array(z.string()).optional(),
});

export type InviteMemberValues = z.infer<typeof inviteMemberSchema>;

export const acceptInvitationSchema = z.object({
  token: z.string().trim().min(1, 'tokenRequired'),
});

export type AcceptInvitationValues = z.infer<typeof acceptInvitationSchema>;
