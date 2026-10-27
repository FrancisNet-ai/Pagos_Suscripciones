export type Role = 'SUPER_ADMIN' | 'BUSINESS_OWNER' | 'BUSINESS_STAFF';

export interface SessionUser {
  id: string;
  fullName: string;
  email: string;
  phone: string;
  roles: Role[];
}

export type BusinessRole = 'OWNER' | 'STAFF';
/** Secciones que el titular habilita a cada colaborador. */
export type StaffPermission = 'PRODUCTS' | 'ORDERS' | 'CUSTOMERS' | 'RENEWALS' | 'REPORTS';

export interface BusinessSummary {
  id: string;
  uniqueCode: string;
  slug: string;
  businessName: string;
  status: 'ACTIVE' | 'SUSPENDED';
  access: AccessSummary;
  role: BusinessRole;
  /** El titular recibe todas; un colaborador, solo las que le habilitaron. */
  permissions: StaffPermission[];
}

export type AccessBlock = 'SUSPENDED' | 'NO_PLAN' | 'PENDING_PAYMENT' | 'EXPIRED' | 'CANCELLED';
export type SaasSubscriptionStatus = 'PENDING_PAYMENT' | 'ACTIVE' | 'PAST_DUE' | 'CANCELLED' | 'EXPIRED';
export type PlanFeature = 'REPORTS' | 'REMINDERS' | 'BRANDING';

/** Si el plan SaaS permite usar las funciones comerciales (lo decide el backend). */
export interface AccessSummary {
  active: boolean;
  block: AccessBlock | null;
  message: string | null;
  subscriptionStatus: SaasSubscriptionStatus | null;
  planName: string | null;
  expiresAt: string | null;
  usableUntil: string | null;
  features: PlanFeature[];
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
  planId?: string | null;
}

export interface LoginPayload {
  email: string;
  password: string;
}
