import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, of, tap } from 'rxjs';

export type TaskType =
  | 'VERIFY_PAYMENT'
  | 'REVIEW_INCIDENT'
  | 'ACTIVATE_SERVICE'
  | 'RELEASE_ACCESS'
  | 'SEND_RENEWAL_NOTICE';
export type TaskView = 'ALL' | 'MINE' | 'UNASSIGNED';

export interface SellerTask {
  id: string;
  type: TaskType;
  typeLabel: string;
  since: string;
  dueAt: string;
  overdue: boolean;
  assignee: { userId: string; fullName: string } | null;
  customerId: string | null;
  customerName: string | null;
  productName: string | null;
  orderId: string | null;
  orderNumber: string | null;
  amount: number | null;
  subscriptionId: string | null;
  detail: string | null;
}

export interface TaskCounts {
  open: number;
  mine: number;
  unassigned: number;
  overdue: number;
}

export interface AssignableMember {
  userId: string;
  fullName: string;
  owner: boolean;
  types: TaskType[];
}

export interface TaskList {
  items: SellerTask[];
  counts: TaskCounts;
  members: AssignableMember[];
}

@Injectable({ providedIn: 'root' })
export class TasksApi {
  private readonly http = inject(HttpClient);
  /** Totales para el menú; se refrescan al navegar y tras cada cambio en Pendientes. */
  readonly counts = signal<TaskCounts | null>(null);

  list(view: TaskView): Observable<TaskList> {
    return this.http.get<TaskList>('/api/v1/tasks', { params: { view } }).pipe(tap((l) => this.counts.set(l.counts)));
  }

  refreshCounts(): void {
    this.http
      .get<TaskCounts>('/api/v1/tasks/summary')
      .pipe(catchError(() => of(null)))
      .subscribe((c) => this.counts.set(c));
  }

  assign(id: string, userId: string | null): Observable<TaskList> {
    return this.http
      .put<TaskList>(`/api/v1/tasks/${id}/assignee`, { userId })
      .pipe(tap((l) => this.counts.set(l.counts)));
  }
}
