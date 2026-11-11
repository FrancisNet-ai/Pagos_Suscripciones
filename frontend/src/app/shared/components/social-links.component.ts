import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export interface StoreSocialLinks {
  instagram: string | null;
  facebook: string | null;
  tiktok: string | null;
  website: string | null;
}

type Network = keyof StoreSocialLinks;

const LABELS: Record<Network, string> = { instagram: 'Instagram', facebook: 'Facebook', tiktok: 'TikTok', website: 'Página web' };

/**
 * Íconos de las redes del vendedor en la portada de su tienda. Solo aparecen las que configuró; sin ninguna, no se
 * dibuja nada. Los enlaces ya vienen validados del servidor (https y del dominio de cada red).
 */
@Component({
  selector: 'app-social-links',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (items().length > 0) {
      <ul class="social-links" aria-label="Redes de la tienda">
        @for (i of items(); track i.network) {
          <li>
            <a [href]="i.url" target="_blank" rel="noopener noreferrer" [attr.aria-label]="i.label + ' de ' + storeName()" [title]="i.label">
              @switch (i.network) {
                @case ('instagram') {
                  <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3.5" y="3.5" width="17" height="17" rx="5" /><circle cx="12" cy="12" r="4" /><circle cx="17.2" cy="6.8" r="1" class="fill" /></svg>
                }
                @case ('facebook') {
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14 8.5h2.5V5H14a4 4 0 0 0-4 4v2H8v3.5h2V21h3.5v-6.5H16l.5-3.5h-3V9.5a1 1 0 0 1 1-1Z" class="fill" /></svg>
                }
                @case ('tiktok') {
                  <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M14 3v11.5a3.5 3.5 0 1 1-3.5-3.5" /><path d="M14 3c.4 2.6 2.2 4.4 5 4.6" /></svg>
                }
                @default {
                  <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8.5" /><path d="M3.5 12h17M12 3.5c2.4 2.4 3.5 5.2 3.5 8.5s-1.1 6.1-3.5 8.5c-2.4-2.4-3.5-5.2-3.5-8.5s1.1-6.1 3.5-8.5Z" /></svg>
                }
              }
            </a>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    .social-links { display: flex; gap: 10px; margin: 0; padding: 0; list-style: none; }
    a { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 999px; color: #f6fafd;
      border: 1px solid rgb(246 250 253 / 45%); background: rgb(10 25 49 / 25%); transition: background-color 0.2s ease; }
    a:hover { background: rgb(10 25 49 / 50%); }
    a:focus-visible { outline: 2px solid #f6fafd; outline-offset: 2px; }
    svg { width: 20px; height: 20px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
    svg .fill { fill: currentColor; stroke: none; }
  `,
})
export class SocialLinksComponent {
  readonly links = input<StoreSocialLinks | null | undefined>(null);
  readonly storeName = input('la tienda');

  protected readonly items = computed(() => {
    const l = this.links();
    if (!l) return [];
    return (['instagram', 'facebook', 'tiktok', 'website'] as Network[])
      .map((network) => ({ network, url: l[network], label: LABELS[network] }))
      .filter((i): i is { network: Network; url: string; label: string } => !!i.url && /^https:\/\//.test(i.url));
  });
}
