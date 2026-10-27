import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { Page } from '../../core/services/page';
import { formatDateTime } from '../orders/orders.api';
import { ResolvedTask, TasksApi } from './tasks.api';

type State =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; page: Page<ResolvedTask> };

@Component({
  selector: 'app-task-history-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="mx-auto max-w-5xl" aria-labelledby="historial-titulo">
      <a routerLink="/panel/pendientes" class="link text-sm">Volver a Pendientes</a>
      <p class="type-eyebrow mt-6">Tu negocio, bajo control</p>
      <h1 id="historial-titulo" class="type-h1 mt-3">Historial de pendientes</h1>
      <p class="mt-3 max-w-3xl">
        Consulta los casos cerrados y quién realizó la acción. El responsable asignado puede ser
        distinto de la persona que lo resolvió.
      </p>
      @let s = state();
      @if (s.status === 'loading') {
        <p class="mt-8 py-8" role="status">Cargando historial…</p>
      } @else if (s.status === 'error') {
        <p class="notice notice-error mt-6" role="alert">{{ s.message }}</p>
        <button type="button" class="btn btn-secondary mt-4" (click)="reload()">
          Volver a intentar
        </button>
      } @else {
        <p class="mt-8 text-sm text-[var(--text-muted)]" role="status">
          {{ s.page.totalItems }} {{ s.page.totalItems === 1 ? 'caso cerrado' : 'casos cerrados' }}
        </p>
        @if (s.page.items.length === 0) {
          <p class="py-14 text-center text-[var(--text-muted)]">
            Todavía no hay pendientes cerrados en esta vista.
          </p>
        } @else {
          <ul class="mt-4 divide-y divide-[var(--line-subtle)]">
            @for (entry of s.page.items; track entry.task.id) {
              <li class="grid gap-2 py-5" [attr.data-testid]="'resolved-' + entry.task.type">
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <p class="font-semibold text-night">{{ entry.task.typeLabel }}</p>
                  <p class="text-sm font-semibold text-night">{{ entry.resolutionLabel }}</p>
                </div>
                <p class="text-sm">
                  {{ entry.task.customerName ?? 'Cliente' }}
                  @if (entry.task.orderNumber) {
                    · Pedido {{ entry.task.orderNumber }}
                  }
                  @if (entry.task.productName) {
                    · {{ entry.task.productName }}
                  }
                </p>
                <p class="text-sm">
                  @if (entry.resolvedBy; as person) {
                    Acción realizada por <strong class="text-night">{{ person.fullName }}</strong
                    >.
                  } @else {
                    Sin autor registrado. Puede corresponder a un cambio automático o a un registro
                    anterior.
                  }
                </p>
                <p class="text-xs text-[var(--text-muted)]">
                  Cerrado el {{ date(entry.resolvedAt) }}
                </p>
                @if (entry.task.assignee; as assignee) {
                  <p class="text-xs text-[var(--text-muted)]">
                    Responsable asignado: {{ assignee.fullName }}
                  </p>
                }
              </li>
            }
          </ul>
        }
        @if (s.page.totalPages > 1) {
          <nav
            aria-label="Paginación del historial"
            class="mt-6 flex flex-wrap items-center justify-between gap-3"
          >
            <button
              type="button"
              class="btn btn-secondary"
              [disabled]="s.page.page === 0"
              (click)="go(s.page.page - 1)"
            >
              Anterior
            </button>
            <p class="text-sm">Página {{ s.page.page + 1 }} de {{ s.page.totalPages }}</p>
            <button
              type="button"
              class="btn btn-secondary"
              [disabled]="s.page.page + 1 >= s.page.totalPages"
              (click)="go(s.page.page + 1)"
            >
              Siguiente
            </button>
          </nav>
        }
      }
    </section>
  `,
})
export class TaskHistoryPage {
  private readonly api = inject(TasksApi);
  private readonly request = signal({ page: 0, revision: 0 });
  protected readonly date = formatDateTime;
  protected readonly state = toSignal(
    toObservable(this.request).pipe(
      switchMap(({ page }) =>
        this.api.history(page).pipe(
          map((result): State => ({ status: 'ready', page: result })),
          catchError((error: unknown) =>
            of<State>({ status: 'error', message: ApiError.from(error).message }),
          ),
          startWith<State>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as State },
  );

  protected go(page: number): void {
    this.request.update((request) => ({ ...request, page }));
  }

  protected reload(): void {
    this.request.update((request) => ({ ...request, revision: request.revision + 1 }));
  }
}
