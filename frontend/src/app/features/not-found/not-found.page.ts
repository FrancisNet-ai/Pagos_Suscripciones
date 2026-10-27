import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BrandMarkComponent } from '../../shared/components/brand-mark.component';

@Component({
  selector: 'app-not-found-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, BrandMarkComponent],
  template: `
    <main class="mx-auto flex min-h-dvh max-w-xl flex-col justify-center px-5 py-14">
      <app-brand-mark />
      <p class="type-numeric mt-14 font-[family-name:var(--font-display)] text-7xl text-mist">404</p>
      <h1 class="type-h2 mt-4">No encontramos esta página</h1>
      <p class="mt-3">Es posible que el enlace esté incompleto o que la página ya no exista.</p>
      <a class="btn btn-primary mt-8 self-start" routerLink="/">Volver al inicio</a>
    </main>
  `,
})
export class NotFoundPage {}
