import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { BRAND } from './brand';

/**
 * Títulos del navegador: «Productos · Nuvyra» en el panel y «Pagos SaaS · Nuvyra Admin» en la
 * superadministración. Las páginas del comprador (`data.storefront`) no llevan la marca de Nuvyra:
 * pertenecen a la tienda del vendedor.
 */
@Injectable({ providedIn: 'root' })
export class NuvyraTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const page = this.buildTitle(snapshot);
    let route = snapshot.root;
    while (route.firstChild) route = route.firstChild;
    if (route.data['storefront']) {
      if (page) this.title.setTitle(page);
      return;
    }
    const suffix = snapshot.url.startsWith('/admin') ? `${BRAND.name} Admin` : BRAND.name;
    this.title.setTitle(page ? `${page} · ${suffix}` : `${BRAND.name} — ${BRAND.tagline}`);
  }
}
