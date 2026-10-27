import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { BrandMarkComponent } from '../../shared/components/brand-mark.component';
import { WaveArtComponent } from '../../shared/components/wave-art.component';
import { IconComponent } from '../../shared/components/icon.component';

@Component({
  selector: 'app-auth-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BrandMarkComponent, WaveArtComponent, IconComponent],
  template: `
    <div class="auth-stage">
      <app-wave-art />
      <div class="auth-layout" [class.auth-wide]="wide()">
        <aside class="auth-story">
          <app-brand-mark tone="light" />
          <h2>{{ quote() }}</h2>
          <p>{{ caption() }}</p>
          <p class="auth-story-note">
            <app-icon name="shield" /><span>Tu negocio, con una sesión protegida.</span>
          </p>
        </aside>
        <main class="auth-card">
          <div class="auth-mobile-brand"><app-brand-mark /></div>
          <ng-content />
        </main>
      </div>
    </div>
  `,
})
export class AuthLayoutComponent {
  readonly quote = input('Un nuevo día. Más posibilidades.');
  readonly caption = input(
    'Ingresa a tu cuenta para gestionar tus productos, clientes y renovaciones.',
  );
  readonly wide = input(false);
}
