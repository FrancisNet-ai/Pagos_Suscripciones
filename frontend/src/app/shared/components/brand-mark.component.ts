import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BRAND } from '../../core/brand';

@Component({
  selector: 'app-brand-mark',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <a
      [routerLink]="link()"
      class="inline-flex items-center gap-2.5 no-underline"
      [class.text-snow]="tone() === 'light'"
      [class.text-night]="tone() === 'dark'"
      [attr.aria-label]="brand.name + ', ir al inicio'"
    >
      <svg width="28" height="28" viewBox="0 0 32 32" aria-hidden="true">
        <circle cx="16" cy="16" r="15" fill="none" stroke="currentColor" stroke-opacity="0.35" />
        <path d="M8 11.5 L16 23 L24 11.5" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" />
        <circle cx="16" cy="8" r="2" fill="#4A7EA7" />
      </svg>
      <span class="font-[family-name:var(--font-display)] text-2xl font-semibold tracking-tight">{{ brand.name }}</span>
    </a>
  `,
})
export class BrandMarkComponent {
  readonly tone = input<'light' | 'dark'>('dark');
  readonly link = input('/');
  protected readonly brand = BRAND;
}
