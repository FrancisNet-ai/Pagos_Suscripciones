import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Composición abstracta de ondas azules. Decorativa: oculta a lectores de pantalla. */
@Component({
  selector: 'app-wave-art',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true', class: 'pointer-events-none block' },
  template: `
    <svg class="h-full w-full" viewBox="0 0 800 600" preserveAspectRatio="xMidYMid slice">
      <defs>
        <linearGradient id="wa-a" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#4A7EA7" [attr.stop-opacity]="intensity() * 0.55" />
          <stop offset="1" stop-color="#1A3D63" stop-opacity="0" />
        </linearGradient>
        <linearGradient id="wa-b" x1="1" y1="0" x2="0" y2="1">
          <stop offset="0" stop-color="#B3CFE5" [attr.stop-opacity]="intensity() * 0.35" />
          <stop offset="1" stop-color="#B3CFE5" stop-opacity="0" />
        </linearGradient>
      </defs>
      <path d="M-50 420 C 120 300, 260 520, 430 400 S 720 260, 860 340 L 860 620 L -50 620 Z" fill="url(#wa-a)" />
      <path d="M-50 210 C 140 120, 300 300, 470 210 S 760 80, 860 150 L 860 0 L -50 0 Z" fill="url(#wa-b)" />
      @for (line of lines; track line) {
        <path
          [attr.d]="'M-50 ' + (300 + line * 18) + ' C 150 ' + (190 + line * 22) + ', 330 ' + (430 + line * 10) + ', 500 ' + (320 + line * 14) + ' S 760 ' + (220 + line * 16) + ', 860 ' + (280 + line * 12)"
          fill="none"
          stroke="#B3CFE5"
          [attr.stroke-opacity]="intensity() * (0.28 - line * 0.03)"
          stroke-width="1"
        />
      }
    </svg>
  `,
})
export class WaveArtComponent {
  /** 0–1: opacidad global de la composición. */
  readonly intensity = input(1);
  protected readonly lines = [0, 1, 2, 3, 4, 5, 6];
}
