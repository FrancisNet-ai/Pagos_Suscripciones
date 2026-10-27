import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiError } from '../../core/services/api-error';
import { Page } from '../../core/services/page';
import { ACTION_LABEL, AdminApi, AdminAuditEntry, actionLabel } from './admin.api';

@Component({
  selector: 'app-admin-audit-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <p class="type-eyebrow">Trazabilidad</p>
    <h1 class="type-h1 mt-3">Auditoría</h1>
    <p class="mt-3 max-w-2xl">
      El detalle solo se muestra en acciones de la plataforma. En las operaciones de cada vendedor se ve qué ocurrió y cuándo, sin datos de sus compradores.
    </p>

    <div class="field mt-8 max-w-sm">
      <label class="field-label" for="action">Acción</label>
      <select id="action" class="input" [formControl]="action" (change)="load(0)">
        <option value="">Todas</option>
        @for (a of actions; track a[0]) { <option [value]="a[0]">{{ a[1] }}</option> }
      </select>
    </div>

    @if (error(); as e) { <p class="notice notice-error mt-6" role="alert">{{ e }}</p> }

    @if (page(); as p) {
      <ul class="mt-6 divide-y divide-[var(--line-subtle)] border-y border-[var(--line-subtle)]">
        @for (a of p.items; track a.id) {
          <li class="grid gap-1 py-4 md:grid-cols-[1.2fr_1fr_auto] md:gap-6">
            <div class="min-w-0">
              <p class="font-semibold text-night">{{ label(a.action) }}</p>
              @if (a.metadata && a.metadata !== '{}') { <p class="mt-1 break-all font-mono text-xs text-[var(--text-muted)]">{{ a.metadata }}</p> }
            </div>
            <div class="min-w-0 text-sm">
              @if (a.businessId) { <a [routerLink]="['/admin/negocios', a.businessId]" class="link">{{ a.businessName }}</a> }
              @if (a.actorEmail) { <p class="truncate text-[var(--text-muted)]">{{ a.actorEmail }}</p> }
            </div>
            <div class="text-sm text-[var(--text-muted)] md:text-right">
              <p>{{ dateTime(a.createdAt) }}</p>
              @if (a.ipAddress) { <p class="type-numeric text-xs">{{ a.ipAddress }}</p> }
            </div>
          </li>
        } @empty {
          <li class="py-10 text-center">Sin registros.</li>
        }
      </ul>
      @if (p.totalPages > 1) {
        <div class="mt-6 flex items-center justify-between text-sm">
          <button type="button" class="btn btn-secondary !min-h-10" [disabled]="p.page === 0" (click)="load(p.page - 1)">Anterior</button>
          <span>Página {{ p.page + 1 }} de {{ p.totalPages }}</span>
          <button type="button" class="btn btn-secondary !min-h-10" [disabled]="p.page + 1 >= p.totalPages" (click)="load(p.page + 1)">Siguiente</button>
        </div>
      }
    } @else if (!error()) {
      <div class="mt-6 h-40 animate-pulse rounded bg-mist/25" aria-busy="true"></div>
    }
  `,
})
export class AdminAuditPage {
  private readonly api = inject(AdminApi);
  protected readonly action = new FormControl('', { nonNullable: true });
  protected readonly actions = Object.entries(ACTION_LABEL);
  protected readonly page = signal<Page<AdminAuditEntry> | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly label = actionLabel;

  constructor() {
    this.load(0);
  }

  protected load(page: number): void {
    this.error.set(null);
    this.api.auditLogs(this.action.value, page).subscribe({
      next: (p) => this.page.set(p),
      error: (e: unknown) => this.error.set(ApiError.from(e).message),
    });
  }

  protected dateTime(iso: string): string {
    return new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso));
  }
}
