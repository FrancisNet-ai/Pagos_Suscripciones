import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { BusinessRole, SessionResponse, StaffPermission } from '../../core/auth/auth.models';

export interface PermissionOption {
  value: StaffPermission;
  label: string;
}

export interface TeamMember {
  id: string;
  userId: string;
  fullName: string;
  email: string;
  role: BusinessRole;
  status: 'ACTIVE' | 'REVOKED';
  permissions: StaffPermission[];
  since: string;
  revokedAt: string | null;
}

export interface PendingInvitation {
  id: string;
  fullName: string;
  email: string;
  permissions: StaffPermission[];
  expiresAt: string;
  expired: boolean;
  createdAt: string;
}

export interface Team {
  members: TeamMember[];
  invitations: PendingInvitation[];
  seatsUsed: number;
  /** `null`: sin plan vigente (no se puede invitar hasta activarlo). */
  seatLimit: number | null;
  permissionOptions: PermissionOption[];
}

/** El enlace solo llega en esta respuesta: el servidor guarda únicamente su huella. */
export interface InvitationCreated {
  invitation: PendingInvitation;
  link: string;
  emailSent: boolean;
  team: Team;
}

export interface PublicInvitation {
  businessName: string;
  fullName: string;
  email: string;
  permissions: PermissionOption[];
  expiresAt: string;
  /** El correo ya tiene cuenta: se une iniciando sesión, sin registrarse de nuevo. */
  existingAccount?: boolean;
}

export interface AcceptInvitationPayload {
  fullName: string;
  phone: string;
  password: string;
  acceptTerms: boolean;
  acceptPrivacy: boolean;
}

const BASE = '/api/v1/business/me/staff';

@Injectable({ providedIn: 'root' })
export class StaffApi {
  private readonly http = inject(HttpClient);

  team(): Observable<Team> {
    return this.http.get<Team>(BASE);
  }

  invite(body: { fullName: string; email: string; permissions: StaffPermission[] }): Observable<InvitationCreated> {
    return this.http.post<InvitationCreated>(`${BASE}/invitations`, body);
  }

  renew(id: string): Observable<InvitationCreated> {
    return this.http.post<InvitationCreated>(`${BASE}/invitations/${id}/renew`, {});
  }

  cancel(id: string): Observable<Team> {
    return this.http.delete<Team>(`${BASE}/invitations/${id}`);
  }

  updatePermissions(memberId: string, permissions: StaffPermission[]): Observable<Team> {
    return this.http.put<Team>(`${BASE}/${memberId}/permissions`, { permissions });
  }

  revoke(memberId: string): Observable<Team> {
    return this.http.post<Team>(`${BASE}/${memberId}/revoke`, {});
  }

  reactivate(memberId: string): Observable<Team> {
    return this.http.post<Team>(`${BASE}/${memberId}/reactivate`, {});
  }

  view(token: string): Observable<PublicInvitation> {
    return this.http.get<PublicInvitation>(`/api/v1/public/invitations/${encodeURIComponent(token)}`);
  }

  join(token: string): Observable<SessionResponse> {
    return this.http.post<SessionResponse>(`/api/v1/public/invitations/${encodeURIComponent(token)}/join`, {});
  }

  accept(token: string, body: AcceptInvitationPayload): Observable<SessionResponse> {
    return this.http.post<SessionResponse>(`/api/v1/public/invitations/${encodeURIComponent(token)}/accept`, body);
  }
}

export const PERMISSION_LABEL: Record<StaffPermission, string> = {
  PRODUCTS: 'Catálogo de productos',
  ORDERS: 'Pedidos y atención de compradores',
  PAYMENTS: 'Aprobación y rechazo de pagos',
  CUSTOMERS: 'Clientes y suscripciones',
  RENEWALS: 'Renovaciones y avisos',
  REPORTS: 'Ingresos y reportes',
};
