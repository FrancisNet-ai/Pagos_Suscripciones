import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { Router, TitleStrategy, provideRouter } from '@angular/router';
import { NuvyraTitleStrategy } from './title.strategy';

@Component({ template: '' })
class Blank {}

describe('NuvyraTitleStrategy', () => {
  it('agrega la marca en el panel y la superadministración, pero no en la tienda del vendedor', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', component: Blank },
          { path: 'panel', title: 'Productos', component: Blank },
          { path: 'admin', title: 'Pagos SaaS', component: Blank },
          { path: 'pedido/:token', title: 'Tu pedido', data: { storefront: true }, component: Blank },
        ]),
        { provide: TitleStrategy, useClass: NuvyraTitleStrategy },
      ],
    });
    const router = TestBed.inject(Router);
    const title = TestBed.inject(Title);
    await router.navigateByUrl('/');
    expect(title.getTitle()).toBe('Nuvyra — Tu negocio, bajo control.');
    await router.navigateByUrl('/panel');
    expect(title.getTitle()).toBe('Productos · Nuvyra');
    await router.navigateByUrl('/admin');
    expect(title.getTitle()).toBe('Pagos SaaS · Nuvyra Admin');
    await router.navigateByUrl('/pedido/abc');
    expect(title.getTitle()).toBe('Tu pedido');
  });
});
