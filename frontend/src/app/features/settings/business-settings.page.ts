import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiError } from '../../core/services/api-error';
import { ImageUploadComponent } from '../../shared/components/image-upload.component';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { peruMobileValidator } from '../../shared/validators/peru-phone';
import { Business, SettingsApi } from './settings.api';

type Field = 'businessName' | 'description' | 'primaryColor' | 'whatsappNumber' | 'timeZone';
type ImageKind = 'logo' | 'cover';

const TIME_ZONES = ['America/Lima', 'America/Bogota', 'America/Guayaquil', 'America/La_Paz', 'America/Santiago', 'America/Mexico_City', 'America/Argentina/Buenos_Aires'];

@Component({
  selector: 'app-business-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ImageUploadComponent],
  templateUrl: './business-settings.page.html',
})
export class BusinessSettingsPage {
  private readonly api = inject(SettingsApi);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly timeZones = TIME_ZONES;
  protected readonly business = signal<Business | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly uploading = signal<ImageKind | null>(null);
  protected readonly imageError = signal<{ kind: ImageKind; message: string } | null>(null);

  protected readonly form = this.fb.group({
    businessName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    description: ['', [Validators.maxLength(1000)]],
    primaryColor: ['#1A3D63', [Validators.pattern(/^#[0-9A-Fa-f]{6}$/)]],
    whatsappNumber: ['', [peruMobileValidator]],
    timeZone: ['America/Lima', [Validators.required]],
  });

  constructor() {
    this.api.getBusiness().subscribe({
      next: (b) => this.fill(b),
      error: (e: unknown) => this.loadError.set(ApiError.from(e).message),
    });
    this.form.valueChanges.subscribe(() => this.saved.set(false));
  }

  protected err(name: Field): string | null {
    const control = this.form.controls[name];
    if (name === 'primaryColor' && control.errors?.['pattern'] && control.touched) return 'Usa un color hexadecimal, por ejemplo #1A3D63';
    if (name === 'whatsappNumber' && control.errors?.['peruMobile'] && control.touched) return 'Ingresa un número de WhatsApp peruano válido';
    return controlError(control);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.error.set(null);
    this.api
      .updateBusiness({
        businessName: v.businessName,
        description: v.description || null,
        primaryColor: v.primaryColor || null,
        whatsappNumber: v.whatsappNumber || null,
        timeZone: v.timeZone,
      })
      .subscribe({
        next: (b) => {
          this.fill(b);
          this.saving.set(false);
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

  protected upload(kind: ImageKind, file: File): void {
    this.runImage(kind, this.api.uploadImage(kind, file));
  }

  protected removeImage(kind: ImageKind): void {
    this.runImage(kind, this.api.deleteImage(kind));
  }

  protected imageErrorFor(kind: ImageKind): string | null {
    const e = this.imageError();
    return e?.kind === kind ? e.message : null;
  }

  private runImage(kind: ImageKind, request: ReturnType<SettingsApi['deleteImage']>): void {
    this.uploading.set(kind);
    this.imageError.set(null);
    request.subscribe({
      next: (b) => {
        this.business.set(b);
        this.uploading.set(null);
      },
      error: (e: unknown) => {
        this.imageError.set({ kind, message: ApiError.from(e).message });
        this.uploading.set(null);
      },
    });
  }

  private fill(b: Business): void {
    this.business.set(b);
    this.form.reset(
      {
        businessName: b.businessName,
        description: b.description ?? '',
        primaryColor: b.primaryColor ?? '#1A3D63',
        whatsappNumber: b.whatsappNumber?.replace(/^\+51/, '') ?? '',
        timeZone: b.timeZone,
      },
      { emitEvent: false },
    );
  }
}
