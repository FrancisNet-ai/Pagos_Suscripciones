import { IconComponent } from '../../shared/components/icon.component';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiError } from '../../core/services/api-error';
import { storeTheme } from '../../shared/components/store-theme';
import { DurationPipe, SolesPipe } from '../../shared/pipes/soles.pipe';
import { PublicRenewal, RenewalsApi } from './renewals.api';

@Component({
  selector: 'app-renewal-public-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, SolesPipe, DurationPipe, IconComponent],
  template: `
    <div [style]="theme()" class="buyer-stage">
      <main class="buyer-card renewal-card" [class.is-completed]="r()?.status === 'COMPLETED'">
        @if (loadError(); as e) {
          <h1 class="type-h2 mt-10">
            {{ e.status === 410 ? 'Este enlace ya venció' : 'Enlace no válido' }}
          </h1>
          <p class="mt-3">{{ e.message }}</p>
        } @else if (r(); as r) {
          <div class="flex items-center gap-3">
            @if (r.store.logoUrl) {
              <img [src]="r.store.logoUrl" alt="" class="h-10 w-10 rounded-full object-cover" />
            }
            <span class="font-semibold text-night">{{ r.store.businessName }}</span>
          </div>

          @if (r.status === 'COMPLETED') {
            <div class="renewal-result" aria-hidden="true"><app-icon name="check" /></div>
            <h1 class="type-h1 mt-5">¡Tu suscripción se renovó!</h1>
          } @else {
            <h1 class="type-h1 mt-8">Hola, {{ r.customerFirstName }}.</h1>
          }
          <p class="mt-3 text-lg">
            Tu suscripción a <strong class="text-night">{{ r.productName }}</strong>
            {{ expired() ? 'venció' : 'vence' }} el
            <strong class="text-night">{{ date(r.currentExpiresAt) }}</strong
            >.
          </p>

          <dl
            class="mt-6 divide-y divide-[var(--line-subtle)] border-y border-[var(--line-subtle)]"
          >
            <div class="flex justify-between gap-4 py-3">
              <dt class="text-[var(--text-muted)]">Renovación</dt>
              <dd class="text-night">{{ r.durationDays | duration }}</dd>
            </div>
            <div class="flex justify-between gap-4 py-3">
              <dt class="text-[var(--text-muted)]">Precio</dt>
              <dd class="type-numeric text-lg font-semibold text-night">{{ r.price | soles }}</dd>
            </div>
          </dl>

          @if (error(); as message) {
            <p class="notice notice-error mt-6" role="alert">{{ message }}</p>
          }

          @switch (r.status) {
            @case ('OPEN') {
              <h2 class="type-h2 mt-8">¿Deseas renovar tu servicio?</h2>
              <div class="mt-5 grid gap-3 sm:grid-cols-2">
                <button
                  type="button"
                  class="btn btn-store !min-h-12 text-base"
                  [disabled]="busy()"
                  (click)="respond('ACCEPTED')"
                >
                  Sí, renovar
                </button>
                <button
                  type="button"
                  class="btn btn-secondary !min-h-12 text-base"
                  [disabled]="busy()"
                  (click)="respond('DECLINED')"
                >
                  No, gracias
                </button>
              </div>
            }
            @case ('AWAITING_PAYMENT') {
              <p class="notice notice-info mt-8" role="status">
                Registramos que deseas renovar. Tu renovación se confirmará cuando
                {{ r.store.businessName }} verifique tu pago.
              </p>
              @if (r.canPay && r.paymentMethods.length) {
                <fieldset class="mt-6 grid gap-2">
                  <legend class="field-label mb-1">¿Cómo pagarás?</legend>
                  <div class="grid grid-cols-2 gap-3">
                    @for (m of r.paymentMethods; track m.method) {
                      <button
                        type="button"
                        class="min-h-14 rounded-[var(--radius-control)] border text-base font-semibold"
                        [class]="
                          method() === m.method
                            ? 'border-[var(--store-accent)] bg-white text-night ring-2 ring-[var(--store-accent)]'
                            : 'border-[var(--line-strong)] text-corporate'
                        "
                        [attr.aria-pressed]="method() === m.method"
                        (click)="method.set(m.method)"
                      >
                        {{ m.label }}
                      </button>
                    }
                  </div>
                </fieldset>
                <button
                  type="button"
                  class="btn btn-store btn-block mt-5 !min-h-12 text-base"
                  [disabled]="!method() || busy()"
                  (click)="pay()"
                >
                  @if (busy()) {
                    <span class="spinner" aria-hidden="true"></span>
                  }
                  Continuar al pago · {{ r.price | soles }}
                </button>
              } @else {
                <p class="mt-4">
                  Este servicio no está disponible para renovar en línea. Escribe al vendedor.
                </p>
              }
              <button
                type="button"
                class="link mt-6 text-sm"
                [disabled]="busy()"
                (click)="respond('DECLINED')"
              >
                Prefiero no renovar
              </button>
            }
            @case ('DECLINED') {
              <p
                class="mt-8 whitespace-pre-line font-[family-name:var(--font-editorial)] text-xl leading-relaxed text-night"
              >
                {{ r.farewellMessage }}
              </p>
              <p class="mt-4 text-sm">
                Tu servicio sigue vigente hasta el {{ date(r.currentExpiresAt) }}.
              </p>
            }
            @case ('COMPLETED') {
              <p class="notice notice-success mt-8" role="status">
                Tu renovación fue confirmada. ¡Gracias por seguir con {{ r.store.businessName }}!
              </p>
              <a class="btn btn-store btn-block mt-5" [routerLink]="['/tienda', r.store.slug]"
                >Volver a la tienda</a
              >
            }
            @case ('EXPIRED') {
              <p class="mt-8">
                Esta solicitud de renovación ya cerró. Si deseas volver, visita la tienda.
              </p>
              <a class="btn btn-store mt-4" [routerLink]="['/tienda', r.store.slug]"
                >Ir a la tienda</a
              >
            }
          }

          <div
            class="mt-12 border-t border-[var(--line-subtle)] pt-5 text-xs text-[var(--text-muted)]"
          >
            @if (r.optedOut) {
              <p>No te enviaremos más avisos de {{ r.store.businessName }}.</p>
            } @else {
              <button type="button" class="underline underline-offset-2" (click)="optOut()">
                No quiero recibir más avisos de este negocio
              </button>
            }
          </div>
        } @else {
          <div class="mt-10 h-48 animate-pulse rounded bg-mist/30" aria-busy="true">
            <span class="sr-only">Cargando…</span>
          </div>
        }
      </main>
    </div>
  `,
})
export class RenewalPublicPage implements OnInit {
  readonly token = input.required<string>();

  private readonly api = inject(RenewalsApi);
  private readonly router = inject(Router);
  protected readonly r = signal<PublicRenewal | null>(null);
  protected readonly loadError = signal<{ status: number; message: string } | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly method = signal<'YAPE' | 'PLIN' | null>(null);
  protected readonly theme = computed(() => storeTheme(this.r()?.store.primaryColor));
  protected readonly expired = computed(() => {
    const r = this.r();
    return !!r && new Date(r.currentExpiresAt).getTime() < Date.now();
  });

  ngOnInit(): void {
    this.api.view(this.token()).subscribe({
      next: (r) => this.set(r),
      error: (e: unknown) => {
        const err = ApiError.from(e);
        this.loadError.set({ status: err.status, message: err.message });
      },
    });
  }

  protected respond(response: 'ACCEPTED' | 'DECLINED'): void {
    this.run(() => this.api.respond(this.token(), response));
  }

  protected optOut(): void {
    this.run(() => this.api.optOut(this.token()));
  }

  protected pay(): void {
    const method = this.method();
    if (!method) return;
    this.busy.set(true);
    this.error.set(null);
    this.api.createOrder(this.token(), method).subscribe({
      next: ({ publicToken }) => void this.router.navigate(['/pedido', publicToken]),
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.busy.set(false);
      },
    });
  }

  protected date(iso: string): string {
    return new Intl.DateTimeFormat('es-PE', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    }).format(new Date(iso));
  }

  private run(request: () => ReturnType<RenewalsApi['view']>): void {
    this.busy.set(true);
    this.error.set(null);
    request().subscribe({
      next: (r) => {
        this.set(r);
        this.busy.set(false);
      },
      error: (e: unknown) => {
        this.error.set(ApiError.from(e).message);
        this.busy.set(false);
      },
    });
  }

  private set(r: PublicRenewal): void {
    this.r.set(r);
    if (r.paymentMethods.length === 1) this.method.set(r.paymentMethods[0].method);
  }
}
