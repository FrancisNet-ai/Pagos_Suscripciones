import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { StaffPermission } from '../../core/auth/auth.models';
import { ApiError } from '../../core/services/api-error';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { formatDate } from '../billing/billing.api';
import { InvitationCreated, PERMISSION_LABEL, StaffApi, Team, TeamMember } from './staff.api';

type PermissionForm = FormGroup<Record<StaffPermission, FormControl<boolean>>>;

const PERMISSIONS = Object.keys(PERMISSION_LABEL) as StaffPermission[];

function atLeastOne(group: AbstractControl): ValidationErrors | null {
  return Object.values((group as PermissionForm).getRawValue()).some(Boolean) ? null : { nonePicked: true };
}

@Component({
  selector: 'app-team-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, StatusBadgeComponent],
  template: `
    <section class="grid gap-12">
      <div>
        <h2 class="font-[family-name:var(--font-editorial)] text-2xl font-semibold">Equipo</h2>
        <p class="mt-3">
          Invita a quienes te ayudan a atender el negocio. Cada colaborador entra con su propia cuenta y solo ve las
          secciones que elijas. La configuración, los cobros, el plan y el equipo quedan siempre a tu cargo.
        </p>
        @if (team(); as t) {
          <p class="mt-4 text-sm font-semibold text-night" data-testid="seats">
            @if (t.seatLimit === null) {
              Activa un plan para sumar colaboradores.
            } @else {
              {{ t.seatsUsed }} de {{ t.seatLimit }} {{ t.seatLimit === 1 ? 'usuario' : 'usuarios' }} en uso (titular
              incluido, con invitaciones pendientes).
            }
          </p>
        }
      </div>

      @if (notice(); as n) {
        <p class="notice notice-success" role="status">{{ n }}</p>
      }
      @if (error(); as e) {
        <p class="notice notice-error" role="alert">{{ e }}</p>
      }

      @if (created(); as c) {
        <section class="rounded-2xl border border-[var(--line-strong)] p-6" aria-labelledby="enlace-invitacion">
          <h3 id="enlace-invitacion" class="font-semibold text-night">Enlace para {{ c.invitation.fullName }}</h3>
          <p class="mt-2 text-sm">
            Compártelo solo con esa persona. Por seguridad no lo volveremos a mostrar: si se pierde, genera uno nuevo.
            Vence el {{ date(c.invitation.expiresAt) }}.
            @if (c.emailSent) {
              También se lo enviamos a {{ c.invitation.email }}.
            }
          </p>
          <p class="mt-4 break-all rounded bg-snow p-3 font-mono text-xs text-night" data-testid="invitation-link">
            {{ c.link }}
          </p>
          <div class="mt-4 flex flex-wrap items-center gap-3">
            <button type="button" class="btn btn-primary" (click)="copy(c.link)">Copiar enlace</button>
            <a class="btn btn-secondary" [href]="whatsappShare(c)" target="_blank" rel="noopener">Compartir por WhatsApp</a>
            <button type="button" class="btn" (click)="created.set(null)">Listo</button>
            <span class="text-sm text-success" role="status" aria-live="polite">{{ copied() ? 'Enlace copiado' : '' }}</span>
          </div>
        </section>
      }

      @if (team(); as t) {
        <section aria-labelledby="miembros-titulo">
          <h3 id="miembros-titulo" class="font-[family-name:var(--font-editorial)] text-xl font-semibold">Personas con acceso</h3>
          <ul class="mt-4 divide-y divide-[var(--line-subtle)] border-y border-[var(--line-subtle)]">
            @for (m of t.members; track m.id) {
              <li class="grid gap-3 py-5" [attr.data-testid]="'member-' + m.email">
                <div class="flex flex-wrap items-start justify-between gap-3">
                  <div class="min-w-0">
                    <p class="font-semibold text-night">{{ m.fullName }}</p>
                    <p class="break-all text-sm text-[var(--text-muted)]">{{ m.email }}</p>
                  </div>
                  @if (m.role === 'OWNER') {
                    <app-status-badge label="Titular · acceso completo" tone="info" />
                  } @else if (m.status === 'ACTIVE') {
                    <app-status-badge label="Colaborador activo" tone="success" />
                  } @else {
                    <app-status-badge [label]="'Acceso revocado el ' + date(m.revokedAt)" tone="neutral" />
                  }
                </div>
                @if (m.role === 'STAFF') {
                  @if (editing() === m.id) {
                    <form class="grid gap-3" [formGroup]="editForm" (ngSubmit)="savePermissions(m)" novalidate>
                      <fieldset class="grid gap-2">
                        <legend class="field-label mb-1">Secciones de {{ m.fullName }}</legend>
                        @for (p of permissions; track p) {
                          <label class="flex gap-3 text-[0.9375rem]">
                            <input type="checkbox" class="checkbox" [formControlName]="p" />{{ label(p) }}
                          </label>
                        }
                      </fieldset>
                      @if (editForm.touched && editForm.hasError('nonePicked')) {
                        <p class="field-error">Elige al menos una sección.</p>
                      }
                      <div class="flex flex-wrap gap-3">
                        <button type="submit" class="btn btn-primary" [disabled]="busy()">Guardar permisos</button>
                        <button type="button" class="btn" (click)="editing.set(null)">Cancelar</button>
                      </div>
                    </form>
                  } @else {
                    <p class="text-sm">{{ labels(m.permissions) }}</p>
                    <div class="flex flex-wrap gap-3">
                      @if (m.status === 'ACTIVE') {
                        <button type="button" class="btn btn-secondary !min-h-10 !text-xs" (click)="edit(m)">
                          Cambiar permisos
                        </button>
                        <button type="button" class="btn !min-h-10 !text-xs" [disabled]="busy()" (click)="revoke(m)">
                          Revocar acceso
                        </button>
                      } @else {
                        <button type="button" class="btn btn-secondary !min-h-10 !text-xs" [disabled]="busy()" (click)="reactivate(m)">
                          Reactivar acceso
                        </button>
                      }
                    </div>
                  }
                }
              </li>
            }
          </ul>
        </section>

        @if (t.invitations.length > 0) {
          <section aria-labelledby="pendientes-titulo">
            <h3 id="pendientes-titulo" class="font-[family-name:var(--font-editorial)] text-xl font-semibold">Invitaciones pendientes</h3>
            <ul class="mt-4 divide-y divide-[var(--line-subtle)] border-y border-[var(--line-subtle)]">
              @for (i of t.invitations; track i.id) {
                <li class="grid gap-3 py-5">
                  <div class="flex flex-wrap items-start justify-between gap-3">
                    <div class="min-w-0">
                      <p class="font-semibold text-night">{{ i.fullName }}</p>
                      <p class="break-all text-sm text-[var(--text-muted)]">{{ i.email }}</p>
                    </div>
                    @if (i.expired) {
                      <app-status-badge label="Enlace vencido" tone="warning" />
                    } @else {
                      <app-status-badge [label]="'Vence el ' + date(i.expiresAt)" tone="neutral" />
                    }
                  </div>
                  <p class="text-sm">{{ labels(i.permissions) }}</p>
                  <div class="flex flex-wrap gap-3">
                    <button type="button" class="btn btn-secondary !min-h-10 !text-xs" [disabled]="busy()" (click)="renew(i.id)">
                      Generar enlace nuevo
                    </button>
                    <button type="button" class="btn !min-h-10 !text-xs" [disabled]="busy()" (click)="cancel(i.id, i.fullName)">
                      Cancelar invitación
                    </button>
                  </div>
                </li>
              }
            </ul>
          </section>
        }

        <form class="grid gap-5 border-t border-[var(--line-subtle)] pt-10" [formGroup]="form" (ngSubmit)="invite()" novalidate>
          <h3 class="font-[family-name:var(--font-editorial)] text-xl font-semibold">Invitar a un colaborador</h3>
          @if (!canInvite()) {
            <p class="notice notice-info" role="note">
              @if (t.seatLimit === null) {
                Necesitas un plan vigente para sumar colaboradores.
              } @else {
                Tu plan admite {{ t.seatLimit }} {{ t.seatLimit === 1 ? 'usuario' : 'usuarios' }} y ya están en uso.
                Revoca un acceso, cancela una invitación o
              }
              <a class="link" routerLink="/panel/plan">revisa tu plan</a>.
            </p>
          }
          <div class="grid gap-5 sm:grid-cols-2">
            <div class="field">
              <label class="field-label" for="staffName">Nombre</label>
              <input id="staffName" class="input" formControlName="fullName" autocomplete="off" />
              @if (err(form.controls.fullName); as e) {
                <p class="field-error">{{ e }}</p>
              }
            </div>
            <div class="field">
              <label class="field-label" for="staffEmail">Correo</label>
              <input id="staffEmail" class="input" type="email" formControlName="email" autocomplete="off" />
              <p class="field-hint">Con este correo iniciará sesión. No puede tener ya una cuenta en Nuvyra.</p>
              @if (err(form.controls.email); as e) {
                <p class="field-error">{{ e }}</p>
              }
            </div>
          </div>
          <fieldset class="grid gap-2" formGroupName="permissions">
            <legend class="field-label mb-1">Secciones que podrá usar</legend>
            @for (p of permissions; track p) {
              <label class="flex gap-3 text-[0.9375rem]">
                <input type="checkbox" class="checkbox" [formControlName]="p" />{{ label(p) }}
              </label>
            }
            @if (form.controls.permissions.touched && form.controls.permissions.hasError('nonePicked')) {
              <p class="field-error">Elige al menos una sección.</p>
            }
          </fieldset>
          <div>
            <button type="submit" class="btn btn-primary" [disabled]="busy() || !canInvite()">
              @if (busy()) {
                <span class="spinner" aria-hidden="true"></span> Creando invitación…
              } @else {
                Crear invitación
              }
            </button>
          </div>
        </form>
      } @else if (!error()) {
        <div class="h-56 animate-pulse rounded bg-mist/25" aria-busy="true"></div>
      }
    </section>
  `,
})
export class TeamPage {
  private readonly api = inject(StaffApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly clipboard = inject(Clipboard);

  protected readonly permissions = PERMISSIONS;
  protected readonly team = signal<Team | null>(null);
  protected readonly created = signal<InvitationCreated | null>(null);
  protected readonly editing = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly copied = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly canInvite = computed(() => {
    const t = this.team();
    return !!t && t.seatLimit !== null && t.seatsUsed < t.seatLimit;
  });

  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    permissions: this.permissionForm([]),
  });

  protected editForm: PermissionForm = this.permissionForm([]);

  constructor() {
    this.api.team().subscribe({
      next: (t) => this.team.set(t),
      error: (e: unknown) => this.error.set(ApiError.from(e).message),
    });
  }

  protected err(control: FormControl): string | null {
    return controlError(control);
  }

  protected label(p: StaffPermission): string {
    return PERMISSION_LABEL[p];
  }

  protected labels(list: StaffPermission[]): string {
    return PERMISSIONS.filter((p) => list.includes(p))
      .map((p) => PERMISSION_LABEL[p])
      .join(' · ');
  }

  protected date(iso: string | null): string {
    return formatDate(iso);
  }

  protected invite(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.reset();
    this.api
      .invite({ fullName: v.fullName.trim(), email: v.email.trim(), permissions: picked(v.permissions) })
      .subscribe({
        next: (c) => {
          this.showLink(c);
          this.form.reset();
        },
        error: (e: unknown) => this.fail(e, this.form),
      });
  }

  protected renew(id: string): void {
    this.busy.set(true);
    this.reset();
    this.api.renew(id).subscribe({ next: (c) => this.showLink(c), error: (e: unknown) => this.fail(e) });
  }

  protected cancel(id: string, name: string): void {
    if (!confirm(`¿Cancelar la invitación de ${name}? El enlace dejará de funcionar.`)) return;
    this.run(this.api.cancel(id), 'Invitación cancelada.');
  }

  protected edit(m: TeamMember): void {
    this.editForm = this.permissionForm(m.permissions);
    this.editing.set(m.id);
  }

  protected savePermissions(m: TeamMember): void {
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    this.run(this.api.updatePermissions(m.id, picked(this.editForm.getRawValue())), `Permisos de ${m.fullName} actualizados.`);
  }

  protected revoke(m: TeamMember): void {
    if (!confirm(`¿Revocar el acceso de ${m.fullName}? Su sesión se cerrará de inmediato.`)) return;
    this.run(this.api.revoke(m.id), `${m.fullName} ya no tiene acceso.`);
  }

  protected reactivate(m: TeamMember): void {
    this.run(this.api.reactivate(m.id), `${m.fullName} puede volver a ingresar con su contraseña.`);
  }

  protected copy(link: string): void {
    if (this.clipboard.copy(link)) {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2500);
    }
  }

  /** Abre WhatsApp con el mensaje listo; el titular elige el contacto y decide enviarlo. */
  protected whatsappShare(c: InvitationCreated): string {
    const text = `Hola ${c.invitation.fullName}, te invito a colaborar en mi negocio en Nuvyra. Crea tu acceso aquí: ${c.link}`;
    return `https://wa.me/?text=${encodeURIComponent(text)}`;
  }

  private showLink(c: InvitationCreated): void {
    this.team.set(c.team);
    this.created.set(c);
    this.busy.set(false);
  }

  private run(request: Observable<Team>, success: string): void {
    this.busy.set(true);
    this.reset();
    request.subscribe({
      next: (t) => {
        this.team.set(t);
        this.editing.set(null);
        this.notice.set(success);
        this.busy.set(false);
      },
      error: (e: unknown) => this.fail(e),
    });
  }

  private fail(e: unknown, form?: FormGroup): void {
    const apiError = ApiError.from(e);
    if (form) applyServerErrors(form, apiError);
    this.error.set(apiError.message);
    this.busy.set(false);
  }

  private reset(): void {
    this.error.set(null);
    this.notice.set(null);
    this.created.set(null);
  }

  private permissionForm(selected: StaffPermission[]): PermissionForm {
    const controls = Object.fromEntries(
      PERMISSIONS.map((p) => [p, new FormControl(selected.includes(p), { nonNullable: true })]),
    ) as Record<StaffPermission, FormControl<boolean>>;
    return new FormGroup(controls, { validators: atLeastOne });
  }
}

function picked(value: Record<StaffPermission, boolean>): StaffPermission[] {
  return PERMISSIONS.filter((p) => value[p]);
}
