export type Role = 'SUPER_ADMIN' | 'BUSINESS_OWNER' | 'BUSINESS_STAFF';

export interface SessionUser {
  id: string;
  fullName: string;
  email: string;
  phone: string;
  roles: Role[];
}

export interface BusinessSummary {
  id: string;
  uniqueCode: string;
  slug: string;
  businessName: string;
  status: 'ACTIVE' | 'SUSPENDED';
}

export interface SessionResponse {
  user: SessionUser;
  business: BusinessSummary | null;
}

export interface RegisterPayload {
  fullName: string;
  email: string;
  password: string;
  phone: string;
  businessName: string;
  acceptTerms: boolean;
  acceptPrivacy: boolean;
}

export interface LoginPayload {
  email: string;
  password: string;
}
