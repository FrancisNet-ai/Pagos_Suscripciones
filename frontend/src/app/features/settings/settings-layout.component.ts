import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-settings-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <section class="mx-auto max-w-4xl">
      <p class="type-eyebrow">Configuración</p>
      <h1 class="type-h1 mt-3">Tu negocio</h1>
      <nav class="mt-8 flex gap-6 overflow-x-auto border-b border-[var(--line-subtle)]" aria-label="Secciones de configuración">
        @for (tab of tabs; track tab.route) {
          <a
            [routerLink]="tab.route"
            routerLinkActive="!border-night !text-night"
            class="-mb-px whitespace-nowrap border-b-2 border-transparent pb-3 text-[0.9375rem] font-semibold text-[var(--text-muted)] no-underline hover:text-night"
          >{{ tab.label }}</a>
        }
      </nav>
      <div class="pt-10">
        <router-outlet />
      </div>
    </section>
  `,
})
export class SettingsLayoutComponent {
  protected readonly tabs = [
    { label: 'Datos y personalización', route: '/panel/configuracion/negocio' },
    { label: 'Enlace público', route: '/panel/configuracion/enlace' },
    { label: 'Yape y Plin', route: '/panel/configuracion/cobros' },
  ];
}
