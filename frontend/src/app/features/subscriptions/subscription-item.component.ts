import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { DurationPipe, SolesPipe } from '../../shared/pipes/soles.pipe';
import { Subscription, SubscriptionsApi, remainingText, shortDate, subscriptionTone } from './subscriptions.api';

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
        </div>
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
