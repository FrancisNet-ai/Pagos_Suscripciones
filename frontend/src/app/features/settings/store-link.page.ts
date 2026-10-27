import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { catchError, debounceTime, distinctUntilChanged, filter, of, switchMap, tap } from 'rxjs';
import { ApiError } from '../../core/services/api-error';
import { Business, SettingsApi, SlugAvailability } from './settings.api';

@Component({
  selector: 'app-store-link-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  template: `
    @if (business(); as b) {
      <section class="grid gap-10">
        <div>
          <h2 class="font-[family-name:var(--font-editorial)] text-2xl font-semibold">Enlace actual</h2>
          <p class="mt-4 break-all font-[family-name:var(--font-editorial)] text-2xl text-night">{{ origin }}/tienda/{{ b.slug }}</p>
          <div class="mt-4 flex items-center gap-3">
            <button type="button" class="btn btn-secondary" (click)="copy(b.slug)">Copiar enlace</button>
            <span class="text-sm text-success" role="status" aria-live="polite">{{ copied() ? 'Enlace copiado' : '' }}</span>
          </div>
        </div>

        <form class="grid gap-5 border-t border-[var(--line-subtle)] pt-10" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <h2 class="font-[family-name:var(--font-editorial)] text-2xl font-semibold">Cambiar enlace</h2>
          <p class="notice notice-info" role="note">Si cambias el enlace, el anterior dejará de funcionar. Actualízalo donde ya lo hayas compartido.</p>

          @if (error(); as message) { <p class="notice notice-error" role="alert">{{ message }}</p> }

          <div class="field">
            <label class="field-label" for="slug">Nuevo enlace</label>
            <div class="flex flex-col sm:flex-row">
              <span class="inline-flex items-center rounded-t-[var(--radius-control)] border border-b-0 border-[var(--line-strong)] bg-snow px-3 py-2 text-sm text-[var(--text-muted)] sm:rounded-l-[var(--radius-control)] sm:rounded-tr-none sm:border-b sm:border-r-0">{{ origin }}/tienda/</span>
              <input id="slug" class="input !rounded-t-none sm:!rounded-l-none sm:!rounded-tr-[var(--radius-control)]" formControlName="slug" autocomplete="off" spellcheck="false"
                     maxlength="50" aria-describedby="slug-status" />
            </div>
            <p id="slug-status" class="text-sm" role="status" aria-live="polite"
               [class.text-success]="check()?.available" [class.text-danger]="check() && !check()!.available">
              @if (checking()) { Comprobando… }
              @else if (check(); as c) { {{ c.available ? 'Disponible' : c.reason }} }
              @else { Letras minúsculas sin tildes, números y guiones. }
            </p>
          </div>

          <div>
            <button class="btn btn-primary" type="submit" [disabled]="saving() || !check()?.available || slug.value === b.slug">
              @if (saving()) { <span class="spinner" aria-hidden="true"></span> Guardando… } @else { Guardar enlace }
            </button>
          </div>
        </form>
      </section>
    } @else if (loadError(); as message) {
      <p class="notice notice-error" role="alert">{{ message }}</p>
    }
  `,
})
export class StoreLinkPage {
  private readonly api = inject(SettingsApi);
  private readonly clipboard = inject(Clipboard);
  protected readonly origin = window.location.origin;

  protected readonly business = signal<Business | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly check = signal<SlugAvailability | null>(null);
  protected readonly checking = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly copied = signal(false);
  protected readonly slug = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  protected readonly form = new FormGroup({ slug: this.slug });

  constructor() {
    this.api.getBusiness().subscribe({
      next: (b) => {
        this.business.set(b);
        this.slug.setValue(b.slug, { emitEvent: false });
      },
      error: (e: unknown) => this.loadError.set(ApiError.from(e).message),
    });

    this.slug.valueChanges
      .pipe(
        tap(() => this.check.set(null)),
        debounceTime(350),
        distinctUntilChanged(),
        filter((value) => value.trim().length > 0 && value !== this.business()?.slug),
        tap(() => this.checking.set(true)),
        switchMap((value) => this.api.checkSlug(value.trim().toLowerCase()).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((result) => {
        this.checking.set(false);
        this.check.set(result);
      });
  }

  protected copy(slug: string): void {
    if (this.clipboard.copy(`${this.origin}/tienda/${slug}`)) {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2500);
    }
  }

  protected save(): void {
    this.saving.set(true);
    this.error.set(null);
    this.api.changeSlug(this.slug.value.trim().toLowerCase()).subscribe({
      next: (b) => {
        this.business.set(b);
        this.check.set(null);
        this.saving.set(false);
      },
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.saving.set(false);
      },
    });
  }
}
