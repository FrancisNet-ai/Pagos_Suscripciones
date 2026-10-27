import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { BrandMarkComponent } from '../../shared/components/brand-mark.component';
import { WaveArtComponent } from '../../shared/components/wave-art.component';

/** Composición dividida para las pantallas de acceso: panel editorial oscuro + formulario claro. */
@Component({
  selector: 'app-auth-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BrandMarkComponent, WaveArtComponent],
  template: `
    <div class="grid min-h-dvh lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <aside class="relative isolate hidden overflow-hidden bg-night p-12 text-snow lg:flex lg:flex-col lg:justify-between">
        <app-wave-art class="absolute inset-0 -z-10" [intensity]="0.85" />
        <app-brand-mark tone="light" />
        <div>
          <p class="font-[family-name:var(--font-display)] text-5xl leading-[1.05] text-snow">{{ quote() }}</p>
          <p class="mt-6 max-w-sm text-mist">{{ caption() }}</p>
        </div>
        <p class="text-sm text-mist/80">Tus datos viajan cifrados y tu sesión se protege con cookies seguras.</p>
      </aside>

      <main class="flex flex-col px-5 py-8 sm:px-10 lg:px-16 lg:py-12">
        <div class="lg:hidden"><app-brand-mark /></div>
        <div class="mx-auto flex w-full max-w-md flex-1 flex-col justify-center py-10" [class.max-w-lg]="wide()">
          <ng-content />
        </div>
      </main>
    </div>
  `,
})
export class AuthLayoutComponent {
  readonly quote = input('Vende, cobra y renueva con calma.');
  readonly caption = input('El panel donde cada suscripción tiene su fecha y cada pago su verificación.');
  readonly wide = input(false);
}
