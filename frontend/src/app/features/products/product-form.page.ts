import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { ImageUploadComponent } from '../../shared/components/image-upload.component';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { DurationPipe, SolesPipe } from '../../shared/pipes/soles.pipe';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { STATUS_LABELS } from './product-labels';
import { Product, ProductPayload, ProductType, ProductsApi, PublicationStatus } from './products.api';

type Field = 'name' | 'description' | 'category' | 'price' | 'durationDays' | 'capacity';

const DURATION_PRESETS = [
  { days: 30, label: '1 mes' },
  { days: 90, label: '3 meses' },
  { days: 180, label: '6 meses' },
  { days: 365, label: '1 año' },
];

@Component({
  selector: 'app-product-form-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, ImageUploadComponent, StatusBadgeComponent, SolesPipe, DurationPipe],
  templateUrl: './product-form.page.html',
})
export class ProductFormPage implements OnInit {
  /** Parámetro de ruta `:id`; ausente al crear. */
  readonly id = input<string>();

  private readonly api = inject(ProductsApi);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly presets = DURATION_PRESETS;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly product = signal<Product | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly statusBusy = signal(false);
  protected readonly imageBusy = signal(false);
  protected readonly imageError = signal<string | null>(null);

  protected readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    description: ['', [Validators.maxLength(2000)]],
    category: ['', [Validators.maxLength(60)]],
    productType: this.fb.control<ProductType>('SUBSCRIPTION'),
    price: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.1), Validators.max(99999.99)]),
    durationDays: this.fb.control<number | null>(30, [Validators.min(1), Validators.max(3650)]),
    available: [true],
    capacityEnabled: [false],
    capacity: this.fb.control<number | null>(null, [Validators.min(1), Validators.max(100000)]),
  });

  protected readonly productType = toSignal(this.form.controls.productType.valueChanges, { initialValue: 'SUBSCRIPTION' as ProductType });
  protected readonly capacityEnabled = toSignal(this.form.controls.capacityEnabled.valueChanges, { initialValue: false });
  protected readonly isNew = computed(() => !this.id());

  constructor() {
    this.form.controls.productType.valueChanges.subscribe((type) => {
      // La duración por defecto (30 días) solo tiene sentido para suscripciones.
      const duration = this.form.controls.durationDays;
      if (type === 'ONE_TIME' && duration.pristine) duration.setValue(null);
      if (type === 'SUBSCRIPTION' && duration.value === null) duration.setValue(30);
      this.syncDurationValidator(type);
    });
    this.form.controls.capacityEnabled.valueChanges.subscribe((enabled) => {
      const capacity = this.form.controls.capacity;
      capacity.setValidators(enabled ? [Validators.required, Validators.min(1), Validators.max(100000)] : []);
      capacity.updateValueAndValidity({ emitEvent: false });
    });
    this.form.valueChanges.subscribe(() => this.saved.set(false));
    this.syncDurationValidator('SUBSCRIPTION');
  }

  ngOnInit(): void {
    const id = this.id();
    if (!id) return;
    this.loading.set(true);
    this.api.get(id).subscribe({
      next: (p) => {
        this.fill(p);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.loadError.set(ApiError.from(e).message);
        this.loading.set(false);
      },
    });
  }

  protected err(name: Field): string | null {
    const control = this.form.controls[name];
    if (control.touched && control.errors?.['min']) return name === 'price' ? 'El precio mínimo es S/ 0.10' : 'Debe ser mayor que cero';
    if (control.touched && control.errors?.['max']) return 'El valor es demasiado alto';
    return controlError(control);
  }

  protected setDuration(days: number): void {
    this.form.controls.durationDays.setValue(days);
    this.form.controls.durationDays.markAsDirty();
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const payload: ProductPayload = {
      name: v.name.trim(),
      description: v.description.trim() || null,
      category: v.category.trim() || null,
      productType: v.productType,
      price: Math.round(Number(v.price) * 100) / 100,
      durationDays: v.durationDays || null,
      capacityEnabled: v.capacityEnabled,
      capacity: v.capacityEnabled ? v.capacity : null,
      available: v.available,
    };
    this.saving.set(true);
    this.error.set(null);
    const id = this.id();
    const request = id ? this.api.update(id, payload) : this.api.create(payload);
    request.subscribe({
      next: (p) => {
        this.saving.set(false);
        if (!id) {
          void this.router.navigate(['/panel/productos', p.id], { state: { created: true } });
          return;
        }
        this.fill(p);
        this.saved.set(true);
      },
      error: (e: unknown) => {
        const apiError = ApiError.from(e);
        applyServerErrors(this.form, apiError);
        this.error.set(apiError.message);
        this.saving.set(false);
      },
    });
  }

  protected changeStatus(status: PublicationStatus): void {
    const p = this.product();
    if (!p) return;
    this.statusBusy.set(true);
    this.error.set(null);
    this.api.changeStatus(p.id, status).subscribe({
      next: (updated) => {
        this.product.set(updated);
        this.statusBusy.set(false);
      },
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.statusBusy.set(false);
      },
    });
  }

  protected uploadImage(file: File): void {
    this.runImage(this.api.uploadImage(this.product()!.id, file));
  }

  protected removeImage(): void {
    this.runImage(this.api.deleteImage(this.product()!.id));
  }

  private runImage(request: Observable<Product>): void {
    this.imageBusy.set(true);
    this.imageError.set(null);
    request.subscribe({
      next: (p) => {
        this.product.set(p);
        this.imageBusy.set(false);
      },
      error: (e: unknown) => {
        this.imageError.set(ApiError.from(e).message);
        this.imageBusy.set(false);
      },
    });
  }

  private syncDurationValidator(type: ProductType): void {
    const duration = this.form.controls.durationDays;
    duration.setValidators(type === 'SUBSCRIPTION' ? [Validators.required, Validators.min(1), Validators.max(3650)] : [Validators.min(1), Validators.max(3650)]);
    duration.updateValueAndValidity({ emitEvent: false });
  }

  private fill(p: Product): void {
    this.product.set(p);
    this.form.reset({
      name: p.name,
      description: p.description ?? '',
      category: p.category ?? '',
      productType: p.productType,
      price: p.price,
      durationDays: p.durationDays,
      available: p.available,
      capacityEnabled: p.availability.capacityEnabled,
      capacity: p.availability.capacity,
    });
  }
}
