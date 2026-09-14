import type { UUID } from '../../../shared/api/contracts';

export interface MeDto {
  id: UUID;
  email: string;
  displayName: string | null;
  locale: string | null;
  timezone: string | null;
  membershipId: UUID;
  membershipStatus: string;
  roles: string[];
  permissions: string[];
}

export interface SessionDto {
  id: UUID;
  createdAt: string;
  expiresAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
  userAgent: string | null;
  sourceIp: string | null;
}
