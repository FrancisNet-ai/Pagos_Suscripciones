import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { BrandMarkComponent } from '../../shared/components/brand-mark.component';
import { LEGAL_VERSION, legalDocs, legalHolderComplete } from './legal-documents';

@Component({
  selector: 'app-legal-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BrandMarkComponent, RouterLink],
  template: `
    <header class="border-b border-[var(--line-subtle)]">
      <div class="mx-auto max-w-3xl px-5 py-5 sm:px-8"><app-brand-mark /></div>
    </header>
    <main class="mx-auto max-w-3xl px-5 py-14 sm:px-8">
      @if (doc(); as d) {
        <p class="type-eyebrow">Documento legal</p>
        <h1 class="type-h1 mt-3">{{ d.title }}</h1>
        <p class="mt-3 text-sm text-[var(--text-muted)]">Versión vigente desde el {{ version }}.</p>
        @if (!holderComplete) {
          <p class="notice notice-info mt-6" role="note">Estamos completando los datos de identificación del titular del servicio.</p>
        }
        <p class="mt-8">{{ d.intro }}</p>
        @for (s of d.sections; track s.heading) {
          <section class="mt-10">
            <h2 class="type-h2 !text-xl">{{ s.heading }}</h2>
            @for (p of s.paragraphs ?? []; track p) { <p class="mt-3">{{ p }}</p> }
            @if (s.items?.length) {
              <ul class="mt-3 list-disc space-y-2 pl-6 marker:text-steel">
                @for (item of s.items; track item) { <li class="pl-1">{{ item }}</li> }
              </ul>
            }
            @for (p of s.after ?? []; track p) { <p class="mt-3">{{ p }}</p> }
          </section>
        }
        <nav class="mt-14 flex flex-wrap gap-4 border-t border-[var(--line-subtle)] pt-6 text-sm" aria-label="Documentos legales">
          <a class="link" routerLink="/legal/condiciones">Términos y condiciones</a>
          <a class="link" routerLink="/legal/privacidad">Política de privacidad</a>
        </nav>
      } @else {
        <h1 class="type-h1">Documento no encontrado</h1>
      }
    </main>
  `,
})
export class LegalPage {
  readonly documento = input<string>();
  protected readonly version = LEGAL_VERSION;
  protected readonly holderComplete = legalHolderComplete();
  protected readonly doc = computed(() => legalDocs()[this.documento() ?? ''] ?? null);
}
