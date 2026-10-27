import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiError } from '../../core/services/api-error';
import { RenewalsApi } from '../renewals/renewals.api';

@Component({
  selector: 'app-renewal-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  template: `
    <form class="grid max-w-2xl gap-6" [formGroup]="form" (ngSubmit)="save()" novalidate>
      <div>
        <h2 class="font-[family-name:var(--font-editorial)] text-2xl font-semibold">Avisos de renovación</h2>
        <p class="mt-2">Preparamos los avisos para que los envíes desde tu WhatsApp. Cada aviso incluye un enlace personal para renovar o rechazar.</p>
      </div>
      @if (error(); as message) { <p class="notice notice-error" role="alert">{{ message }}</p> }

      <div class="field">
        <label class="field-label" for="reminderDaysBefore">Aviso previo</label>
        <select id="reminderDaysBefore" class="input max-w-xs" formControlName="reminderDaysBefore">
          <option [ngValue]="0">Sin aviso previo</option>
          @for (d of [1, 2, 3, 5, 7, 10, 15]; track d) { <option [ngValue]="d">{{ d }} {{ d === 1 ? 'día' : 'días' }} antes del vencimiento</option> }
        </select>
      </div>

      <label class="flex items-start gap-3">
        <input class="checkbox" type="checkbox" formControlName="reminderOnDueDay" />
        <span><span class="font-semibold text-night">Aviso el día del vencimiento</span><br /><span class="text-sm">Un último recordatorio el mismo día.</span></span>
      </label>

      <div class="field">
        <label class="field-label" for="renewalGraceDays">Plazo de gracia</label>
        <select id="renewalGraceDays" class="input max-w-xs" formControlName="renewalGraceDays" aria-describedby="grace-hint">
          @for (d of [0, 1, 2, 3, 5, 7, 10, 15, 30]; track d) { <option [ngValue]="d">{{ d === 0 ? 'Sin plazo de gracia' : d + (d === 1 ? ' día' : ' días') }}</option> }
        </select>
        <p id="grace-hint" class="field-hint">Días después del vencimiento en que el enlace sigue activo y el cupo se mantiene reservado para quien quiere renovar.</p>
      </div>

      <div class="flex items-center gap-4 border-t border-[var(--line-subtle)] pt-6">
        <button class="btn btn-primary" type="submit" [disabled]="saving() || form.pristine">Guardar</button>
        <span class="text-sm text-success" role="status" aria-live="polite">{{ saved() ? 'Cambios guardados' : '' }}</span>
      </div>
      <p class="text-xs text-[var(--text-muted)]">Los cambios se aplican a las próximas solicitudes de renovación.</p>
    </form>
  `,
})
export class RenewalSettingsPage {
  private readonly api = inject(RenewalsApi);
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(NonNullableFormBuilder).group({
    reminderDaysBefore: [3, [Validators.required]],
    reminderOnDueDay: [true],
    renewalGraceDays: [3, [Validators.required]],
  });

  constructor() {
    this.api.settings().subscribe((s) => this.form.reset(s));
    this.form.valueChanges.subscribe(() => this.saved.set(false));
  }

  protected save(): void {
    this.saving.set(true);
    this.error.set(null);
    this.api.updateSettings(this.form.getRawValue()).subscribe({
      next: (s) => {
        this.form.reset(s);
        this.saving.set(false);
        this.saved.set(true);
      },
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.saving.set(false);
      },
    });
  }
}
