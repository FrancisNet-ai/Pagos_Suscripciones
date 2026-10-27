import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type RowAction = 'CREATE' | 'EXISTING' | 'ERROR';
export type RowOutcome = 'CREATED' | 'SUBSCRIPTION_ADDED' | 'UNCHANGED' | 'FAILED' | 'SKIPPED';

export interface ImportRow {
  rowNumber: number;
  fullName: string | null;
  phone: string | null;
  notes: string | null;
  productId: string | null;
  productName: string | null;
  startDate: string | null;
  durationDays: number | null;
  price: number | null;
  status: 'ACTIVE' | 'EXPIRED' | 'CANCELLED' | null;
  action: RowAction;
  errors: string[];
  outcome: RowOutcome | null;
  outcomeMessage: string | null;
}

export interface CustomerImport {
  id: string;
  fileName: string;
  status: 'PREVIEW' | 'COMMITTED' | 'DISCARDED';
  totalRows: number;
  summary: { toCreate: number; existing: number; withSubscription: number; errors: number };
  result: { created: number; subscriptionsAdded: number; unchanged: number; failed: number; skipped: number } | null;
  expiresAt: string;
  rows: ImportRow[];
}

@Injectable({ providedIn: 'root' })
export class ImportsApi {
  private readonly http = inject(HttpClient);

  preview(file: File): Observable<CustomerImport> {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<CustomerImport>('/api/v1/customers/imports', body);
  }

  commit(id: string): Observable<CustomerImport> {
    return this.http.post<CustomerImport>(`/api/v1/customers/imports/${id}/commit`, {});
  }

  discard(id: string): Observable<void> {
    return this.http.post<void>(`/api/v1/customers/imports/${id}/discard`, {});
  }
}

/** Plantilla descargable: separador «;» para que Excel en español la abra en columnas. */
export const TEMPLATE_CSV =
  '﻿nombre;celular;producto;inicio;duracion_dias;precio;estado;notas\r\n' +
  'Ana Torres;987654321;Servicio Premium;01/10/2026;30;15.00;activa;perfil 2\r\n' +
  'Luis Díaz;912345678;;;;;;cliente sin suscripción\r\n';
