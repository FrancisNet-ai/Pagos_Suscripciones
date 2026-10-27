import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const PATHS: Record<string, string[]> = {
  grid: ['M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z'],
  box: ['m12 3 9 5-9 5-9-5 9-5Z M3 8v9l9 5 9-5V8 M12 13v9 M7.5 5.5l9 5'],
  users: [
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M20 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75',
  ],
  wallet: ['M20 8V5a2 2 0 0 0-2-2H5a3 3 0 0 0 0 6h16v11H5a3 3 0 0 1-3-3V6 M21 12h-5v5h5'],
  renew: ['M20 7v5h-5 M4 17v-5h5 M6.1 6.1a8 8 0 0 1 13.2 3.4 M4.7 14.5a8 8 0 0 0 13.2 3.4'],
  settings: [
    'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M9 3l-1 3-3 1-2 4 2 2v3l3 2 1 3h5l1-3 3-2v-3l2-2-2-4-3-1-1-3H9Z',
  ],
  chart: ['M3 3v18h18 M7 15l4-5 4 3 6-8'],
  arrow: ['M4 12h16 M14 6l6 6-6 6'],
  external: ['M14 3h7v7 M21 3l-9 9 M10 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-5'],
  chevron: ['m9 5 7 7-7 7'],
  menu: ['M4 6h16 M4 12h16 M4 18h16'],
  close: ['m6 6 12 12 M18 6 6 18'],
  shield: ['M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7l-9-4Z m-4 9 3 3 5-6'],
  check: ['m5 12 4 4L19 6'],
  clock: ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18 M12 7v5l3 2'],
  play: ['m9 5 11 7-11 7V5Z'],
  book: ['M12 5v16 M3 3l9 2 9-2v16l-9 2-9-2V3Z'],
  sparkle: ['m12 3 2.7 6.3L21 12l-6.3 2.7L12 21l-2.7-6.3L3 12l6.3-2.7L12 3Z'],
  search: ['M10.5 3a7.5 7.5 0 1 0 0 15 7.5 7.5 0 0 0 0-15 m5.5 13 5 5'],
  logout: ['M9 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h4 M9 12h12 m-5-5 5 5-5 5'],
  plus: ['M12 5v14 M5 12h14'],
  file: ['M14 2H5v20h14V7l-5-5Z M14 2v6h5 M8 13h8 M8 17h6'],
};

/** Iconos decorativos; el control que los contiene conserva su nombre accesible. */
@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true', class: 'ui-icon' },
  template: `<svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.65"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    @for (path of paths(); track $index) {
      <path [attr.d]="path" />
    }
  </svg>`,
})
export class IconComponent {
  readonly name = input('grid');
  protected readonly paths = computed(() => PATHS[this.name()] ?? PATHS['grid']);
}
