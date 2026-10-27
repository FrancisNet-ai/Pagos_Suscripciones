import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { Page } from '../../core/services/page';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { DurationPipe, SolesPipe } from '../../shared/pipes/soles.pipe';
import { RenewalHistoryItem, Subscription, SubscriptionsApi, remainingText, shortDate, subscriptionTone } from './subscriptions.api';

type Mode = 'none' | 'activate' | 'cancel';

/** Una suscripción con sus acciones. Toda transición la valida el backend. */
@Component({
  selector: 'app-subscription-item',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, StatusBadgeComponent, SolesPipe, DurationPipe],
  template: `
    @let s = sub();
    <article class="grid gap-3 py-5">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div class="min-w-0">
          @if (showCustomer()) {
            <a [routerLink]="['/panel/clientes', s.customerId]" class="font-semibold text-night no-underline hover:underline">{{ s.customerName }}</a>
            <p class="text-sm text-[var(--text-muted)]">{{ s.productName }} · {{ s.durationDays | duration }}</p>
          } @else {
            <p class="font-semibold text-night">{{ s.productName }}</p>
            <p class="text-sm text-[var(--text-muted)]">{{ s.durationDays | duration }} · {{ s.contractedPrice | soles }}{{ s.origin === 'MANUAL' ? ' · registrada a mano' : '' }}</p>
          }
        </div>
        <div class="text-right">
          <app-status-badge [label]="s.statusLabel" [tone]="tone(s.status)" />
          @if (remaining(); as r) { <p class="mt-0.5 text-xs font-semibold" [class]="(s.daysRemaining ?? 99) <= 3 ? 'text-warning' : 'text-[var(--text-muted)]'">{{ r }}</p> }
        </div>
      </div>

      <dl class="flex flex-wrap gap-x-8 gap-y-1 text-sm">
        @if (s.paidAt) { <div><dt class="inline text-[var(--text-muted)]">Pagada: </dt><dd class="inline text-night">{{ date(s.paidAt) }}</dd></div> }
        <div><dt class="inline text-[var(--text-muted)]">Inicio: </dt><dd class="inline text-night">{{ s.startAt ? date(s.startAt) : 'sin activar' }}</dd></div>
        <div><dt class="inline text-[var(--text-muted)]">Vence: </dt><dd class="inline text-night">{{ date(s.expiresAt) }}</dd></div>
        @if (s.capacityStatus === 'RELEASE_PENDING') { <div class="font-semibold text-warning">Cupo pendiente de liberar</div> }
      </dl>
      @if (s.cancellationReason) { <p class="text-sm text-[var(--text-muted)]">Motivo de cancelación: {{ s.cancellationReason }}</p> }

      @if (error(); as message) { <p class="notice notice-error" role="alert">{{ message }}</p> }

      @if (mode() === 'activate') {
        <div class="flex flex-wrap items-end gap-3 rounded-[var(--radius-control)] bg-white p-4 ring-1 ring-[var(--line-subtle)]">
          <label class="field">
            <span class="field-label">Inicio real del servicio</span>
            <input class="input" type="date" [formControl]="startDate" [max]="today" />
          </label>
          <button type="button" class="btn btn-primary" [disabled]="busy()" (click)="activate()">Confirmar activación</button>
          <button type="button" class="btn btn-secondary" (click)="mode.set('none')">Volver</button>
          <p class="w-full text-xs text-[var(--text-muted)]">Usa la fecha en que el cliente empezó a usar el servicio. El vencimiento se calcula desde ahí.</p>
        </div>
      } @else if (mode() === 'cancel') {
        <div class="grid gap-3 rounded-[var(--radius-control)] bg-white p-4 ring-1 ring-[var(--line-subtle)]">
          <label class="field">
            <span class="field-label">Motivo de cancelación</span>
            <input class="input" [formControl]="reason" maxlength="300" placeholder="Ej.: el cliente pidió darse de baja" />
          </label>
          <div class="flex flex-wrap gap-3">
            <button type="button" class="btn bg-danger text-snow" [disabled]="busy() || reason.invalid" (click)="cancel()">Cancelar suscripción</button>
            <button type="button" class="btn btn-secondary" (click)="mode.set('none')">Volver</button>
          </div>
        </div>
      } @else {
        <div class="flex flex-wrap gap-2">
          @if (s.status === 'PENDING_ACTIVATION') {
            <button type="button" class="btn btn-primary !min-h-10" (click)="mode.set('activate')">Registrar activación</button>
          }
          @if (canRelease()) {
            <button type="button" class="btn btn-secondary !min-h-10" [disabled]="busy()" (click)="release()">Confirmar que retiré el acceso</button>
          }
          @if (['PENDING_ACTIVATION', 'ACTIVE', 'RENEWAL_PENDING', 'EXPIRED'].includes(s.status)) {
            <button type="button" class="btn !min-h-10 text-danger hover:bg-danger-soft" (click)="mode.set('cancel')">Cancelar</button>
          }
          @if (s.orderId && showOrderLink()) {
            <a class="btn !min-h-10 text-corporate hover:bg-mist/30" [routerLink]="['/panel/ventas', s.orderId]">Ver pedido</a>
          }
          <button type="button" class="btn !min-h-10 text-corporate hover:bg-mist/30" [attr.aria-expanded]="historyOpen()"
                  (click)="toggleHistory()">Historial de renovaciones</button>
        </div>
      }
      @if (historyOpen()) {
        <section class="border-t border-[var(--line-subtle)] pt-4" aria-label="Historial de renovaciones" [attr.aria-busy]="historyLoading()">
          @if (historyLoading()) { <p role="status">Cargando renovaciones…</p> }
          @if (historyError(); as message) {
            <p class="notice notice-error" role="alert">{{ message }}</p>
            <button type="button" class="link mt-2" (click)="loadHistory()">Volver a intentar</button>
          }
          @if (history(); as h) {
            @if (h.items.length === 0) { <p class="text-sm">No hay renovaciones cobradas para esta suscripción.</p> }
            <ul class="divide-y divide-[var(--line-subtle)]">
              @for (r of h.items; track r.id) {
                <li class="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
                  <div><p class="font-semibold text-night">{{ r.amount | soles }} · confirmado {{ date(r.confirmedAt) }}</p>
                    <p>Vencimiento anterior: {{ date(r.previousExpiresAt) }} · nuevo: {{ date(r.newExpiresAt) }}</p></div>
                  <a class="link" [routerLink]="['/panel/ventas', r.orderId]">Ver pedido de renovación</a>
                </li>
              }
            </ul>
            @if (h.totalPages > 1) {
              <nav class="mt-3 flex flex-wrap items-center gap-3" aria-label="Paginación del historial">
                <button type="button" class="btn btn-secondary" [disabled]="historyLoading() || h.page === 0" (click)="loadHistory(h.page - 1)">Anterior</button>
                <span class="text-sm">{{ h.page + 1 }} / {{ h.totalPages }}</span>
                <button type="button" class="btn btn-secondary" [disabled]="historyLoading() || h.page + 1 >= h.totalPages" (click)="loadHistory(h.page + 1)">Siguiente</button>
              </nav>
            }
          }
        </section>
      }
    </article>
  `,
})
export class SubscriptionItemComponent {
  readonly sub = input.required<Subscription>();
  readonly showCustomer = input(false);
  readonly showOrderLink = input(true);
  readonly changed = output<Subscription>();

  private readonly api = inject(SubscriptionsApi);
  protected readonly tone = subscriptionTone;
  protected readonly date = shortDate;
  protected readonly today = new Date().toISOString().slice(0, 10);
  protected readonly mode = signal<Mode>('none');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly historyOpen = signal(false);
  protected readonly history = signal<Page<RenewalHistoryItem> | null>(null);
  protected readonly historyLoading = signal(false);
  protected readonly historyError = signal<string | null>(null);
  protected readonly startDate = new FormControl(this.today, { nonNullable: true });
  protected readonly reason = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(5), Validators.maxLength(300)] });

  protected readonly remaining = computed(() => remainingText(this.sub()));
  protected readonly canRelease = computed(() => {
    const s = this.sub();
    return (s.status === 'EXPIRED' || s.status === 'CANCELLED') && s.capacityStatus === 'RELEASE_PENDING';
  });

  protected activate(): void {
    // Hoy = momento actual; otra fecha = mediodía local de ese día.
    const value = this.startDate.value;
    const startAt = !value || value === this.today ? null : new Date(`${value}T12:00:00`).toISOString();
    this.run(this.api.activate(this.sub().id, startAt));
  }

  protected cancel(): void {
    this.run(this.api.cancel(this.sub().id, this.reason.value.trim()));
  }

  protected release(): void {
    this.run(this.api.releaseCapacity(this.sub().id));
  }

  protected toggleHistory(): void {
    this.historyOpen.update((open) => !open);
    if (this.historyOpen()) this.loadHistory();
  }

  protected loadHistory(page = 0): void {
    if (this.historyLoading()) return;
    this.historyLoading.set(true);
    this.historyError.set(null);
    this.api.renewals(this.sub().id, page).subscribe({
      next: (history) => { this.history.set(history); this.historyLoading.set(false); },
      error: (e: unknown) => { this.historyError.set(ApiError.from(e).message); this.historyLoading.set(false); },
    });
  }

  private run(request: Observable<Subscription>): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: (s) => {
        this.busy.set(false);
        this.mode.set('none');
        this.changed.emit(s);
      },
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.busy.set(false);
      },
    });
  }
}
