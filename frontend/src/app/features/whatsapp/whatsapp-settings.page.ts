import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiError } from '../../core/services/api-error';
import { StatusBadgeComponent } from '../../shared/components/status-badge.component';
import { applyServerErrors, controlError } from '../../shared/validators/form-errors';
import { formatDate } from '../billing/billing.api';
import { WhatsAppApi, WhatsAppIntegration } from './whatsapp.api';

const GRAPH_ID = /^[0-9]{5,30}$/;
const TEMPLATE = /^[a-z0-9_]{1,512}$/;

@Component({
  selector: 'app-whatsapp-settings-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, StatusBadgeComponent],
  template: `
    <div class="grid max-w-3xl gap-8">
      <div>
        <div class="flex flex-wrap items-baseline justify-between gap-3">
          <h2 class="font-[family-name:var(--font-editorial)] text-2xl font-semibold">WhatsApp Business</h2>
          @if (data(); as d) {
            @if (!d.configured) { <app-status-badge label="Sin conectar" /> }
            @else if (d.enabled && d.status === 'VERIFIED') { <app-status-badge label="Envío automático activo" tone="success" /> }
            @else if (d.status === 'ERROR') { <app-status-badge label="Error de conexión" tone="danger" /> }
            @else { <app-status-badge label="Conectado, envío pausado" tone="warning" /> }
          }
        </div>
        <p class="mt-3">
          Con la integración oficial de Meta, los avisos de renovación se envían solos desde tu número de WhatsApp Business y tus clientes
          pueden responder <strong>Sí</strong> o <strong>No</strong> con un botón. Sin ella, sigues enviándolos tú con un toque desde el panel.
        </p>
        <p class="notice notice-info mt-4" role="note">
          Solo se envían automáticamente a clientes que autorizaron recordatorios al comprar y no pidieron dejar de recibirlos.
          Los demás avisos siguen esperando tu envío manual.
        </p>
      </div>

      @if (notice(); as n) { <p class="notice notice-success" role="status">{{ n }}</p> }
      @if (error(); as e) { <p class="notice notice-error" role="alert">{{ e }}</p> }

      @if (data(); as d) {
        @if (!d.planAllows) {
          <p class="notice notice-warning" role="note">Tu plan no incluye avisos automáticos. <a routerLink="/panel/plan" class="font-semibold underline">Cambia de plan</a> para conectar WhatsApp Business.</p>
        }
        @if (d.status === 'ERROR' && d.lastError) {
          <p class="notice notice-error" role="alert">Meta respondió: {{ d.lastError }}</p>
        } @else if (d.lastError) {
          <p class="notice notice-warning" role="note">{{ d.lastError }}</p>
        }
        @if (d.status === 'VERIFIED') {
          <p class="text-sm">Número verificado: <strong class="type-numeric">{{ d.displayPhone }}</strong> · {{ formatDate(d.lastVerifiedAt) }}</p>
        }

        <form class="grid gap-6" [formGroup]="form" (ngSubmit)="save()" novalidate>
          <fieldset class="grid gap-5" [disabled]="!d.planAllows">
            <legend class="field-label mb-1">Datos de tu cuenta en Meta (WhatsApp Manager)</legend>
            <div class="grid gap-5 sm:grid-cols-2">
              <div class="field">
                <label class="field-label" for="phoneNumberId">Identificador del número de teléfono</label>
                <input id="phoneNumberId" class="input type-numeric" formControlName="phoneNumberId" inputmode="numeric" autocomplete="off" />
                @if (err('phoneNumberId'); as e) { <p class="field-error">{{ e }}</p> }
              </div>
              <div class="field">
                <label class="field-label" for="wabaId">Identificador de la cuenta de WhatsApp Business</label>
                <input id="wabaId" class="input type-numeric" formControlName="wabaId" inputmode="numeric" autocomplete="off" />
                <p class="field-hint">Opcional. Lo usamos para recibir los estados de entrega y las respuestas.</p>
                @if (err('wabaId'); as e) { <p class="field-error">{{ e }}</p> }
              </div>
            </div>
            <div class="field">
              <label class="field-label" for="accessToken">Token de acceso</label>
              <input id="accessToken" class="input" type="password" formControlName="accessToken" autocomplete="off"
                     [placeholder]="d.tokenSaved ? 'Guardado · déjalo vacío para conservarlo' : ''" />
              <p class="field-hint">Se guarda cifrado y nunca se vuelve a mostrar. Usa un token permanente de usuario del sistema.</p>
              @if (err('accessToken'); as e) { <p class="field-error">{{ e }}</p> }
            </div>
          </fieldset>

          <fieldset class="grid gap-5 border-t border-[var(--line-subtle)] pt-6" [disabled]="!d.planAllows">
            <legend class="field-label mb-1">Plantillas aprobadas por Meta</legend>
            <div class="grid gap-5 sm:grid-cols-[1fr_1fr_8rem]">
              <div class="field">
                <label class="field-label" for="reminderTemplate">Recordatorio</label>
                <input id="reminderTemplate" class="input" formControlName="reminderTemplate" autocomplete="off" placeholder="recordatorio_renovacion" />
                @if (err('reminderTemplate'); as e) { <p class="field-error">{{ e }}</p> }
              </div>
              <div class="field">
                <label class="field-label" for="farewellTemplate">Despedida (opcional)</label>
                <input id="farewellTemplate" class="input" formControlName="farewellTemplate" autocomplete="off" placeholder="despedida_cliente" />
                @if (err('farewellTemplate'); as e) { <p class="field-error">{{ e }}</p> }
              </div>
              <div class="field">
                <label class="field-label" for="templateLanguage">Idioma</label>
                <input id="templateLanguage" class="input" formControlName="templateLanguage" autocomplete="off" />
                @if (err('templateLanguage'); as e) { <p class="field-error">{{ e }}</p> }
              </div>
            </div>
            <details class="rounded-[var(--radius-control)] bg-white p-4 text-sm ring-1 ring-[var(--line-subtle)]">
              <summary class="cursor-pointer font-semibold text-night">Cómo deben ser las plantillas</summary>
              <div class="mt-3 grid gap-3">
                <p><strong>Recordatorio</strong> (categoría «Utilidad»), cuerpo con 4 variables y 3 botones en este orden:</p>
                <p class="whitespace-pre-line rounded bg-snow p-3 font-mono text-xs">Hola, {{ v[0] }}. Tu suscripción a {{ v[1] }} de {{ v[2] }} vence {{ v[3] }}. ¿Deseas renovarla?
Botón 1 · respuesta rápida: Sí, renovar
Botón 2 · respuesta rápida: No, gracias
Botón 3 · enlace: {{ renewUrlBase }}{{ v[0] }}</p>
                <p>{{ v[0] }} nombre del cliente, {{ v[1] }} servicio, {{ v[2] }} tu negocio, {{ v[3] }} «hoy» o «el 29 de octubre».</p>
                <p><strong>Despedida</strong>: cuerpo con 2 variables, {{ v[0] }} nombre del cliente y {{ v[1] }} tu negocio. Si no la configuras, la despedida queda para envío manual,
                  salvo cuando el cliente responde «No» por WhatsApp: entonces se le contesta en la misma conversación.</p>
              </div>
            </details>
          </fieldset>

          <label class="flex items-start gap-3">
            <input class="checkbox" type="checkbox" formControlName="enabled" [attr.disabled]="d.planAllows ? null : true" />
            <span><span class="font-semibold text-night">Enviar avisos automáticamente</span><br />
              <span class="text-sm">Antes de activarlo verificamos el número con Meta.</span></span>
          </label>

          <div class="flex flex-wrap items-center gap-3 border-t border-[var(--line-subtle)] pt-6">
            <button class="btn btn-primary" type="submit" [disabled]="busy() || !d.planAllows">
              @if (busy()) { <span class="spinner" aria-hidden="true"></span> Verificando con Meta… } @else { Guardar y verificar }
            </button>
            @if (d.configured) {
              <button type="button" class="btn btn-secondary" [disabled]="busy()" (click)="remove()">Desconectar y borrar token</button>
            }
          </div>
        </form>

        <section class="border-t border-[var(--line-subtle)] pt-6 text-sm" aria-labelledby="webhook-titulo">
          <h3 id="webhook-titulo" class="font-semibold text-night">Estados de entrega y respuestas</h3>
          @if (d.webhooksAvailable) {
            <p class="mt-2">Nuvyra recibe los avisos de Meta en <span class="break-all font-mono text-xs">{{ d.webhookUrl }}</span>: verás si el mensaje se entregó
              y las respuestas Sí/No se registran solas. Si el cliente escribe «BAJA», deja de recibir avisos.</p>
          } @else {
            <p class="mt-2">Nuvyra aún no tiene configurada la recepción de webhooks. Los avisos se envían, pero los estados de entrega y las respuestas por botón
              no se registrarán; el cliente puede responder desde el enlace del mensaje.</p>
          }
        </section>
      } @else if (!error()) {
        <div class="h-56 animate-pulse rounded bg-mist/25" aria-busy="true"></div>
      }
    </div>
  `,
})
export class WhatsAppSettingsPage {
  private readonly api = inject(WhatsAppApi);
  protected readonly data = signal<WhatsAppIntegration | null>(null);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly formatDate = formatDate;
  protected readonly renewUrlBase = `${location.origin}/renovar/`;
  /** Marcadores de variables de Meta, mostrados tal cual en la ayuda. */
  protected readonly v = ['{{1}}', '{{2}}', '{{3}}', '{{4}}'];

  protected readonly form = inject(NonNullableFormBuilder).group({
    phoneNumberId: ['', [Validators.required, Validators.pattern(GRAPH_ID)]],
    wabaId: ['', [Validators.pattern(GRAPH_ID)]],
    accessToken: ['', [Validators.maxLength(1024)]],
    reminderTemplate: ['', [Validators.required, Validators.pattern(TEMPLATE)]],
    farewellTemplate: ['', [Validators.pattern(TEMPLATE)]],
    templateLanguage: ['es', [Validators.required, Validators.pattern(/^[a-z]{2}(_[A-Z]{2})?$/)]],
    enabled: [true],
  });

  constructor() {
    this.api.get().subscribe({ next: (d) => this.apply(d), error: (e: unknown) => this.error.set(ApiError.from(e).message) });
  }

  protected err(name: keyof typeof this.form.controls): string | null {
    const c = this.form.controls[name];
    if (c.errors?.['pattern'] && (c.touched || c.dirty)) {
      return name === 'templateLanguage' ? 'Usa un código como es o es_PE'
        : name.endsWith('Template') ? 'Usa el nombre exacto: minúsculas, números y guion bajo' : 'Solo dígitos, tal como aparece en WhatsApp Manager';
    }
    return controlError(c);
  }

  protected save(): void {
    const d = this.data();
    if (!d) return;
    if (!d.tokenSaved && !this.form.controls.accessToken.value.trim()) {
      this.form.controls.accessToken.setErrors({ server: 'Ingresa el token de acceso' });
      this.form.controls.accessToken.markAsTouched();
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.api.save({
      phoneNumberId: v.phoneNumberId.trim(), wabaId: v.wabaId.trim() || null, accessToken: v.accessToken.trim() || null,
      reminderTemplate: v.reminderTemplate.trim(), farewellTemplate: v.farewellTemplate.trim() || null, templateLanguage: v.templateLanguage.trim(), enabled: v.enabled,
    }).subscribe({
      next: (r) => {
        this.apply(r);
        this.busy.set(false);
        if (r.status === 'VERIFIED') this.notice.set(r.enabled ? 'Conexión verificada. Los avisos se enviarán automáticamente.' : 'Conexión verificada. El envío automático está pausado.');
      },
      error: (e: unknown) => {
        const apiError = ApiError.from(e);
        applyServerErrors(this.form, apiError);
        this.error.set(apiError.message);
        this.busy.set(false);
      },
    });
  }

  protected remove(): void {
    this.busy.set(true);
    this.api.remove().subscribe({
      next: () => {
        this.busy.set(false);
        this.notice.set('Desconectamos tu número y borramos el token.');
        this.api.get().subscribe((d) => this.apply(d));
      },
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.busy.set(false);
      },
    });
  }

  private apply(d: WhatsAppIntegration): void {
    this.data.set(d);
    this.form.reset({
      phoneNumberId: d.phoneNumberId ?? '', wabaId: d.wabaId ?? '', accessToken: '', reminderTemplate: d.reminderTemplate ?? '',
      farewellTemplate: d.farewellTemplate ?? '', templateLanguage: d.templateLanguage, enabled: d.configured ? d.enabled : true,
    });
  }
}
