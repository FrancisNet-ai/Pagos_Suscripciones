import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
      @if (loadError()) { <button type="button" class="link justify-self-start" (click)="load()">Volver a cargar ajustes</button> }
      @if (loading()) { <p role="status">Cargando ajustes…</p> }

      <fieldset class="grid gap-6" [disabled]="loading() || loadError() || saving()">
      <legend class="sr-only">Política de avisos y renovaciones</legend>
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
        <button class="btn btn-primary" type="submit" [disabled]="saving() || form.pristine || loading() || loadError()">Guardar</button>
        <span class="text-sm text-success" role="status" aria-live="polite">{{ saved() ? 'Cambios guardados' : '' }}</span>
      </div>
      <p class="text-xs text-[var(--text-muted)]">Los cambios se aplican a las próximas solicitudes de renovación.</p>
      </fieldset>
    </form>
  `,
})
export class RenewalSettingsPage {
  private readonly api = inject(RenewalsApi);
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(true);
  protected readonly loadError = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    reminderDaysBefore: [3, [Validators.required]],
    reminderOnDueDay: [true],
    renewalGraceDays: [3, [Validators.required]],
  });

  constructor() {
    this.load();
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.saved.set(false));
  }

  protected load(): void {
    this.loading.set(true);
    this.loadError.set(false);
    this.error.set(null);
    this.api.settings().subscribe({
      next: (s) => { this.form.reset(s); this.loading.set(false); },
      error: (e: unknown) => { this.error.set(ApiError.from(e).message); this.loading.set(false); this.loadError.set(true); },
    });
  }

  protected save(): void {
    if (this.form.invalid || this.loading() || this.loadError() || this.saving()) return;
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
