import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { startWith } from 'rxjs';
import { controlError } from '../../shared/validators/form-errors';
import { Product, ProductsApi } from '../products/products.api';
import { ManualSubscriptionPayload } from './subscriptions.api';

export type ManualSubscriptionForm = FormGroup<{
  productId: FormControl<string>;
  startDate: FormControl<string>;
  durationDays: FormControl<number | null>;
  contractedPrice: FormControl<number | null>;
  status: FormControl<'ACTIVE' | 'EXPIRED' | 'CANCELLED'>;
  notes: FormControl<string>;
}>;

export function manualSubscriptionForm(fb: NonNullableFormBuilder): ManualSubscriptionForm {
  return fb.group({
    productId: ['', [Validators.required]],
    startDate: ['', [Validators.required]],
    durationDays: fb.control<number | null>(30, [Validators.required, Validators.min(1), Validators.max(3650)]),
    contractedPrice: fb.control<number | null>(null, [Validators.required, Validators.min(0), Validators.max(99999.99)]),
    status: fb.control<'ACTIVE' | 'EXPIRED' | 'CANCELLED'>('ACTIVE'),
    notes: ['', [Validators.maxLength(1000)]],
  });
}

export function toManualPayload(form: ManualSubscriptionForm): ManualSubscriptionPayload {
  const v = form.getRawValue();
  return {
    productId: v.productId,
    startDate: v.startDate,
    durationDays: Number(v.durationDays),
    contractedPrice: Math.round(Number(v.contractedPrice) * 100) / 100,
    status: v.status,
    notes: v.notes.trim() || null,
  };
}

/** Campos para registrar una suscripción que el cliente ya tenía antes de usar la plataforma. */
@Component({
  selector: 'app-manual-subscription-fields',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  template: `
    <div class="grid gap-5" [formGroup]="form()">
      <div class="field">
        <label class="field-label" for="ms-product">Producto contratado</label>
        <select id="ms-product" class="input" formControlName="productId" (change)="onProduct()" [attr.aria-invalid]="!!err('productId')">
          <option value="" disabled>Selecciona un producto</option>
          @for (p of products(); track p.id) { <option [value]="p.id">{{ p.name }}{{ p.publicationStatus === 'ARCHIVED' ? ' (archivado)' : '' }}</option> }
        </select>
        @if (err('productId'); as e) { <p class="field-error">{{ e }}</p> }
        @if (products().length === 0 && loaded()) { <p class="field-hint">Primero crea el producto en tu catálogo (puede quedar como borrador).</p> }
      </div>
      <div class="grid gap-5 sm:grid-cols-3">
        <div class="field">
          <label class="field-label" for="ms-start">Fecha de inicio</label>
          <input id="ms-start" class="input" type="date" formControlName="startDate" [max]="maxDate" [attr.aria-invalid]="!!err('startDate')" />
          @if (err('startDate'); as e) { <p class="field-error">{{ e }}</p> }
        </div>
        <div class="field">
          <label class="field-label" for="ms-duration">Duración (días)</label>
          <input id="ms-duration" class="input type-numeric" type="number" min="1" formControlName="durationDays" [attr.aria-invalid]="!!err('durationDays')" />
          @if (err('durationDays'); as e) { <p class="field-error">{{ e }}</p> }
        </div>
        <div class="field">
          <label class="field-label" for="ms-price">Importe pagado</label>
          <div class="flex">
            <span class="inline-flex items-center rounded-l-[var(--radius-control)] border border-r-0 border-[var(--line-strong)] bg-snow px-3 text-sm font-semibold text-night">S/</span>
            <input id="ms-price" class="input type-numeric !rounded-l-none" type="number" step="0.01" min="0" formControlName="contractedPrice" [attr.aria-invalid]="!!err('contractedPrice')" />
          </div>
          @if (err('contractedPrice'); as e) { <p class="field-error">{{ e }}</p> }
        </div>
      </div>
      <p class="text-sm" aria-live="polite">
        Vence el <strong class="text-night">{{ expiryText() }}</strong>.
        <span class="text-[var(--text-muted)]">El importe es histórico: no se suma a tus ingresos confirmados.</span>
      </p>
      <fieldset class="grid gap-2">
        <legend class="field-label mb-1">Estado actual</legend>
        <div class="flex flex-wrap gap-4">
          @for (opt of statuses; track opt.value) {
            <label class="flex items-center gap-2"><input type="radio" class="checkbox" formControlName="status" [value]="opt.value" /> {{ opt.label }}</label>
          }
        </div>
        @if (err('status'); as e) { <p class="field-error">{{ e }}</p> }
      </fieldset>
      <div class="field">
        <label class="field-label" for="ms-notes">Notas internas (opcional)</label>
        <textarea id="ms-notes" class="input min-h-20" formControlName="notes" maxlength="1000" placeholder="Ej.: perfil 3 de la cuenta familiar"></textarea>
        <p class="field-hint">No guardes contraseñas de cuentas de terceros.</p>
      </div>
    </div>
  `,
})
export class ManualSubscriptionFieldsComponent implements OnInit {
  readonly form = input.required<ManualSubscriptionForm>();

  private readonly productsApi = inject(ProductsApi);
  protected readonly products = signal<Product[]>([]);
  protected readonly loaded = signal(false);
  protected readonly maxDate = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);
  protected readonly statuses = [
    { value: 'ACTIVE', label: 'Activa' },
    { value: 'EXPIRED', label: 'Vencida' },
    { value: 'CANCELLED', label: 'Cancelada' },
  ] as const;

  private values = signal<{ startDate?: string; durationDays?: number | null }>({});
  protected readonly expiryText = computed(() => {
    const { startDate, durationDays } = this.values();
    if (!startDate || !durationDays) return '—';
    const date = new Date(`${startDate}T12:00:00`);
    date.setDate(date.getDate() + Number(durationDays));
    return new Intl.DateTimeFormat('es-PE', { day: 'numeric', month: 'long', year: 'numeric' }).format(date);
  });

  ngOnInit(): void {
    const form = this.form();
    if (!form.controls.startDate.value) form.controls.startDate.setValue(new Date().toISOString().slice(0, 10));
    form.valueChanges.pipe(startWith(form.getRawValue())).subscribe(() => {
      const v = form.getRawValue();
      this.values.set({ startDate: v.startDate, durationDays: v.durationDays });
    });
    this.productsApi.list({ filter: 'ALL', size: 100 }).subscribe((page) => {
      this.products.set(page.items);
      this.loaded.set(true);
    });
  }

  protected err(name: keyof ManualSubscriptionForm['controls']): string | null {
    return controlError(this.form().controls[name]);
  }

  /** Propone la duración y el precio del producto; el vendedor puede ajustarlos. */
  protected onProduct(): void {
    const product = this.products().find((p) => p.id === this.form().controls.productId.value);
    if (!product) return;
    if (product.durationDays) this.form().controls.durationDays.setValue(product.durationDays);
    this.form().controls.contractedPrice.setValue(product.price);
  }
}
