import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiError } from '../../core/services/api-error';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { SolesPipe } from '../../shared/pipes/soles.pipe';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { BillingPeriod, Plan, planLines } from '../billing/billing.api';
import { AdminApi, PlanPayload } from './admin.api';

@Component({
  selector: 'app-admin-plans-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, SolesPipe, StatusBadgeComponent],
  template: `
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p class="type-eyebrow">Comercialización</p>
        <h1 class="type-h1 mt-3">Planes</h1>
        <p class="mt-3 max-w-2xl">Precios, límites y funciones se definen aquí. Cambiar un precio no altera cobros ya emitidos; desactivar un plan lo retira de la venta, pero quien lo tiene puede renovarlo.</p>
      </div>
      @if (editing() === null) { <button type="button" class="btn btn-primary" (click)="startNew()">Nuevo plan</button> }
    </div>

    @if (notice(); as n) { <p class="notice notice-success mt-6" role="status">{{ n }}</p> }
    @if (error(); as e) { <p class="notice notice-error mt-6" role="alert">{{ e }}</p> }

    @if (editing() !== null) {
      <form class="panel mt-8 grid gap-6 p-6 sm:p-8" [formGroup]="form" (ngSubmit)="save()" novalidate>
        <h2 class="type-h2">{{ editing() === 'new' ? 'Nuevo plan' : 'Editar plan' }}</h2>
        <div class="grid gap-5 sm:grid-cols-2">
          <div class="field">
            <label class="field-label" for="name">Nombre</label>
            <input id="name" class="input" formControlName="name" [attr.aria-invalid]="!!err('name')" />
            @if (err('name'); as e) { <p class="field-error">{{ e }}</p> }
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div class="field">
              <label class="field-label" for="price">Precio (S/)</label>
              <input id="price" class="input type-numeric" type="number" min="0" step="0.01" formControlName="price" [attr.aria-invalid]="!!err('price')" />
              <p class="field-hint">0 = plan gratuito</p>
              @if (err('price'); as e) { <p class="field-error">{{ e }}</p> }
            </div>
            <div class="field">
              <label class="field-label" for="period">Periodo</label>
              <select id="period" class="input" formControlName="billingPeriod">
                <option value="MONTHLY">Mensual</option><option value="QUARTERLY">Trimestral</option><option value="YEARLY">Anual</option>
              </select>
            </div>
          </div>
        </div>
        <div class="field">
          <label class="field-label" for="description">Descripción (opcional)</label>
          <input id="description" class="input" formControlName="description" maxlength="500" />
        </div>
        <div class="grid gap-5 sm:grid-cols-3">
          <div class="field">
            <label class="field-label" for="productLimit">Límite de productos activos</label>
            <input id="productLimit" class="input type-numeric" type="number" min="1" formControlName="productLimit" placeholder="Sin límite" />
            @if (err('productLimit'); as e) { <p class="field-error">{{ e }}</p> }
          </div>
          <div class="field">
            <label class="field-label" for="customerLimit">Límite de clientes</label>
            <input id="customerLimit" class="input type-numeric" type="number" min="1" formControlName="customerLimit" placeholder="Sin límite" />
            @if (err('customerLimit'); as e) { <p class="field-error">{{ e }}</p> }
          </div>
          <div class="field">
            <label class="field-label" for="adminUserLimit">Usuarios administradores</label>
            <input id="adminUserLimit" class="input type-numeric" type="number" min="1" formControlName="adminUserLimit" />
            <p class="field-hint">Se aplicará cuando existan cuentas de colaboradores; hoy cada negocio tiene solo a su titular.</p>
          </div>
        </div>
        <fieldset class="grid gap-3">
          <legend class="field-label">Funciones incluidas</legend>
          <label class="flex gap-3"><input type="checkbox" class="checkbox" formControlName="featureReports" /> Reportes de ventas</label>
          <label class="flex gap-3"><input type="checkbox" class="checkbox" formControlName="featureReminders" /> Avisos de renovación automáticos</label>
          <label class="flex gap-3"><input type="checkbox" class="checkbox" formControlName="featureBranding" /> Colores y portada propios en la tienda</label>
        </fieldset>
        <div class="grid gap-5 sm:grid-cols-2">
          <label class="flex gap-3"><input type="checkbox" class="checkbox" formControlName="active" /> Disponible para contratar</label>
          <div class="field">
            <label class="field-label" for="sortOrder">Orden de aparición</label>
            <input id="sortOrder" class="input type-numeric" type="number" min="0" formControlName="sortOrder" />
          </div>
        </div>
        <div class="flex flex-wrap gap-3">
          <button type="submit" class="btn btn-primary" [disabled]="busy()">Guardar plan</button>
          <button type="button" class="btn btn-secondary" (click)="editing.set(null)">Cancelar</button>
        </div>
      </form>
    }

    @if (plans(); as list) {
      @if (list.length === 0) {
        <p class="py-16 text-center font-[family-name:var(--font-editorial)] text-2xl text-night">Aún no hay planes. Crea el primero para que los vendedores puedan contratar.</p>
      } @else {
        <ul class="mt-10 divide-y divide-[var(--line-subtle)] border-y border-[var(--line-subtle)]">
          @for (p of list; track p.id) {
            <li class="grid gap-3 py-6 md:grid-cols-[1.2fr_1.5fr_auto] md:items-start md:gap-8">
              <div>
                <p class="font-[family-name:var(--font-editorial)] text-xl font-semibold text-night">{{ p.name }}</p>
                <p class="type-numeric mt-1">{{ p.price === 0 ? 'Gratis' : (p.price | soles) }} · {{ p.billingPeriodLabel }}</p>
                <div class="mt-2 flex gap-3">
                  <app-status-badge [label]="p.active ? 'A la venta' : 'Retirado'" [tone]="p.active ? 'success' : 'neutral'" />
                  <span class="text-sm text-[var(--text-muted)]">{{ p.subscribers ?? 0 }} {{ p.subscribers === 1 ? 'negocio' : 'negocios' }}</span>
                </div>
              </div>
              <ul class="grid gap-1 text-sm">
                @for (line of lines(p); track line) { <li>— {{ line }}</li> }
              </ul>
              <button type="button" class="btn btn-secondary !min-h-10" (click)="startEdit(p)">Editar</button>
            </li>
          }
        </ul>
      }
    } @else if (!error()) {
      <div class="mt-6 h-40 animate-pulse rounded bg-mist/25" aria-busy="true"></div>
    }
  `,
})
export class AdminPlansPage {
  private readonly api = inject(AdminApi);
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly plans = signal<Plan[] | null>(null);
  protected readonly editing = signal<'new' | string | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly lines = planLines;

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(80)]],
    description: [''],
    price: [0, [Validators.required, Validators.min(0), Validators.max(99999.99)]],
    billingPeriod: this.fb.control<BillingPeriod>('MONTHLY'),
    productLimit: this.fb.control<number | null>(null, [Validators.min(1)]),
    customerLimit: this.fb.control<number | null>(null, [Validators.min(1)]),
    adminUserLimit: [1, [Validators.required, Validators.min(1)]],
    featureReports: [true],
    featureReminders: [true],
    featureBranding: [true],
    active: [true],
    sortOrder: [0, [Validators.required, Validators.min(0)]],
  });

  constructor() {
    this.load();
  }

  protected err(name: keyof typeof this.form.controls): string | null {
    return controlError(this.form.controls[name]);
  }

  protected startNew(): void {
    this.form.reset();
    this.notice.set(null);
    this.editing.set('new');
  }

  protected startEdit(p: Plan): void {
    this.notice.set(null);
    this.form.setValue({
      name: p.name, description: p.description ?? '', price: p.price, billingPeriod: p.billingPeriod,
      productLimit: p.productLimit, customerLimit: p.customerLimit, adminUserLimit: p.adminUserLimit,
      featureReports: p.featureReports, featureReminders: p.featureReminders, featureBranding: p.featureBranding,
      active: p.active, sortOrder: p.sortOrder,
    });
    this.editing.set(p.id);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body: PlanPayload = {
      ...v,
      name: v.name.trim(),
      description: v.description.trim() || null,
      productLimit: v.productLimit || null,
      customerLimit: v.customerLimit || null,
    };
    const id = this.editing();
    this.busy.set(true);
    this.error.set(null);
    (id === 'new' || id === null ? this.api.createPlan(body) : this.api.updatePlan(id, body)).subscribe({
      next: (p) => {
        this.notice.set(`Plan «${p.name}» guardado.`);
        this.editing.set(null);
        this.busy.set(false);
        this.load();
      },
      error: (e: unknown) => {
        const apiError = ApiError.from(e);
        applyServerErrors(this.form, apiError);
        this.error.set(apiError.message);
        this.busy.set(false);
      },
    });
  }

  private load(): void {
    this.api.plans().subscribe({
      next: (p) => this.plans.set(p),
      error: (e: unknown) => this.error.set(ApiError.from(e).message),
    });
  }
}
