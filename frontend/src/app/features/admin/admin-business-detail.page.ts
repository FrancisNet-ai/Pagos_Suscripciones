import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiError } from '../../core/services/api-error';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { BillingPeriod, Plan, formatDate } from '../billing/billing.api';
import { AdminSupportPanelComponent } from '../support/admin-support-panel.component';
import { AdminApi, AdminBusinessDetail, SUBSCRIPTION_LABEL, actionLabel, subscriptionTone } from './admin.api';

@Component({
  selector: 'app-admin-business-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, SolesPipe, StatusBadgeComponent, AdminSupportPanelComponent],
  template: `
    <a routerLink="/admin/negocios" class="text-sm text-corporate no-underline hover:underline">← Negocios</a>
    @if (error(); as e) { <p class="notice notice-error mt-6" role="alert">{{ e }}</p> }
    @if (notice(); as n) { <p class="notice notice-success mt-6" role="status">{{ n }}</p> }

    @if (data(); as d) {
      <h1 class="type-h1 mt-4">{{ d.summary.businessName }}</h1>
      <p class="mt-2 text-[var(--text-muted)]">
        <span class="type-numeric">{{ d.summary.uniqueCode }}</span> ·
        <a [href]="'/tienda/' + d.summary.slug" target="_blank" rel="noopener" class="link">/tienda/{{ d.summary.slug }}</a> ·
        registrado el {{ formatDate(d.summary.createdAt) }}
      </p>

      <div class="mt-10 grid gap-10 border-y border-[var(--line-subtle)] py-8 md:grid-cols-3">
        <div>
          <p class="text-sm text-[var(--text-muted)]">Titular</p>
          <p class="mt-1 font-semibold text-night">{{ d.summary.ownerName }}</p>
          <p class="text-sm">{{ d.summary.ownerEmail }}</p>
          <p class="type-numeric text-sm">{{ d.ownerPhone }}</p>
        </div>
        <div>
          <p class="text-sm text-[var(--text-muted)]">Plan</p>
          <p class="mt-1 font-semibold text-night">{{ d.summary.planName ?? 'Sin plan' }}</p>
          <div class="mt-1 flex flex-wrap gap-3">
            <app-status-badge [label]="d.summary.subscriptionStatus ? subscriptionLabel[d.summary.subscriptionStatus] : 'Sin plan'" [tone]="tone(d)" />
            @if (d.summary.status === 'SUSPENDED') { <app-status-badge label="Suspendido" tone="danger" /> }
          </div>
          @if (d.summary.expiresAt) { <p class="mt-1 text-sm">Vence el {{ formatDate(d.summary.expiresAt) }}</p> }
        </div>
        <dl class="grid grid-cols-3 gap-3">
          <div><dt class="text-sm text-[var(--text-muted)]">Productos</dt><dd class="type-numeric text-2xl text-night">{{ d.usage.products }}</dd></div>
          <div><dt class="text-sm text-[var(--text-muted)]">Clientes</dt><dd class="type-numeric text-2xl text-night">{{ d.usage.customers }}</dd></div>
          <div><dt class="text-sm text-[var(--text-muted)]">Pedidos pagados</dt><dd class="type-numeric text-2xl text-night">{{ d.usage.ordersPaid }}</dd></div>
        </dl>
      </div>

      <!-- Suspensión -->
      <section class="mt-10 max-w-2xl" aria-labelledby="estado-titulo">
        <h2 id="estado-titulo" class="type-h2">Estado del negocio</h2>
        @if (d.summary.status === 'SUSPENDED') {
          <p class="notice notice-error mt-4" role="note">Suspendido el {{ formatDate(d.suspendedAt) }}. Motivo: {{ d.suspensionReason }}</p>
          <p class="mt-3 text-sm">Reactivar abre de nuevo la tienda y el panel si el plan sigue vigente. Las fechas pagadas no cambian.</p>
          <button type="button" class="btn btn-primary mt-4" [disabled]="busy()" (click)="reactivate(d)">Reactivar negocio</button>
        } @else {
          <p class="mt-3 text-sm">Suspender cierra la tienda pública y deja el panel del vendedor en solo lectura. No se borra ningún dato y el vendedor verá el motivo.</p>
          <div class="field mt-4">
            <label class="field-label" for="reason">Motivo de la suspensión</label>
            <input id="reason" class="input" [formControl]="reason" maxlength="300" />
            @if (reason.touched && reason.invalid) { <p class="field-error">Indica un motivo de 5 a 300 caracteres</p> }
          </div>
          <button type="button" class="btn btn-secondary mt-4 !text-danger" [disabled]="busy()" (click)="suspend(d)">Suspender negocio</button>
        }
      </section>

      <!-- Cortesía -->
      <section class="mt-10 max-w-2xl" aria-labelledby="cortesia-titulo">
        <h2 id="cortesia-titulo" class="type-h2">Plan de cortesía</h2>
        <p class="mt-3 text-sm">
          Activa o extiende un plan sin cobro: para tu propia tienda o para regalar una prueba. Sirve también con planes
          desactivados, que no aparecen en la página pública. Si el negocio ya tiene ese plan vigente, se suma al vencimiento.
          Anula el cobro pendiente y queda registrado con el motivo.
        </p>
        @if (plans().length > 0) {
          <form class="mt-4 grid gap-4" [formGroup]="gift" (ngSubmit)="grant(d)" novalidate>
            <div class="grid gap-4 sm:grid-cols-2">
              <div class="field">
                <label class="field-label" for="gift-plan">Plan</label>
                <select id="gift-plan" class="input" formControlName="planId">
                  @for (p of plans(); track p.id) {
                    <option [value]="p.id">{{ p.name }}{{ p.active ? '' : ' (oculto)' }}</option>
                  }
                </select>
              </div>
              <div class="field">
                <label class="field-label" for="gift-periods">Duración</label>
                <select id="gift-periods" class="input" formControlName="periods">
                  @for (n of periodOptions; track n) { <option [ngValue]="n">{{ durationLabel(n) }}</option> }
                </select>
              </div>
            </div>
            <div class="field">
              <label class="field-label" for="gift-reason">Motivo</label>
              <input id="gift-reason" class="input" formControlName="reason" maxlength="300" placeholder="Ej.: Tienda propia del titular" />
              @if (gift.controls.reason.touched && gift.controls.reason.invalid) { <p class="field-error">Indica un motivo de 5 a 300 caracteres</p> }
            </div>
            <div><button type="submit" class="btn btn-primary" [disabled]="busy()">Dar plan de cortesía</button></div>
          </form>
        } @else {
          <p class="mt-4 text-sm">Primero crea un plan en «Planes».</p>
        }
      </section>

      <app-admin-support-panel [businessId]="d.summary.id" [until]="d.supportAccessUntil ?? null" />

      <!-- Cobros -->
      <section class="mt-12" aria-labelledby="cobros-titulo">
        <h2 id="cobros-titulo" class="type-h2">Cobros SaaS</h2>
        @if (d.invoices.length === 0) {
          <p class="mt-4">Sin cobros todavía.</p>
        } @else {
          <ul class="mt-6 divide-y divide-[var(--line-subtle)] border-y border-[var(--line-subtle)]">
            @for (i of d.invoices; track i.id) {
              <li class="grid gap-1 py-4 sm:grid-cols-[1fr_auto_auto] sm:items-center sm:gap-6">
                <div>
                  <p class="font-semibold text-night">{{ i.invoiceNumber }} · {{ i.planName }}</p>
                  <p class="text-sm text-[var(--text-muted)]">{{ i.kindLabel }} · {{ formatDate(i.createdAt) }}@if (i.periodEnd) { · hasta {{ formatDate(i.periodEnd) }} }</p>
                </div>
                <span class="type-numeric font-semibold">{{ i.amount | soles }}</span>
                <app-status-badge [label]="i.status === 'PAID' ? 'Pagado' : i.status === 'VOID' ? 'Anulado' : 'Pendiente'"
                                  [tone]="i.status === 'PAID' ? 'success' : i.status === 'VOID' ? 'neutral' : 'warning'" />
              </li>
            }
          </ul>
        }
      </section>

      <!-- Actividad -->
      <section class="mt-12" aria-labelledby="actividad-titulo">
        <h2 id="actividad-titulo" class="type-h2">Actividad de la cuenta</h2>
        <ul class="mt-6 divide-y divide-[var(--line-subtle)]">
          @for (a of d.activity; track a.id) {
            <li class="flex flex-wrap justify-between gap-2 py-3 text-sm">
              <span><span class="font-semibold text-night">{{ actionLabel(a.action) }}</span>@if (a.actorEmail) { · {{ a.actorEmail }} }</span>
              <span class="text-[var(--text-muted)]">{{ dateTime(a.createdAt) }}</span>
            </li>
          } @empty {
            <li class="py-3 text-sm">Sin actividad registrada.</li>
          }
        </ul>
      </section>
    } @else if (!error()) {
      <div class="mt-6 h-56 animate-pulse rounded bg-mist/25" aria-busy="true"></div>
    }
  `,
})
export class AdminBusinessDetailPage {
  private readonly api = inject(AdminApi);
  readonly id = input.required<string>();
  protected readonly data = signal<AdminBusinessDetail | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly reason = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(5), Validators.maxLength(300)] });
  protected readonly plans = signal<Plan[]>([]);
  protected readonly periodOptions = [1, 2, 3, 6, 12];
  protected readonly gift = new FormGroup({
    planId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    periods: new FormControl(1, { nonNullable: true, validators: [Validators.required] }),
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(5), Validators.maxLength(300)] }),
  });
  protected readonly subscriptionLabel = SUBSCRIPTION_LABEL;
  protected readonly formatDate = formatDate;
  protected readonly actionLabel = actionLabel;

  ngOnInit(): void {
    this.api.business(this.id()).subscribe({
      next: (d) => this.data.set(d),
      error: (e: unknown) => this.error.set(ApiError.from(e).message),
    });
    this.api.plans().subscribe({
      next: (list) => {
        this.plans.set(list);
        if (list.length > 0 && !this.gift.controls.planId.value) this.gift.controls.planId.setValue(list[0].id);
      },
    });
  }

  /** «3 meses», «2 trimestres», «1 año», según el periodo del plan elegido. */
  protected durationLabel(n: number): string {
    const period: BillingPeriod = this.plans().find((p) => p.id === this.gift.controls.planId.value)?.billingPeriod ?? 'MONTHLY';
    const [one, many] = { MONTHLY: ['mes', 'meses'], QUARTERLY: ['trimestre', 'trimestres'], YEARLY: ['año', 'años'] }[period];
    return `${n} ${n === 1 ? one : many}`;
  }

  protected grant(d: AdminBusinessDetail): void {
    if (this.gift.invalid) {
      this.gift.markAllAsTouched();
      return;
    }
    const { planId, periods, reason } = this.gift.getRawValue();
    const planName = this.plans().find((p) => p.id === planId)?.name ?? '';
    this.act(this.api.complimentary(d.summary.id, planId, periods, reason.trim()), `Plan ${planName} de cortesía activado.`);
  }

  protected tone(d: AdminBusinessDetail) {
    return subscriptionTone(d.summary.subscriptionStatus);
  }

  protected suspend(d: AdminBusinessDetail): void {
    if (this.reason.invalid) {
      this.reason.markAsTouched();
      return;
    }
    this.act(this.api.suspend(d.summary.id, this.reason.value.trim()), 'Negocio suspendido.');
  }

  protected reactivate(d: AdminBusinessDetail): void {
    this.act(this.api.reactivate(d.summary.id), 'Negocio reactivado.');
  }

  protected dateTime(iso: string): string {
    return new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso));
  }

  private act(request: ReturnType<AdminApi['reactivate']>, success: string): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: (d) => {
        this.data.set(d);
        this.notice.set(success);
        this.reason.reset();
        this.gift.controls.reason.reset();
        this.busy.set(false);
      },
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.busy.set(false);
      },
    });
  }
}
