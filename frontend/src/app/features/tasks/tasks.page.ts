import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/services/api-error';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { formatDateTime } from '../orders/orders.api';
import { SellerTask, TaskList, TaskView, TasksApi } from './tasks.api';

interface Target {
  link: unknown[];
  queryParams?: Record<string, string>;
  label: string;
}

/**
 * Pendientes del negocio. Ninguna tarea se marca como hecha aquí: se resuelve sola cuando la acción se
 * completa en su pantalla (verificar el pago, activar el servicio, liberar el cupo, enviar el aviso).
 */
@Component({
  selector: 'app-tasks-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, StatusBadgeComponent, SolesPipe],
  template: `
    <section class="mx-auto max-w-5xl" aria-labelledby="pendientes-titulo">
      <p class="type-eyebrow">Tu negocio, bajo control</p>
      <h1 id="pendientes-titulo" class="type-h1 mt-3">Pendientes</h1>
      <a routerLink="/panel/pendientes/historial" class="link mt-4 inline-block"
        >Ver historial de pendientes</a
      >
      <p class="mt-3 max-w-3xl">
        Lo que tus clientes esperan de ti. Cada pendiente desaparece solo cuando completas la acción
        en su pantalla{{ auth.isOwner() ? '; asígnalo a quien de tu equipo lo atenderá' : '' }}.
      </p>

      @if (error(); as e) {
        <p class="notice notice-error mt-6" role="alert">{{ e }}</p>
      }

      @if (list(); as l) {
        <div
          class="mt-8 flex gap-6 overflow-x-auto border-b border-[var(--line-subtle)]"
          role="tablist"
          aria-label="Filtrar pendientes"
        >
          @for (t of tabs; track t.view) {
            <button
              type="button"
              role="tab"
              [attr.aria-selected]="view() === t.view"
              class="-mb-px whitespace-nowrap border-b-2 pb-3 text-[0.9375rem] font-semibold"
              [class]="
                view() === t.view
                  ? 'border-night text-night'
                  : 'border-transparent text-[var(--text-muted)]'
              "
              (click)="load(t.view)"
            >
              {{ t.label }} ({{ l.counts[t.count] }})
            </button>
          }
        </div>
        @if (l.counts.overdue > 0) {
          <p class="notice notice-warning mt-6" role="status">
            {{ l.counts.overdue }}
            {{ l.counts.overdue === 1 ? 'pendiente superó' : 'pendientes superaron' }} su plazo
            sugerido.
          </p>
        }

        @if (l.items.length === 0) {
          <p class="py-14 text-center text-[var(--text-muted)]">
            {{
              view() === 'ALL'
                ? 'No hay pendientes. Todo está al día.'
                : 'No hay pendientes en esta vista.'
            }}
          </p>
        } @else {
          <ul class="mt-4 divide-y divide-[var(--line-subtle)]">
            @for (t of l.items; track t.id) {
              <li class="grid gap-3 py-5" [attr.data-testid]="'task-' + t.type">
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div class="min-w-0">
                    <p class="font-semibold text-night">{{ t.typeLabel }}</p>
                    <p class="mt-1 text-sm">
                      {{ t.customerName ?? 'Cliente' }}
                      @if (t.orderNumber) {
                        · Pedido {{ t.orderNumber }}
                      }
                      @if (t.productName) {
                        · {{ t.productName }}
                      }
                      @if (t.amount !== null) {
                        · {{ t.amount | soles }}
                      }
                    </p>
                    @if (t.detail) {
                      <p class="mt-1 text-sm text-[var(--text-muted)]">{{ t.detail }}</p>
                    }
                  </div>
                  @if (t.overdue) {
                    <app-status-badge [label]="'Plazo vencido · ' + date(t.dueAt)" tone="danger" />
                  } @else {
                    <app-status-badge [label]="'Plazo: ' + date(t.dueAt)" tone="neutral" />
                  }
                </div>
                <p class="text-xs text-[var(--text-muted)]">Desde {{ date(t.since) }}</p>
                <div class="flex flex-wrap items-center gap-3">
                  @if (target(t); as go) {
                    <a
                      class="btn btn-primary !min-h-10 !text-xs"
                      [routerLink]="go.link"
                      [queryParams]="go.queryParams ?? null"
                      >{{ go.label }}</a
                    >
                  }
                  @if (auth.isOwner()) {
                    <label class="flex items-center gap-2 text-sm">
                      <span>Responsable</span>
                      <select
                        class="input !min-h-10 !w-auto !py-1 text-sm"
                        [value]="t.assignee?.userId ?? ''"
                        [disabled]="busy()"
                        (change)="assign(t, $any($event.target).value || null)"
                      >
                        <option value="">Sin asignar</option>
                        @for (m of eligible(t); track m.userId) {
                          <option [value]="m.userId">
                            {{ m.fullName }}{{ m.owner ? ' (titular)' : '' }}
                          </option>
                        }
                      </select>
                    </label>
                  } @else if (!t.assignee) {
                    <button
                      type="button"
                      class="btn btn-secondary !min-h-10 !text-xs"
                      [disabled]="busy()"
                      (click)="assign(t, me())"
                    >
                      Tomar
                    </button>
                  } @else if (t.assignee.userId === me()) {
                    <span class="text-sm font-semibold text-night">A tu cargo</span>
                    <button
                      type="button"
                      class="btn !min-h-10 !text-xs"
                      [disabled]="busy()"
                      (click)="assign(t, null)"
                    >
                      Soltar
                    </button>
                  } @else {
                    <span class="text-sm">A cargo de {{ t.assignee.fullName }}</span>
                  }
                </div>
              </li>
            }
          </ul>
        }
      } @else if (!error()) {
        <div class="mt-8 h-56 animate-pulse rounded bg-mist/25" aria-busy="true"></div>
      }
    </section>
  `,
})
export class TasksPage {
  private readonly api = inject(TasksApi);
  protected readonly auth = inject(AuthService);
  protected readonly list = signal<TaskList | null>(null);
  protected readonly view = signal<TaskView>('ALL');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly me = computed(() => this.auth.user()?.id ?? '');

  protected readonly tabs = [
    { view: 'ALL', label: 'Todos', count: 'open' },
    { view: 'MINE', label: 'A mi cargo', count: 'mine' },
    { view: 'UNASSIGNED', label: 'Sin asignar', count: 'unassigned' },
  ] as const;

  constructor() {
    this.load('ALL');
  }

  protected load(view: TaskView): void {
    this.view.set(view);
    this.api.list(view).subscribe({
      next: (l) => {
        this.list.set(l);
        this.error.set(null);
      },
      error: (e: unknown) => this.error.set(ApiError.from(e).message),
    });
  }

  protected assign(t: SellerTask, userId: string | null): void {
    this.busy.set(true);
    this.api.assign(t.id, userId).subscribe({
      next: () => {
        this.busy.set(false);
        this.load(this.view());
      },
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.busy.set(false);
        this.load(this.view());
      },
    });
  }

  protected eligible(t: SellerTask) {
    return this.list()?.members.filter((m) => m.types.includes(t.type)) ?? [];
  }

  protected date(iso: string): string {
    return formatDateTime(iso);
  }

  /** Pantalla donde se resuelve, según las secciones a las que tiene acceso el usuario. */
  protected target(t: SellerTask): Target | null {
    switch (t.type) {
      case 'VERIFY_PAYMENT':
      case 'REVIEW_INCIDENT':
        return t.orderId ? { link: ['/panel/ventas', t.orderId], label: 'Revisar pago' } : null;
      case 'ACTIVATE_SERVICE':
        return t.customerId
          ? { link: ['/panel/clientes', t.customerId], label: 'Activar servicio' }
          : null;
      case 'RELEASE_ACCESS':
        if (this.auth.can('CUSTOMERS') && t.customerId)
          return { link: ['/panel/clientes', t.customerId], label: 'Liberar cupo' };
        return {
          link: ['/panel/renovaciones'],
          queryParams: { vista: 'RELEASE' },
          label: 'Liberar cupo',
        };
      case 'SEND_RENEWAL_NOTICE':
        return { link: ['/panel/renovaciones'], label: 'Enviar aviso' };
    }
  }
}
